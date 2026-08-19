package ai.chatur.cortex.spring.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.spring.support.FakeBranches;
import ai.chatur.cortex.spring.support.FakeIngestor;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.ElicitationSpec;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Plain JUnit test for {@link IngestTools}, against hand-rolled fakes of its two narrow Phase-3
 * role dependencies ({@link ai.chatur.cortex.CortexIngestor}, {@link
 * ai.chatur.cortex.CortexBranches}) rather than a Spring context.
 *
 * <p>Beyond delegation, what these pin is the guarantee that makes {@code autoApprove} safe to
 * expose to an agent at all: nothing is ever approved unless a human answered yes.
 */
class IngestToolsTests {

  private static final String TTL = "kb:Task kb:assignedTo kb:Agent .";

  @Test
  void ingestShouldDelegateToCortexIngest() {
    IngestResult expected = new IngestResult(true, "branch-1", null);
    FakeIngestor ingestor = new FakeIngestor(expected);
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.ACCEPT, true);
    IngestTools tools = new IngestTools(ingestor, branches);

    IngestResult result = tools.ingest(context, TTL, null);

    assertThat(result).isSameAs(expected);
    assertThat(ingestor.ingested()).isEqualTo(TTL);
    assertThat(context.elicitations).as("an omitted autoApprove asks the human nothing").isEmpty();
    assertThat(branches.approvedBranch)
        .as(
            "ingestion only ever stages: nothing is approved unless the caller asked and a human"
                + " confirmed")
        .isNull();
  }

  @Test
  void ingestShouldNotApproveWhenAutoApproveIsFalse() {
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.ACCEPT, true);
    IngestTools tools =
        new IngestTools(new FakeIngestor(new IngestResult(true, "branch-1", null)), branches);

    tools.ingest(context, TTL, false);

    assertThat(context.elicitations).isEmpty();
    assertThat(branches.approvedBranch).isNull();
  }

  @Test
  void ingestShouldApproveWhenTheHumanConfirms() {
    IngestResult expected = new IngestResult(true, "branch-1", null);
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.ACCEPT, true);
    IngestTools tools = new IngestTools(new FakeIngestor(expected), branches);

    IngestResult result = tools.ingest(context, TTL, true);

    assertThat(result).isSameAs(expected);
    assertThat(context.elicitations)
        .as("the human is asked, and the question names the branch they are approving")
        .singleElement()
        .asString()
        .contains("branch-1");
    assertThat(branches.approvedBranch).isEqualTo("branch-1");
  }

  @Test
  void ingestShouldNotApproveWhenTheHumanDeclines() {
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.DECLINE, false);
    IngestTools tools =
        new IngestTools(new FakeIngestor(new IngestResult(true, "branch-1", null)), branches);

    assertThatIllegalStateException()
        .isThrownBy(() -> tools.ingest(context, TTL, true))
        .withMessageContaining("branch-1");

    assertThat(branches.approvedBranch)
        .as("a decline leaves the assertions staged for review")
        .isNull();
  }

  @Test
  void ingestShouldNotApproveWhenTheConfirmationIsSubmittedButAnswersNo() {
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.ACCEPT, false);
    IngestTools tools =
        new IngestTools(new FakeIngestor(new IngestResult(true, "branch-1", null)), branches);

    assertThatIllegalStateException()
        .isThrownBy(() -> tools.ingest(context, TTL, true))
        .withMessageContaining("branch-1");

    assertThat(branches.approvedBranch)
        .as("submitting the form is not the consent; the answer inside it is")
        .isNull();
  }

  @Test
  void ingestShouldNotApproveWhenTheClientCannotAskTheHuman() {
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.withoutElicitation();
    IngestTools tools =
        new IngestTools(new FakeIngestor(new IngestResult(true, "branch-1", null)), branches);

    assertThatIllegalStateException()
        .isThrownBy(() -> tools.ingest(context, TTL, true))
        .withMessageContaining("elicitation");

    assertThat(branches.approvedBranch)
        .as("a client that cannot ask is a refusal, not consent")
        .isNull();
  }

  @Test
  void ingestShouldAskNothingWhenNothingWasStaged() {
    IngestResult expected = new IngestResult(false, null, "not valid");
    FakeBranches branches = FakeBranches.withNoBranches();
    FakeContext context = FakeContext.answering(Action.ACCEPT, true);
    IngestTools tools = new IngestTools(new FakeIngestor(expected), branches);

    IngestResult result = tools.ingest(context, TTL, true);

    assertThat(result).isSameAs(expected);
    assertThat(context.elicitations)
        .as("there is no branch to approve, so the human is not interrupted")
        .isEmpty();
    assertThat(branches.approvedBranch).isNull();
  }

  /**
   * Hand-rolled fake of {@link McpSyncRequestContext} that answers every elicitation with a canned
   * verdict and records the questions it was asked. Everything the ingest tool does not call
   * throws, so a fake this narrow stays honest about what is actually exercised.
   */
  private static final class FakeContext implements McpSyncRequestContext {

    private static final String UNUSED = "not exercised by the ingest tool";

    private final boolean elicitEnabled;
    private final Action action;
    private final boolean approve;
    private final List<String> elicitations = new ArrayList<>();

    private FakeContext(boolean elicitEnabled, Action action, boolean approve) {
      this.elicitEnabled = elicitEnabled;
      this.action = action;
      this.approve = approve;
    }

    private static FakeContext answering(Action action, boolean approve) {
      return new FakeContext(true, action, approve);
    }

    private static FakeContext withoutElicitation() {
      return new FakeContext(false, Action.CANCEL, false);
    }

    @Override
    public boolean elicitEnabled() {
      return elicitEnabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> StructuredElicitResult<T> elicit(Consumer<ElicitationSpec> spec, Class<T> type) {
      RecordingSpec recording = new RecordingSpec();
      spec.accept(recording);
      elicitations.add(recording.message);
      return new StructuredElicitResult<>(action, (T) new ApprovalConfirmation(approve), Map.of());
    }

    @Override
    public boolean rootsEnabled() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.ListRootsResult roots() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public <T> StructuredElicitResult<T> elicit(Class<T> type) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public <T> StructuredElicitResult<T> elicit(ParameterizedTypeReference<T> type) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public <T> StructuredElicitResult<T> elicit(
        Consumer<ElicitationSpec> spec, ParameterizedTypeReference<T> type) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.ElicitResult elicit(McpSchema.ElicitRequest request) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public boolean sampleEnabled() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.CreateMessageResult sample(String... messages) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.CreateMessageResult sample(Consumer<SamplingSpec> spec) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.CreateMessageResult sample(McpSchema.CreateMessageRequest request) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void progress(int percentage) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void progress(Consumer<ProgressSpec> spec) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void progress(McpSchema.ProgressNotification notification) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void ping() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void log(Consumer<LoggingSpec> spec) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void debug(String message) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void info(String message) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void warn(String message) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public void error(String message) {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSyncServerExchange exchange() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.Request request() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public String sessionId() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.Implementation clientInfo() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpSchema.ClientCapabilities clientCapabilities() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public Map<String, Object> requestMeta() {
      throw new UnsupportedOperationException(UNUSED);
    }

    @Override
    public McpTransportContext transportContext() {
      throw new UnsupportedOperationException(UNUSED);
    }

    /** Captures the message the tool put on the elicitation it raised. */
    private static final class RecordingSpec implements ElicitationSpec {

      private String message;

      @Override
      public ElicitationSpec message(String message) {
        this.message = message;
        return this;
      }

      @Override
      public ElicitationSpec meta(Map<String, Object> meta) {
        return this;
      }

      @Override
      public ElicitationSpec meta(String key, Object value) {
        return this;
      }
    }
  }
}
