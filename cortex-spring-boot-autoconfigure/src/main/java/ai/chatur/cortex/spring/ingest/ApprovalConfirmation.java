package ai.chatur.cortex.spring.ingest;

/**
 * The answer a human gives when {@link IngestTools#ingest} asks — over MCP elicitation — whether it
 * may approve staged assertions without review.
 *
 * @param approve whether the staged assertions may be merged into the knowledge graph immediately
 */
public record ApprovalConfirmation(boolean approve) {}
