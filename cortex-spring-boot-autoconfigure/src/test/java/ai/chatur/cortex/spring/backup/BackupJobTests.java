package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionException;

/**
 * Plain JUnit tests for {@link BackupJob}, against a mocked {@link BackupRunner} and no scheduler —
 * the job is a thin adapter, so there is nothing here a Quartz context would add.
 */
class BackupJobTests {

  @Test
  void executeShouldDelegateToTheRunner() throws Exception {
    BackupRunner runner = mock(BackupRunner.class);

    new BackupJob(runner).execute(null);

    verify(runner).run();
  }

  @Test
  void executeShouldWrapFailuresWithoutRefiring() {
    BackupRunner runner = mock(BackupRunner.class);
    RuntimeException failure = new IllegalStateException("no such bucket");
    doThrow(failure).when(runner).run();
    BackupJob job = new BackupJob(runner);

    JobExecutionException thrown =
        catchThrowableOfType(JobExecutionException.class, () -> job.execute(null));

    assertThat(thrown)
        .as("the scheduler must learn the backup failed rather than the failure being swallowed")
        .isNotNull()
        .hasCause(failure);
    assertThat(thrown.refireImmediately())
        .as(
            "a durable failure — bad credentials, a full disk, a missing bucket — would hot-loop if"
                + " Quartz refired immediately; the next scheduled fire is soon enough")
        .isFalse();
  }
}
