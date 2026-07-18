package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.RestoreService;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import ai.chatur.cortex.spring.CortexProperties;
import org.apache.jena.ontapi.model.OntModel;
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
 * Restore-on-startup auto-configuration for Cortex: downloads the most recent backup from S3 and
 * loads it into the assertions store before the application serves traffic, so a replaced instance
 * on a fresh volume comes up with its data.
 *
 * <p>The inverse of {@link CortexBackupAutoConfiguration}, and a deliberately independent switch
 * from it, in the same way {@link CortexS3AutoConfiguration} is independent: an instance may
 * restore without scheduling backups of its own, or take backups without restoring. It treats S3 as
 * the source of truth and the local store as replaceable — the restore is a wipe-and-load that runs
 * on every boot (see {@link RestoreService}). A bucket with no backup is not an error; a first-ever
 * deployment starts empty.
 *
 * <p>Off by default, and like backups it cannot simply be switched on: it needs {@code
 * cortex.persistent=true}, an enabled and configured {@link CortexProperties.S3} client, and the
 * {@code software.amazon.awssdk:s3} and {@code software.amazon.awssdk:apache-client} dependencies
 * the starter does not bring. Every requirement is checked in this class's constructor, so a
 * half-configured restore fails the context at startup naming what is missing rather than silently
 * booting with an empty graph the operator expected to be seeded.
 *
 * <p>The bean touching the AWS SDK lives in the imported {@link RestoreExecutionConfiguration},
 * behind {@code @ConditionalOnClass}, and the S3 client itself in {@link
 * CortexS3AutoConfiguration}. This class names neither the SDK in a method signature — otherwise a
 * missing optional dependency would surface as a {@code NoClassDefFoundError} while Spring
 * introspects this class, pre-empting the explanation the constructor is here to give.
 */
@AutoConfiguration(after = {CortexAutoConfiguration.class, CortexS3AutoConfiguration.class})
@ConditionalOnProperty(prefix = "cortex.restore", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CortexProperties.class)
@Import(RestoreExecutionConfiguration.class)
public class CortexRestoreAutoConfiguration {

  private static final String S3_CLIENT_CLASS = "software.amazon.awssdk.services.s3.S3Client";
  private static final String APACHE_CLIENT_CLASS =
      "software.amazon.awssdk.http.apache.ApacheHttpClient";

  /**
   * Creates the auto-configuration, validating that a restore can actually run.
   *
   * @param properties the Cortex configuration properties
   * @throws IllegalStateException if the assertions are not persistent, if the AWS SDK is missing
   *     from the classpath, or if the S3 client is disabled or unconfigured
   */
  public CortexRestoreAutoConfiguration(CortexProperties properties) {
    if (!properties.persistent()) {
      throw new IllegalStateException(
          "cortex.restore.enabled=true requires cortex.persistent=true. A restore loads a backup"
              + " into the on-disk TDB2 store; with cortex.persistent=false the store is in memory"
              + " and thrown away at shutdown, so restoring into it every boot would seed a store"
              + " nothing outlives. Set cortex.persistent=true (and cortex.assertionsLocation), or"
              + " set cortex.restore.enabled=false.");
    }
    requireClass(S3_CLIENT_CLASS, "software.amazon.awssdk:s3");
    requireClass(APACHE_CLIENT_CLASS, "software.amazon.awssdk:apache-client");
    if (!properties.s3().enabled()) {
      throw new IllegalStateException(
          "cortex.restore.enabled=true requires cortex.s3.enabled=true: the backup to restore is"
              + " downloaded from S3, and there is nowhere else to fetch it from.");
    }
    require(properties.s3().bucket(), "cortex.s3.bucket");
    require(properties.s3().region(), "cortex.s3.region");
    if (properties.s3().auth() == CortexProperties.S3.Auth.STATIC) {
      require(properties.s3().accessKeyId(), "cortex.s3.access-key-id");
      require(properties.s3().secretAccessKey(), "cortex.s3.secret-access-key");
    }
  }

  private static void requireClass(String className, String coordinates) {
    if (!ClassUtils.isPresent(className, CortexRestoreAutoConfiguration.class.getClassLoader())) {
      throw new IllegalStateException(
          "cortex.restore.enabled=true requires "
              + coordinates
              + " on the classpath, but "
              + className
              + " was not found. The Cortex starter does not bring it, since restore is off by"
              + " default — add the dependency, or set cortex.restore.enabled=false.");
    }
  }

  private static void require(String value, String property) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(property + " is required when cortex.restore.enabled=true");
    }
  }

  /**
   * Creates the service that loads a downloaded backup into the assertions store.
   *
   * <p>Registered here rather than in {@link CortexAutoConfiguration} because a restore is an
   * operation on the store, not on the knowledge graph, and only exists when restore is switched on
   * — see {@link RestoreService} for why it is deliberately not part of {@link
   * ai.chatur.cortex.Cortex}. It names no AWS type, so it lives on this class rather than in the
   * {@code @ConditionalOnClass}-gated {@link RestoreExecutionConfiguration}.
   *
   * @param assertions the assertions dataset the backup is loaded into
   * @param ontModel the ontology model, whose prefixes are re-seeded after the load
   * @return the restore service
   */
  @Bean
  @ConditionalOnMissingBean
  RestoreService restoreService(@Qualifier("assertions") Dataset assertions, OntModel ontModel) {
    return new RestoreService(assertions, ontModel);
  }
}
