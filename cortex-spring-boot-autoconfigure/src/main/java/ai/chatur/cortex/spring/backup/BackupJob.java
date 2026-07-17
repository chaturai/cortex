package ai.chatur.cortex.spring.backup;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz adapter around {@link BackupRunner}.
 *
 * <p>Deliberately holds no behavior of its own: everything worth testing lives in {@link
 * BackupRunner}, which needs neither a scheduler nor a Quartz context to exercise.
 *
 * <p>Instantiated by Quartz, not by Spring: Boot's {@code QuartzAutoConfiguration} installs a
 * {@code SpringBeanJobFactory} with the application context set, which creates each job through
 * {@code getAutowireCapableBeanFactory().createBean(jobClass)}. This class therefore has exactly
 * <strong>one</strong> constructor, which is what makes that constructor injection unambiguous —
 * adding a second would break the wiring at runtime rather than at compile time.
 *
 * <p>{@link DisallowConcurrentExecution} matters at short intervals: a backup of a large store can
 * outlast its own interval. Overlapping runs would not corrupt anything — TDB2 serializes them
 * inside read transactions — but they would thrash disk and upload bandwidth to no benefit.
 */
@DisallowConcurrentExecution
public class BackupJob implements Job {

  private final BackupRunner runner;

  /**
   * Creates the job.
   *
   * @param runner the runner that takes and uploads the backup
   */
  public BackupJob(BackupRunner runner) {
    this.runner = runner;
  }

  /**
   * Runs one backup.
   *
   * <p>Failures are wrapped rather than swallowed, so Quartz reports them. The wrap passes {@code
   * refireImmediately = false} deliberately: a durable failure — bad credentials, a full disk, a
   * missing bucket — would otherwise hot-loop. The next scheduled fire still happens, so a
   * transient failure recovers on its own.
   *
   * @param context the Quartz execution context, unused
   * @throws JobExecutionException if the backup or its upload fails
   */
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      runner.run();
    } catch (RuntimeException e) {
      throw new JobExecutionException("Scheduled Cortex backup failed", e, false);
    }
  }
}
