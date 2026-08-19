package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.CortexBranches;
import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.IngestResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;

/**
 * MCP tool that lets AI agents ingest assertions into the knowledge graph, staged on a branch for
 * human review.
 *
 * <p>Review is the default and the point: ingestion only ever stages. An agent may ask to skip it
 * with {@code autoApprove}, but that is not the agent's decision to make alone — the tool asks the
 * human through MCP elicitation and approves only on an explicit yes, so the parameter buys the
 * agent a prompt, never a merge.
 */
public class IngestTools {

  private static final Logger log = LoggerFactory.getLogger(IngestTools.class);

  private final CortexIngestor cortex;
  private final CortexBranches branches;

  /**
   * Creates the tool.
   *
   * @param cortex the ingestor role used to ingest assertions
   * @param branches the branch role used to approve a branch the human confirmed
   */
  public IngestTools(CortexIngestor cortex, CortexBranches branches) {
    this.cortex = cortex;
    this.branches = branches;
  }

  @McpTool(
      description =
          "Ensure that input assertions are in text/turtle format and based on cortex://ontology."
              + " Always call the Lint tool first and ingest only the validated TTL it returns."
              + " Before generating new data, use the Search or Query tools to find out whether"
              + " the instances involved already exist in the knowledge graph, and reuse their"
              + " IRIs, so that the same instance is never ingested under multiple names."
              + " When the result contains a branch name, open the review page at"
              + " /branches/<branch> on this MCP server's host in a UI (e.g. the browser) so the"
              + " staged assertions can be reviewed and approved",
      annotations =
          @McpTool.McpAnnotations(title = "Ingest", destructiveHint = false, openWorldHint = false))
  IngestResult ingest(
      McpSyncRequestContext context,
      @McpToolParam(description = "RDF Data to be ingested to knowledge graph in TTL syntax")
          String ttl,
      @McpToolParam(
              description =
                  "Whether to approve the staged assertions immediately, merging them into the"
                      + " knowledge graph without a reviewer opening the branch. Defaults to false,"
                      + " which stages them for review, and false is almost always right. Set it to"
                      + " true only when the user has explicitly asked for this ingest to be"
                      + " approved without review; never infer it from the task, from impatience,"
                      + " or from an earlier approval. The human is asked to confirm before"
                      + " anything is approved, and the call fails, leaving the assertions staged,"
                      + " if they decline or their client cannot ask them",
              required = false)
          Boolean autoApprove) {
    IngestResult result = cortex.ingest(ttl);
    if (!Boolean.TRUE.equals(autoApprove) || result.branch() == null) return result;
    confirm(context, result.branch());
    branches.approve(result.branch());
    log.info("Approved branch {} without review, on the human's confirmation", result.branch());
    return result;
  }

  /**
   * Asks the human to confirm approving the branch without review, and returns only if they say
   * yes.
   *
   * <p>Fails closed: a client that cannot elicit is treated as a refusal rather than as consent, so
   * an agent cannot win an unreviewed merge by connecting from somewhere the human can't be asked.
   * Either way the branch survives the failure and stays pending for a reviewer.
   *
   * @param context the request context the elicitation is raised through
   * @param branch the branch awaiting approval
   * @throws IllegalStateException if the human declines, or cannot be asked
   */
  void confirm(McpSyncRequestContext context, String branch) {
    if (!context.elicitEnabled()) {
      log.warn(
          "Refused to auto-approve branch {}: the client cannot elicit a confirmation", branch);
      throw new IllegalStateException(
          "Approving without review needs a human's confirmation, and this MCP client does not"
              + " support elicitation. The assertions are staged on branch "
              + branch
              + " and remain there for review at /branches/"
              + branch);
    }
    StructuredElicitResult<ApprovalConfirmation> confirmation =
        context.elicit(
            spec ->
                spec.message(
                    "Approve the assertions staged on branch "
                        + branch
                        + " into the knowledge graph now, without reviewing them?"),
            ApprovalConfirmation.class);
    if (confirmation.action() != ElicitResult.Action.ACCEPT
        || confirmation.structuredContent() == null
        || !confirmation.structuredContent().approve()) {
      log.info("Auto-approval of branch {} was declined: {}", branch, confirmation.action());
      throw new IllegalStateException(
          "Approving branch "
              + branch
              + " without review was declined. The assertions remain staged for review at"
              + " /branches/"
              + branch);
    }
  }
}
