package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.BackupService;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import ai.chatur.cortex.spring.CortexProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Registers the runner that takes and uploads a backup, and the Quartz job and trigger that drive
 * it.
 *
 * <p>Imported by {@link CortexBackupAutoConfiguration}, and separated from it so that {@code
 * spring-boot-starter-quartz} and {@code software.amazon.awssdk:s3} can stay {@code compileOnly}
 * dependencies of this module: every type from either is named here, behind {@link
 * ConditionalOnClass @ConditionalOnClass}, rather than in a class Spring introspects
 * unconditionally.
 *
 * <p>Nothing here re-checks the configuration — {@link CortexBackupAutoConfiguration}'s constructor
 * has already established that both libraries are present and that S3 is enabled, so by the time
 * these beans are built an {@link S3Client} exists.
 *
 * <p>Spring Boot's {@code QuartzAutoConfiguration} collects every {@link JobDetail} and {@link
 * Trigger} bean into its {@link SchedulerFactoryBean}, so nothing here touches the {@link
 * Scheduler} directly.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Scheduler.class, SchedulerFactoryBean.class, S3Client.class})
class BackupJobConfiguration {

  /**
   * Creates the runner holding the backup logic.
   *
   * @param backupService takes the TDB2 snapshot
   * @param cortexS3Client the client the snapshot is uploaded through
   * @param properties the Cortex configuration properties
   * @return the backup runner
   */
  @Bean
  @ConditionalOnMissingBean
  BackupRunner backupRunner(
      BackupService backupService, S3Client cortexS3Client, CortexProperties properties) {
    return new BackupRunner(
        backupService, cortexS3Client, properties.s3().bucket(), properties.backup().keyPrefix());
  }

  /**
   * Creates the Quartz job detail for {@link BackupJob}.
   *
   * <p><strong>Matched by bean name, not by type.</strong> A bare {@code @ConditionalOnMissingBean}
   * matches on the {@link JobDetail} return type, so it would back off entirely for any consumer
   * who already has a Quartz job of their own — silently disabling backups on exactly the
   * applications most likely to be using Quartz, with nothing thrown. Boot collects {@code
   * ObjectProvider<JobDetail>} precisely because several job beans are the norm. This is the same
   * trap the two {@code Dataset} beans in {@link CortexAutoConfiguration} document.
   *
   * @return the backup job detail
   */
  @Bean
  @ConditionalOnMissingBean(name = "cortexBackupJobDetail")
  JobDetail cortexBackupJobDetail() {
    return JobBuilder.newJob(BackupJob.class)
        .withIdentity("cortexBackupJob", "cortex")
        .storeDurably()
        .build();
  }

  /**
   * Creates the trigger firing the backup job every {@code cortex.backup.interval}.
   *
   * <p>Matched by bean name for the same reason as the job detail.
   *
   * <p>The first fire is deliberately one interval away rather than at scheduler startup: otherwise
   * every restart would leave a backup behind, which on a machine running devtools means a
   * directory full of them within the hour.
   *
   * <p>Misfires are handled with {@code NextWithRemainingCount}, so an application that was down or
   * paused resumes on schedule rather than firing every backup it missed back to back.
   *
   * @param properties the Cortex configuration properties
   * @param cortexBackupJobDetail the job to fire
   * @return the backup trigger
   */
  @Bean
  @ConditionalOnMissingBean(name = "cortexBackupTrigger")
  Trigger cortexBackupTrigger(CortexProperties properties, JobDetail cortexBackupJobDetail) {
    Duration interval = properties.backup().interval();
    return TriggerBuilder.newTrigger()
        .withIdentity("cortexBackupTrigger", "cortex")
        .forJob(cortexBackupJobDetail)
        .startAt(Date.from(Instant.now().plus(interval)))
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(interval.toMillis())
                .repeatForever()
                .withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }
}
