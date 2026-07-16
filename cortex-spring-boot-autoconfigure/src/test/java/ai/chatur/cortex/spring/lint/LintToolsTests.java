package ai.chatur.cortex.spring.lint;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.LintResult;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit test for {@link LintTools}, against a hand-rolled lambda fake of its single narrow
 * Phase-3 role dependency ({@link ai.chatur.cortex.CortexLinter}) rather than a Spring context.
 */
class LintToolsTests {

  @Test
  void lintShouldDelegateToCortexLint() {
    LintResult expected = new LintResult(true, "@prefix kb: <example://kb/> .", null);
    AtomicReference<String> lastTtl = new AtomicReference<>();
    LintTools tools =
        new LintTools(
            ttl -> {
              lastTtl.set(ttl);
              return expected;
            });

    LintResult result = tools.lint("kb:Task kb:assignedTo kb:Agent .");

    assertThat(result).isSameAs(expected);
    assertThat(lastTtl.get()).isEqualTo("kb:Task kb:assignedTo kb:Agent .");
  }
}
