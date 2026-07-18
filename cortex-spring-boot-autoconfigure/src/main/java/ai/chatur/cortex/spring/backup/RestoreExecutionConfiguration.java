package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.RestoreService;
import ai.chatur.cortex.spring.CortexProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Registers the runner that downloads and loads the latest backup, and the bootstrap that runs it
 * once at startup.
 *
 * <p>Imported by {@link CortexRestoreAutoConfiguration}, and separated from it so that {@code
 * software.amazon.awssdk:s3} can stay a {@code compileOnly} dependency of this module: the {@link
 * S3Client} type is named here, behind {@link ConditionalOnClass @ConditionalOnClass}, rather than
 * in a class Spring introspects unconditionally — the same split {@link BackupJobConfiguration}
 * uses. {@link CortexRestoreAutoConfiguration}'s constructor has already established that the SDK
 * is present and that S3 is enabled, so an {@link S3Client} exists by the time these beans are
 * built.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(S3Client.class)
class RestoreExecutionConfiguration {

  /**
   * Creates the runner holding the restore logic.
   *
   * @param restoreService loads a downloaded backup into the store
   * @param cortexS3Client the client the backup is listed and downloaded through
   * @param properties the Cortex configuration properties
   * @return the restore runner
   */
  @Bean
  @ConditionalOnMissingBean
  RestoreRunner restoreRunner(
      RestoreService restoreService, S3Client cortexS3Client, CortexProperties properties) {
    return new RestoreRunner(
        restoreService, cortexS3Client, properties.s3().bucket(), properties.restore().keyPrefix());
  }

  /**
   * Creates the bootstrap that runs the restore once, during context refresh.
   *
   * <p>An {@link InitializingBean} rather than an {@code ApplicationRunner} or an {@code
   * ApplicationReadyEvent} listener on purpose: its {@link #afterPropertiesSet()} runs while the
   * context is still refreshing — after its {@link RestoreRunner} dependency (and so after the
   * assertions dataset and the S3 client it in turn depends on), but before the embedded web server
   * starts accepting traffic and before {@code InferenceInitializer} rebuilds the inference closure
   * on {@code ApplicationReadyEvent}. The store is therefore seeded before anything reads it, and
   * the closure and text index are computed over the restored data with no further wiring.
   *
   * @param restoreRunner the runner to invoke at startup
   * @return the bootstrap
   */
  @Bean
  @ConditionalOnMissingBean
  RestoreBootstrap cortexRestoreBootstrap(RestoreRunner restoreRunner) {
    return new RestoreBootstrap(restoreRunner);
  }

  /**
   * Runs the restore once as the context refreshes.
   *
   * <p>Deliberately holds no behaviour of its own — the restore lives in {@link RestoreRunner},
   * which needs neither Spring nor a lifecycle to exercise. This is the restore counterpart to
   * {@link BackupJob}, the Quartz adapter over {@link BackupRunner}.
   */
  static final class RestoreBootstrap implements InitializingBean {

    private final RestoreRunner restoreRunner;

    RestoreBootstrap(RestoreRunner restoreRunner) {
      this.restoreRunner = restoreRunner;
    }

    /** Runs the restore. Failures propagate, failing the context. */
    @Override
    public void afterPropertiesSet() {
      restoreRunner.run();
    }
  }
}
