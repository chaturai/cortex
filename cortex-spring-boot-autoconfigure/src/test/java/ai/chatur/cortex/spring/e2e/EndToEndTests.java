package ai.chatur.cortex.spring.e2e;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.ai.mcp.server.protocol=stateless", "spring.ai.mcp.server.type=sync"})
public class EndToEndTests {

  @SpringBootApplication
  static class EndToEndApplication {}

  static final Pattern BRANCH = Pattern.compile("\"branch\":\"([^\"]+)\"");

  static final String TTL =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix o: <cortex://ontology/> .
      @prefix : <cortex://assertions/> .

      :E2EAgent a o:Agent ;
          rdfs:label "end to end agent" .

      :E2ETask a o:Task ;
          o:assignedTo :E2EAgent ;
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
      assert (linted.contains("\"valid\":true"));

      String ingested = getText(client, "ingest", Map.of("ttl", TTL));
      assert (ingested.contains("\"valid\":true"));
      Matcher matcher = BRANCH.matcher(ingested);
      assert (matcher.find());
      String branch = matcher.group(1);

      // The branches page lists the branch with its provenance activity badges
      String branchesPage = get("/branches");
      assert (branchesPage.contains(branch));
      assert (branchesPage.contains("prov:Activity"));
      assert (branchesPage.contains("6 triples"));

      // The branch page shows the staged statements grouped by subject
      String branchPage = get("/branches/" + branch);
      assert (branchPage.contains("E2ETask"));
      assert (branchPage.contains("E2EAgent"));
      assert (branchPage.contains("data-subject=\"cortex://assertions/E2ETask\""));
      assert (branchPage.contains("end to end task"));
      assert (branchPage.contains("Approve"));
      assert (branchPage.contains("Delete"));

      // The reviewer edits the task label and deletes its comment
      String changes =
          """
          [
            {"subject":"cortex://assertions/E2ETask",
             "predicate":"http://www.w3.org/2000/01/rdf-schema#label",
             "object":"end to end task","literal":true,
             "datatype":"http://www.w3.org/2001/XMLSchema#string",
             "newObject":"edited end to end task"},
            {"subject":"cortex://assertions/E2ETask",
             "predicate":"http://www.w3.org/2000/01/rdf-schema#comment",
             "object":"task used by the end to end test","literal":true,
             "datatype":"http://www.w3.org/2001/XMLSchema#string",
             "newObject":null}
          ]
          """;
      HttpResponse<String> updated = postJson("/branches/" + branch + "/update", changes);
      assert (updated.statusCode() == 204);

      branchPage = get("/branches/" + branch);
      assert (branchPage.contains("edited end to end task"));
      assert (!branchPage.contains("task used by the end to end test"));

      // The reviewer approves the branch
      HttpResponse<String> approved = postForm("/branches/" + branch + "/approve");
      assert (approved.statusCode() >= 300 && approved.statusCode() < 400);
      String branchesAfterApprove = get("/branches");
      assert (!branchesAfterApprove.contains(branch));
      // The provenance graph is not a branch
      assert (!branchesAfterApprove.contains("provenance"));

      // The approved assertions carry provenance on the describe page
      String describePage = get("/assertions/assertions/E2ETask");
      assert (describePage.contains("edited end to end task"));
      assert (describePage.contains("statement-created"));

      // The agent finds the curated assertions over MCP search
      String found = getText(client, "search", Map.of("text", "edited"));
      assert (found.contains("E2ETask"));
    } finally {
      client.close();
    }

    // The reviewer finds them through the UI search bar
    String searchPage = get("/search?q=edited");
    assert (searchPage.contains("assertions/E2ETask"));
    assert (searchPage.contains("edited end to end task"));
  }

  String getText(McpSyncClient client, String tool, Map<String, Object> arguments) {
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    assert (result.isError() == null || !result.isError());
    return ((McpSchema.TextContent) result.content().getFirst()).text();
  }

  String get(String path) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(getUri(path)).GET().build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assert (response.statusCode() == 200);
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

  URI getUri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
