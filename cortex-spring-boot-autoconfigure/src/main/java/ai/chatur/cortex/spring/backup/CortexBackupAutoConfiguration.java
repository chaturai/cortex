package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.BackupService;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import ai.chatur.cortex.spring.CortexProperties;
import java.time.Duration;
import org.apache.jena.query.Dataset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.ClassUtils;

/**
 * Backup auto-configuration for Cortex: a Quartz job that periodically snapshots the assertions
 * store, and the S3 client each snapshot is uploaded through.
 *
 * <p>This is the fourth delivery mechanism alongside {@link CortexAutoConfiguration} (the graph
 * itself), {@link ai.chatur.cortex.spring.web.CortexWebAutoConfiguration} (HTTP, to humans), and
 * {@link ai.chatur.cortex.spring.mcp.CortexMcpAutoConfiguration} (MCP, to agents) — it delivers the
 * graph to durable storage, on a schedule. It registers no graph beans of its own, which is what
 * keeps {@code cortex.backup.enabled} a single switch of the same shape as {@code
 * cortex.web.enabled} and {@code cortex.mcp.enabled} rather than a domain-grouped config.
 *
 * <p>Off by default, and unlike the other two it cannot simply be switched on: it needs {@code
 * cortex.persistent=true}, an enabled and configured {@link CortexProperties.S3} client, and
 * dependencies the Cortex starter deliberately does not bring — {@code
 * org.springframework.boot:spring-boot-starter-quartz}, {@code software.amazon.awssdk:s3}, and
 * {@code software.amazon.awssdk:apache-client}. The starter brings what is on by default; this is
 * not.
 *
 * <p>Every one of those requirements is checked in this class's constructor, so a half-configured
 * backup fails the context at startup naming exactly what is missing. That matters more here than
 * anywhere else in Cortex: the alternative is an application that boots clean, reports healthy, and
 * simply never backs anything up — discovered only when someone needs a backup and finds an empty
 * bucket. The classpath checks in particular are deliberately not left to
 * {@code @ConditionalOnClass} back-off, which is the right default for a property nobody set and
 * the wrong one for a consumer who explicitly asked for backups.
 *
 * <p>The beans touching Quartz and the AWS SDK live in the imported {@link BackupJobConfiguration},
 * behind {@code @ConditionalOnClass}, and the S3 client itself in {@link
 * CortexS3AutoConfiguration}. This class deliberately names neither library in a method signature —
 * otherwise a missing optional dependency would surface as a {@code NoClassDefFoundError} while
 * Spring introspects this class, pre-empting the explanation the constructor is here to give.
 */
@AutoConfiguration(after = {CortexAutoConfiguration.class, CortexS3AutoConfiguration.class})
@ConditionalOnProperty(prefix = "cortex.backup", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CortexProperties.class)
@Import(BackupJobConfiguration.class)
public class CortexBackupAutoConfiguration {

  private static final String SCHEDULER_CLASS = "org.quartz.Scheduler";
  private static final String S3_CLIENT_CLASS = "software.amazon.awssdk.services.s3.S3Client";
  private static final String APACHE_CLIENT_CLASS =
      "software.amazon.awssdk.http.apache.ApacheHttpClient";

  /**
   * Creates the auto-configuration, validating that backups can actually run.
   *
   * @param properties the Cortex configuration properties
   * @throws IllegalStateException if the assertions are not persistent, if Quartz or the AWS SDK
   *     are missing from the classpath, if the S3 client is disabled or unconfigured, or if the
   *     backup interval is not positive
   */
  public CortexBackupAutoConfiguration(CortexProperties properties) {
    if (!properties.persistent()) {
      throw new IllegalStateException(
          "cortex.backup.enabled=true requires cortex.persistent=true. A TDB2 backup is an admin"
              + " operation on an on-disk store, and the in-memory store opened when"
              + " cortex.persistent=false has no location to write a backup beside, so every"
              + " scheduled run would throw. Set cortex.persistent=true (and"
              + " cortex.assertionsLocation), or set cortex.backup.enabled=false.");
    }
    requireClass(SCHEDULER_CLASS, "org.springframework.boot:spring-boot-starter-quartz");
    requireClass(S3_CLIENT_CLASS, "software.amazon.awssdk:s3");
    requireClass(APACHE_CLIENT_CLASS, "software.amazon.awssdk:apache-client");
    if (!properties.s3().enabled()) {
      throw new IllegalStateException(
          "cortex.backup.enabled=true requires cortex.s3.enabled=true: backups are uploaded to S3,"
              + " and there is nowhere else to put them.");
    }
    require(properties.s3().bucket(), "cortex.s3.bucket");
    require(properties.s3().region(), "cortex.s3.region");
    if (properties.s3().auth() == CortexProperties.S3.Auth.STATIC) {
      require(properties.s3().accessKeyId(), "cortex.s3.access-key-id");
      require(properties.s3().secretAccessKey(), "cortex.s3.secret-access-key");
    }
    Duration interval = properties.backup().interval();
    if (interval.isZero() || interval.isNegative()) {
      throw new IllegalStateException(
          "cortex.backup.interval must be positive, but was "
              + interval
              + ". A repeat-forever Quartz trigger with a zero interval fires without pause.");
    }
  }

  private static void requireClass(String className, String coordinates) {
    if (!ClassUtils.isPresent(className, CortexBackupAutoConfiguration.class.getClassLoader())) {
      throw new IllegalStateException(
          "cortex.backup.enabled=true requires "
              + coordinates
              + " on the classpath, but "
              + className
              + " was not found. The Cortex starter does not bring it, since backups are off by"
              + " default — add the dependency, or set cortex.backup.enabled=false.");
    }
  }

  private static void require(String value, String property) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(property + " is required when cortex.backup.enabled=true");
    }
  }

  /**
   * Creates the service that snapshots the assertions store.
   *
   * <p>Registered here rather than in {@link CortexAutoConfiguration} because a backup is an
   * operation on the store, not on the knowledge graph, and only exists when backups are switched
   * on — see {@link BackupService} for why it is deliberately not part of {@link
   * ai.chatur.cortex.Cortex}.
   *
   * @param assertions the assertions dataset
   * @return the backup service
   */
  @Bean
  @ConditionalOnMissingBean
  BackupService backupService(@Qualifier("assertions") Dataset assertions) {
    return new BackupService(assertions);
  }
}
