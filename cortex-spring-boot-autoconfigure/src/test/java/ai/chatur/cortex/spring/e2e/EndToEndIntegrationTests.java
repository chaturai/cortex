package ai.chatur.cortex.spring.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * End-to-end test of the AI agent and human reviewer workflows: assertions are linted and ingested
 * over the MCP protocol, then reviewed, edited, and approved through the web UI, and finally found
 * again through MCP search and the UI search bar.
 *
 * <p>This is the one test in the module that genuinely earns a full {@link SpringBootTest} context
 * — it is the only round trip that actually exercises MCP, the web UI, and the wiring between them
 * together. Everything else that used to boot Spring either moved to {@code cortex-core} as a
 * direct behavior test, or moved to a plain JUnit test of a controller/tool against a hand-rolled
 * fake of its narrow role dependency.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.ai.mcp.server.protocol=stateless", "spring.ai.mcp.server.type=sync"})
class EndToEndIntegrationTests {

  @SpringBootApplication
  static class EndToEndApplication {}

  static final Pattern BRANCH = Pattern.compile("\"branch\":\"([^\"]+)\"");

  static final String TTL =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:E2EAgent a :Agent ;
          rdfs:label "end to end agent" .

      kb:E2ETask a :Task ;
          :assignedTo kb:E2EAgent ;
          rdfs:label "end to end task" ;
          rdfs:comment "task used by the end to end test" .
      """;

  @LocalServerPort int port;

  final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @Test
  void agentsShouldIngestAndReviewersShouldCurate() throws IOException, InterruptedException {
    McpSyncClient client =
        McpClient.sync(
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
            .build();
    try {
      client.initialize();

      // The agent lints its assertions, then ingests them over MCP
      String linted = getText(client, "lint", Map.of("ttl", TTL));
      assertThat(linted).contains("\"valid\":true");

      String ingested = getText(client, "ingest", Map.of("ttl", TTL));
      assertThat(ingested).contains("\"valid\":true");
      Matcher matcher = BRANCH.matcher(ingested);
      assertThat(matcher.find()).as("the ingest response carries the staged branch name").isTrue();
      String branch = matcher.group(1);

      // The branches page lists the branch with its provenance activity badges
      String branchesPage = get("/branches");
      assertThat(branchesPage).contains(branch);
      assertThat(branchesPage).contains("prov:Activity");
      assertThat(branchesPage).contains("6 triples");

      // The branch page shows the staged statements grouped by subject
      String branchPage = get("/branches/" + branch);
      assertThat(branchPage).contains("E2ETask");
      assertThat(branchPage).contains("E2EAgent");
      assertThat(branchPage).contains("data-subject=\"example://kb/E2ETask\"");
      assertThat(branchPage).contains("end to end task");
      assertThat(branchPage).contains("Approve");
      assertThat(branchPage).contains("Reject");

      // The reviewer edits the task label and deletes its comment
      String changes =
          """
          [
            {"subject":"example://kb/E2ETask",
             "predicate":"http://www.w3.org/2000/01/rdf-schema#label",
             "object":"end to end task","literal":true,
             "datatype":"http://www.w3.org/2001/XMLSchema#string",
             "newObject":"edited end to end task"},
            {"subject":"example://kb/E2ETask",
             "predicate":"http://www.w3.org/2000/01/rdf-schema#comment",
             "object":"task used by the end to end test","literal":true,
             "datatype":"http://www.w3.org/2001/XMLSchema#string",
             "newObject":null}
          ]
          """;
      HttpResponse<String> updated = postJson("/branches/" + branch + "/update", changes);
      assertThat(updated.statusCode()).as("the update endpoint reports success").isEqualTo(204);

      branchPage = get("/branches/" + branch);
      assertThat(branchPage).contains("edited end to end task");
      assertThat(branchPage)
          .as("the deleted comment no longer appears on the branch page")
          .doesNotContain("task used by the end to end test");

      // The reviewer approves the branch
      HttpResponse<String> approved = postForm("/branches/" + branch + "/approve");
      assertThat(approved.statusCode())
          .as("approving redirects (3xx) to /branches")
          .isBetween(300, 399);
      String branchesAfterApprove = get("/branches");
      assertThat(branchesAfterApprove)
          .as("the approved branch is no longer pending review")
          .doesNotContain(branch);
      assertThat(branchesAfterApprove)
          .as("the provenance graph is never listed as a branch")
          .doesNotContain("provenance");

      // The approved assertions carry provenance on the describe page
      String describePage = get("/describe?uri=example://kb/E2ETask");
      assertThat(describePage).contains("edited end to end task");
      assertThat(describePage).contains("statement-created");

      // The agent finds the curated assertions over MCP search
      String found = getText(client, "search", Map.of("text", "edited"));
      assertThat(found).contains("E2ETask");
    } finally {
      client.close();
    }

    // The reviewer finds them through the UI search bar
    String searchPage = get("/search?q=edited");
    assertThat(searchPage).contains("describe?uri=example://kb/E2ETask");
    assertThat(searchPage).contains("edited end to end task");
  }

  /**
   * Exercises {@code /export} and {@code /import} over real HTTP — including genuine {@code
   * multipart/form-data} decoding by Spring MVC's {@code MultipartFile} binding, which the
   * controller's fake-based unit test cannot observe since it calls the Java method directly with a
   * {@code MockMultipartFile}.
   */
  @Test
  void archiveExportAndImportShouldRoundTripOverRealMultipartHttp()
      throws IOException, InterruptedException {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:ArchiveTask :assignedTo kb:ArchiveAgent .
        """;
    String branch;
    try (McpSyncClient client = mcpClient()) {
      String ingested = getText(client, "ingest", Map.of("ttl", ttl));
      Matcher matcher = BRANCH.matcher(ingested);
      assertThat(matcher.find()).isTrue();
      branch = matcher.group(1);
    }
    HttpResponse<String> approved = postForm("/branches/" + branch + "/approve");
    assertThat(approved.statusCode()).isBetween(300, 399);

