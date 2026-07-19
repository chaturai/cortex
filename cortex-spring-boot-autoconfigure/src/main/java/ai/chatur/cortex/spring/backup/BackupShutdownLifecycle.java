package ai.chatur.cortex.spring.backup;

import java.util.function.Supplier;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Takes one last backup while the context closes, and blocks shutdown until it has uploaded.
 *
 * <p>Without this, a deliberate restart discards every approval made since the last scheduled fire
 * — up to a full {@code cortex.backup.interval}, which defaults to 24 hours. The scheduled job
 * covers the process dying; this covers the process being stopped, which is the far more common
 * case and the only one where waiting is possible at all.
 *
 * <p>Holds no backup logic of its own — {@link BackupRunner} is shared verbatim with {@link
 * BackupJob}, so the shutdown snapshot and the scheduled one are the same operation producing the
 * same kind of object under the same key prefix. There is deliberately nothing that marks an upload
 * as a shutdown backup: {@link RestoreRunner} restores the most recent object under the prefix, and
 * a shutdown backup is only interesting because it is the most recent.
 *
 * <h2>Phase</h2>
 *
 * <p>{@link #PHASE} is below every phase Boot stops before it, so by the time the snapshot is taken
 * nothing can still be writing: Spring's {@code SchedulerFactoryBean} stops at {@link
 * Integer#MAX_VALUE}, graceful shutdown drains in-flight requests at {@code MAX_VALUE - 1024}, and
 * the web server stops accepting connections at {@code MAX_VALUE - 2048}. Raising it above any of
 * those would snapshot a store still taking approvals over HTTP, quietly losing exactly the writes
 * this class exists to preserve.
 *
 * <h2>Waiting</h2>
 *
 * <p>{@link #stop()} runs on the calling thread, which is what makes the wait unbounded. Spring's
 * {@code DefaultLifecycleProcessor} invokes {@link SmartLifecycle#stop(Runnable)}, whose default
 * implementation calls {@link #stop()} and then the callback, so the latch it waits on is already
 * counted down and {@code spring.lifecycle.timeout-per-shutdown-phase} never applies. Handing the
 * work to an executor and counting the latch down on completion would look equivalent and silently
 * cap the upload at that timeout (30s by default) — a large store would then be killed mid-upload
 * on every restart. Don't.
 *
 * <p>Failures propagate out of {@link #stop()}, where {@code DefaultLifecycleProcessor} logs them
 * and continues closing the context. That is the intended split: a backup that <em>fails</em> — bad
 * credentials, a missing bucket — must not make the application impossible to restart, while a
 * backup that is merely <em>slow</em> is waited on however long it takes.
 */
class BackupShutdownLifecycle implements SmartLifecycle {

  /**
   * The lifecycle phase this stops in, below Boot's Quartz, graceful-shutdown, and web-server
   * phases so the final snapshot sees a store nothing is still writing to.
   */
  static final int PHASE = Integer.MAX_VALUE - 4096;

  private static final long POLL_MILLIS = 100;

  private static final Logger log = LoggerFactory.getLogger(BackupShutdownLifecycle.class);

  private final Supplier<Scheduler> scheduler;
  private final JobKey jobKey;
  private final BackupRunner runner;

  private volatile boolean running;

  /**
   * Creates the lifecycle.
   *
   * @param scheduler resolves the scheduler the backup job runs on, or {@code null} if there is
   *     none; resolved lazily, and only at shutdown
   * @param jobDetail the backup job, named only to recognize it among the currently executing jobs
   * @param runner the runner that takes and uploads the backup
   */
  BackupShutdownLifecycle(Supplier<Scheduler> scheduler, JobDetail jobDetail, BackupRunner runner) {
    this.scheduler = scheduler;
    this.jobKey = jobDetail.getKey();
    this.runner = runner;
  }

  /** Marks this running, so that {@link #stop()} is called when the context closes. */
  @Override
  public void start() {
    running = true;
  }

  /**
   * Quiesces the scheduler and takes the final backup, blocking until it has uploaded.
   *
   * @throws RuntimeException if the backup cannot be taken or the upload fails
   */
  @Override
  public void stop() {
    if (!running) {
      return;
    }
    running = false;
    quiesce();
    log.info("Taking a final Cortex backup before shutdown");
    runner.run();
  }

  /**
   * Reports whether the final backup is still pending.
   *
   * @return {@code true} until {@link #stop()} has run
   */
  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * Returns {@link #PHASE}.
   *
   * @return the lifecycle phase this stops in
   */
  @Override
  public int getPhase() {
    return PHASE;
  }

  /**
   * Stops new backups firing and waits for any already in flight.
   *
   * <p>{@link Scheduler#standby()} alone is not enough: it prevents further fires but lets a
   * running job finish, so without the wait a scheduled backup and the shutdown backup could
   * snapshot the same store at once. That is not corrupting — TDB2 serializes them inside read
   * transactions — but it doubles the disk and upload cost of a shutdown, which is the moment least
   * able to afford it.
   *
   * <p>The scheduler is resolved lazily and may be absent: this bean must not force a {@code
   * SchedulerFactoryBean} into existence just to hold a reference to it, and a context that somehow
   * has no scheduler still wants its final backup. Scheduler faults are logged rather than thrown
   * for the same reason — a scheduler that cannot be quiesced is a reason to take the backup
   * carefully, not a reason to skip it.
   */
  private void quiesce() {
    Scheduler scheduler = this.scheduler.get();
    if (scheduler == null) {
      return;
    }
    try {
      if (scheduler.isShutdown()) {
        return;
      }
      scheduler.standby();
      while (isBackupExecuting(scheduler)) {
        log.info("Waiting for the in-flight Cortex backup to finish before shutting down");
        Thread.sleep(POLL_MILLIS);
      }
    } catch (SchedulerException e) {
      log.warn("Could not quiesce the scheduler before the final backup; taking it anyway", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted waiting for the in-flight Cortex backup; taking the final one anyway");
    }
  }

  private boolean isBackupExecuting(Scheduler scheduler) throws SchedulerException {
    for (JobExecutionContext executing : scheduler.getCurrentlyExecutingJobs()) {
      if (jobKey.equals(executing.getJobDetail().getKey())) {
        return true;
      }
    }
    return false;
  }
}
