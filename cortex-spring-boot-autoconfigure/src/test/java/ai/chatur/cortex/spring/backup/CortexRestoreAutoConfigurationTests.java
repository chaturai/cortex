package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.chatur.cortex.core.store.RestoreService;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * Tests for {@link CortexRestoreAutoConfiguration}, driven by {@link ApplicationContextRunner}
 * against the ontology on the test classpath.
 *
 * <p>Unlike the scheduled backup, a restore <em>runs</em> at startup — {@link
 * RestoreExecutionConfiguration.RestoreBootstrap} fires it during context refresh. So the enabled
 * cases supply a stub {@link S3Client} that reports an empty bucket, which drives the harmless
 * "nothing to restore" path with no network. It backs off the real client, which {@code
 * CortexS3AutoConfiguration} registers with {@code @ConditionalOnMissingBean}. The fail-fast cases
 * need no stub: they throw in {@link CortexRestoreAutoConfiguration}'s constructor, before any bean
 * is built.
 *
 * <p>One temp directory is shared across the class deliberately, as in the backup tests: TDB2
 * caches its store connection per location for the JVM's lifetime.
 */
class CortexRestoreAutoConfigurationTests {

  @TempDir static Path store;

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CortexAutoConfiguration.class,
                CortexS3AutoConfiguration.class,
                CortexRestoreAutoConfiguration.class))
        .withPropertyValues("cortex.assertionsLocation=" + store);
  }

  private ApplicationContextRunner enabledRunner() {
    return runner()
        .withBean(S3Client.class, CortexRestoreAutoConfigurationTests::emptyBucketClient)
        .withPropertyValues(
            "cortex.persistent=true",
            "cortex.restore.enabled=true",
            "cortex.s3.enabled=true",
            "cortex.s3.bucket=cortex-backups",
            "cortex.s3.auth=anonymous");
  }

  @Test
  void shouldRegisterNothingByDefault() {
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(RestoreService.class);
              assertThat(context).doesNotHaveBean(RestoreRunner.class);
            });
  }

  @Test
  void shouldRestoreAtStartupWhenEnabledAndConfigured() {
    // Independent of cortex.backup.enabled, which is off here: an instance may restore without
    // scheduling backups of its own.
    enabledRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RestoreService.class);
              assertThat(context).hasSingleBean(RestoreRunner.class);
              assertThat(context).hasBean("cortexRestoreBootstrap");
            });
  }

  @Test
  void shouldFailWhenRestoreIsEnabledWithoutPersistence() {
    runner()
        .withPropertyValues(
            "cortex.restore.enabled=true", "cortex.s3.enabled=true", "cortex.s3.bucket=b")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .hasStackTraceContaining("cortex.restore.enabled")
                  .hasStackTraceContaining("cortex.persistent");
            });
  }

  @Test
  void shouldFailWhenRestoreIsEnabledWithoutS3() {
    runner()
        .withPropertyValues("cortex.persistent=true", "cortex.restore.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context).getFailure().hasStackTraceContaining("cortex.s3.enabled");
            });
  }

  @Test
  void shouldFailWhenRestoreIsEnabledWithoutABucket() {
    runner()
        .withPropertyValues(
            "cortex.persistent=true", "cortex.restore.enabled=true", "cortex.s3.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context).getFailure().hasStackTraceContaining("cortex.s3.bucket");
            });
  }

  @Test
  void shouldFailWhenStaticAuthHasNoCredentials() {
    enabledRunner()
        .withPropertyValues("cortex.s3.auth=static")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context).getFailure().hasStackTraceContaining("cortex.s3.access-key-id");
            });
  }

  private static S3Client emptyBucketClient() {
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
    return s3Client;
  }
}