    HttpRequest exportRequest = HttpRequest.newBuilder(getUri("/export")).GET().build();
    HttpResponse<String> exportResponse =
        http.send(exportRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(exportResponse.statusCode()).isEqualTo(200);
    assertThat(exportResponse.headers().firstValue("Content-Type"))
        .hasValueSatisfying(contentType -> assertThat(contentType).contains("text/turtle"));
    assertThat(exportResponse.headers().firstValue("Content-Disposition"))
        .as("the download names the file as an attachment")
        .hasValueSatisfying(
            disposition -> {
              assertThat(disposition).contains("attachment");
              assertThat(disposition).matches(".*filename=\"cortex-assertions-.*\\.ttl\".*");
            });
    String exported = exportResponse.body();
    assertThat(exported).contains("ArchiveTask");

    // Counted rather than asserted absolutely: every test in this class shares one context, and so
    // one dataset, and a sibling may legitimately have branches pending.
    long branchesBeforeImport = countBranches(get("/branches"));

    HttpResponse<String> importResponse =
        postMultipartFile("/import", "file", "assertions.ttl", "text/turtle", exported);
    assertThat(importResponse.statusCode())
        .as("importing redirects (3xx) to /branches")
        .isBetween(300, 399);

    // The round trip still holds, but for a different reason than when /import was a destructive
    // restore: an export is now instance data that is already approved, so ingest finds nothing
    // novel in it, stages no branch, and leaves the graph exactly as it was.
    assertThat(countBranches(get("/branches")))
        .as("re-importing an export stages no branch: every statement in it is already approved")
        .isEqualTo(branchesBeforeImport);

    HttpResponse<String> reExport = http.send(exportRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(reExport.body())
        .as("re-importing an export leaves the approved assertions byte-for-byte unchanged")
        .isEqualTo(exported);
  }

  private static long countBranches(String branchesPage) {
    return Pattern.compile("class=\"branch-link\"").matcher(branchesPage).results().count();
  }

  /**
   * Exercises {@code /branches/{branch}/rename} over real HTTP, the one endpoint of {@link
   * ai.chatur.cortex.spring.branch.BranchEditController} the workflow test above does not already
   * cover at the HTTP layer (it covers {@code /update} and {@code /approve}).
   */
  @Test
  void renameBranchSubjectsShouldRenameOverRealHttp() throws IOException, InterruptedException {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:RenameTask :assignedTo kb:RenameAgent .
        """;
    String branch;
    try (McpSyncClient client = mcpClient()) {
      String ingested = getText(client, "ingest", Map.of("ttl", ttl));
      Matcher matcher = BRANCH.matcher(ingested);
      assertThat(matcher.find()).isTrue();
      branch = matcher.group(1);
    }

    String renames =
        """
        [{"subject":"example://kb/RenameAgent","newSubject":"example://kb/RenamedAgent"}]
        """;
    HttpResponse<String> renamed = postJson("/branches/" + branch + "/rename", renames);
    assertThat(renamed.statusCode()).isEqualTo(204);

    String branchPage = get("/branches/" + branch);
    assertThat(branchPage)
        .as("the renamed IRI appears on the branch page")
        .contains("RenamedAgent");
    assertThat(branchPage)
        .as("the pre-rename IRI no longer appears as an object")
        .doesNotContain("data-object=\"example://kb/RenameAgent\"");
  }

  McpSyncClient mcpClient() {
    McpSyncClient client =
        McpClient.sync(
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
            .build();
    client.initialize();
    return client;
  }

  String getText(McpSyncClient client, String tool, Map<String, Object> arguments) {
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    assertThat(result.isError() == null || !result.isError())
        .as("the MCP tool call did not report an error")
        .isTrue();
    return ((McpSchema.TextContent) result.content().getFirst()).text();
  }

  String get(String path) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(getUri(path)).GET().build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("GET " + path).isEqualTo(200);
    return response.body();
  }

  HttpResponse<String> postJson(String path, String json) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(getUri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> postForm(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(getUri(path)).POST(HttpRequest.BodyPublishers.noBody()).build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Hand-encodes a real {@code multipart/form-data} request body carrying a single file part, so
   * the test exercises Spring MVC's {@code MultipartFile} binding for real rather than through a
   * {@code MockMultipartFile} handed directly to the controller method.
   */
  HttpResponse<String> postMultipartFile(
      String path, String fieldName, String filename, String contentType, String fileContent)
      throws IOException, InterruptedException {
    String boundary = "----CortexE2EBoundary" + System.nanoTime();
    String body =
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\""
            + fieldName
            + "\"; filename=\""
            + filename
            + "\"\r\n"
            + "Content-Type: "
            + contentType
            + "\r\n\r\n"
            + fileContent
            + "\r\n--"
            + boundary
            + "--\r\n";
    HttpRequest request =
        HttpRequest.newBuilder(getUri(path))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  URI getUri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
