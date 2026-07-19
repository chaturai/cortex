package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.chatur.cortex.core.store.BackupService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Plain JUnit tests for {@link BackupShutdownLifecycle}, against a mocked {@link Scheduler} and a
 * {@link BackupRunner} over mocks — no Spring context, no scheduler thread pool, no network.
 */
class BackupShutdownLifecycleTests {

  private static final JobDetail JOB =
      JobBuilder.newJob(BackupJob.class).withIdentity("cortexBackupJob", "cortex").build();

  private static BackupRunner runner(S3Client s3Client, Path backup) {
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenReturn(backup.toString());
    return new BackupRunner(backupService, s3Client, "cortex-backups", "cortex/");
  }

  private static Path backupFile(Path dir) throws IOException {
    return Files.writeString(dir.resolve("backup_2026-07-19_020000.nq.gz"), "gzip-bytes");
  }

  @Test
  void stopShouldTakeAndUploadAFinalBackup(@TempDir Path dir) throws IOException {
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(
            () -> mock(Scheduler.class), JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldPutTheSchedulerInStandbyBeforeBackingUp(@TempDir Path dir) throws Exception {
    Scheduler scheduler = mock(Scheduler.class);
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> scheduler, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    InOrder order = inOrder(scheduler, s3Client);
    order.verify(scheduler).standby();
    order.verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldWaitForAnInFlightBackupBeforeTakingItsOwn(@TempDir Path dir) throws Exception {
    Scheduler scheduler = mock(Scheduler.class);
    JobExecutionContext executing = mock(JobExecutionContext.class);
    when(executing.getJobDetail()).thenReturn(JOB);
    AtomicInteger polls = new AtomicInteger();
    when(scheduler.getCurrentlyExecutingJobs())
        .thenAnswer(
            invocation ->
                polls.incrementAndGet() < 3 ? List.of(executing) : List.<JobExecutionContext>of());
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> scheduler, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    assertThat(polls.get())
        .as("the scheduled backup is polled until it drains, so the two never run at once")
        .isGreaterThanOrEqualTo(3);
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldIgnoreOtherJobsStillExecuting(@TempDir Path dir) throws Exception {
    Scheduler scheduler = mock(Scheduler.class);
    JobExecutionContext other = mock(JobExecutionContext.class);
    when(other.getJobDetail())
        .thenReturn(JobBuilder.newJob(BackupJob.class).withIdentity("someOtherJob", "app").build());
    when(scheduler.getCurrentlyExecutingJobs()).thenReturn(List.of(other));
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> scheduler, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldSkipQuiescingAnAlreadyShutDownScheduler(@TempDir Path dir) throws Exception {
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.isShutdown()).thenReturn(true);
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> scheduler, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    verify(scheduler, never()).standby();
    verify(s3Client, description("a scheduler already down is no reason to skip the final backup"))
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldStillBackUpWhenTheSchedulerCannotBeQuiesced(@TempDir Path dir) throws Exception {
    Scheduler scheduler = mock(Scheduler.class);
    doThrow(new SchedulerException("scheduler is broken")).when(scheduler).standby();
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> scheduler, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    verify(s3Client, description("a scheduler that cannot be quiesced is not a reason to skip it"))
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldStillBackUpWhenThereIsNoScheduler(@TempDir Path dir) throws IOException {
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(() -> null, JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();

    verify(s3Client, description("the scheduler is optional; the final backup is not"))
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldPropagateBackupFailures(@TempDir Path dir) throws IOException {
    S3Client s3Client = mock(S3Client.class);
    doThrow(new IllegalStateException("no such bucket"))
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(
            () -> mock(Scheduler.class), JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();

    assertThatThrownBy(lifecycle::stop)
        .as(
            "Spring's lifecycle processor logs this and continues, so a failing backup cannot make"
                + " the application impossible to restart")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void stopShouldBeIdempotent(@TempDir Path dir) throws IOException {
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(
            () -> mock(Scheduler.class), JOB, runner(s3Client, backupFile(dir)));

    lifecycle.start();
    lifecycle.stop();
    lifecycle.stop();

    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void stopShouldDoNothingWhenNeverStarted(@TempDir Path dir) throws IOException {
    S3Client s3Client = mock(S3Client.class);
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(
            () -> mock(Scheduler.class), JOB, runner(s3Client, backupFile(dir)));

    lifecycle.stop();

    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void isRunningShouldTrackWhetherTheFinalBackupIsStillPending(@TempDir Path dir)
      throws IOException {
    BackupShutdownLifecycle lifecycle =
        new BackupShutdownLifecycle(
            () -> mock(Scheduler.class), JOB, runner(mock(S3Client.class), backupFile(dir)));

    assertThat(lifecycle.isRunning()).isFalse();
    lifecycle.start();
    assertThat(lifecycle.isRunning()).isTrue();
    lifecycle.stop();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void phaseShouldStopAfterEveryPhaseThatCanStillWriteToTheStore() {
    assertThat(BackupShutdownLifecycle.PHASE)
        .as(
            "phases stop in descending order, so a phase at or above Boot's web server"
                + " (Integer.MAX_VALUE - 2048), its graceful shutdown (- 1024), or"
                + " SchedulerFactoryBean (Integer.MAX_VALUE) would snapshot a store still being"
                + " written to")
        .isLessThan(Integer.MAX_VALUE - 2048);
  }
}
