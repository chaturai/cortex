package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.chatur.cortex.core.store.BackupService;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Tests for {@link CortexBackupAutoConfiguration}, driven by {@link ApplicationContextRunner}
 * against the ontology on the test classpath.
 *
 * <p>No network and no bucket: building an {@link S3Client} resolves no credentials and opens no
 * connection, so every S3 case here is offline.
 *
 * <p>{@code cortex.assertionsLocation} is pointed at a temp directory in every case that enables
 * backups, since those need {@code cortex.persistent=true} and the default location would write a
 * real TDB2 store into the repository root. One directory is shared across the class deliberately:
 * TDB2 caches its store connection per location for the lifetime of the JVM, so a directory per
 * test would leave a pile of open stores behind.
 */
class CortexBackupAutoConfigurationTests {

  @TempDir static Path store;

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CortexAutoConfiguration.class,
                CortexS3AutoConfiguration.class,
                CortexBackupAutoConfiguration.class))
        .withPropertyValues("cortex.assertionsLocation=" + store);
  }

  private ApplicationContextRunner enabledRunner() {
    return runner()
        .withPropertyValues(
            "cortex.persistent=true",
            "cortex.backup.enabled=true",
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
              assertThat(context).doesNotHaveBean(BackupService.class);
              assertThat(context).doesNotHaveBean(BackupRunner.class);
              assertThat(context).doesNotHaveBean(S3Client.class);
              assertThat(context).doesNotHaveBean(Trigger.class);
            });
  }

  @Test
  void shouldRegisterTheS3ClientAloneWhenOnlyS3IsEnabled() {
    // cortex.s3.enabled is a switch in its own right, not an acknowledgement that must always be
    // true alongside cortex.backup.enabled: it registers the client and nothing else.
    runner()
        .withPropertyValues("cortex.s3.enabled=true", "cortex.s3.bucket=cortex-backups")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(S3Client.class);
              assertThat(context).doesNotHaveBean(BackupService.class);
              assertThat(context).doesNotHaveBean(BackupRunner.class);
            });
  }

  @Test
  void shouldRegisterTheBackupJobWhenEnabledAndConfigured() {
    enabledRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(BackupService.class);
              assertThat(context).hasSingleBean(BackupRunner.class);
              assertThat(context).hasSingleBean(S3Client.class);
              assertThat(context).hasBean("cortexBackupJobDetail");
              assertThat(context).hasBean("cortexBackupTrigger");
              assertThat(context).hasBean("cortexBackupShutdownLifecycle");
            });
  }

  @Test
  void shouldTakeAFinalBackupOnShutdownByDefault() {
    // Backups are scheduled at cortex.backup.interval, 24h by default, so without this a deliberate
    // restart discards up to a day of approvals. Enabling backups is enough to opt in.
    enabledRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(BackupShutdownLifecycle.class);
            });
  }

  @Test
  void shouldNotTakeAFinalBackupOnShutdownWhenDisabled() {
    // The one part of the feature that makes shutdown block on the network, so it is switchable
    // without giving up scheduled backups.
    enabledRunner()
        .withPropertyValues("cortex.backup.on-shutdown=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(BackupShutdownLifecycle.class);
              assertThat(context)
                  .as("disabling the shutdown backup leaves the scheduled one alone")
                  .hasBean("cortexBackupTrigger");
            });
  }

  @Test
  void shouldActuallyBackUpWhenTheContextCloses() {
    // The one case here that closes a real context rather than inspecting bean definitions: it
    // proves Spring invokes the lifecycle on shutdown at all, which no amount of wiring assertions
    // can. The snapshot is a real TDB2 backup of the temp store; only the upload is mocked.
    S3Client s3Client = mock(S3Client.class);

    enabledRunner()
        .withBean("cortexS3Client", S3Client.class, () -> s3Client)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              verify(s3Client, never())
                  .putObject(any(PutObjectRequest.class), any(RequestBody.class));
            });

    // ApplicationContextRunner closes the context once the callback returns.
    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client, description("closing the context must upload a final backup"))
        .putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("cortex-backups");
    assertThat(request.getValue().key())
        .as("the shutdown backup is an ordinary backup under the same prefix, so restore finds it")
        .startsWith("cortex/backup_");
  }

  @Test
  void shouldNotBackUpOnCloseWhenTheShutdownBackupIsDisabled() {
    S3Client s3Client = mock(S3Client.class);

    enabledRunner()
        .withPropertyValues("cortex.backup.on-shutdown=false")
        .withBean("cortexS3Client", S3Client.class, () -> s3Client)
        .run(context -> assertThat(context).hasNotFailed());

    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void shouldStopTheShutdownLifecycleAfterTheWebServerAndScheduler() {
    // Lifecycle phases stop in descending order. At or above Boot's web server phase the final
    // snapshot would race approvals still arriving over HTTP -- the writes it exists to preserve.
    enabledRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(BackupShutdownLifecycle.class).getPhase())
                  .isLessThan(Integer.MAX_VALUE - 2048);
            });
  }

  @Test
  void shouldFailWhenBackupIsEnabledWithoutPersistence() {
    // The regression test for the constraint BackupServiceTests pins in cortex-core: TDB2 cannot
    // back up an in-memory store. Booting clean and never backing anything up is the failure this
    // exists to prevent.
    runner()
        .withPropertyValues(
            "cortex.backup.enabled=true", "cortex.s3.enabled=true", "cortex.s3.bucket=b")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .hasStackTraceContaining("cortex.backup.enabled")
                  .hasStackTraceContaining("cortex.persistent");
            });
  }

  @Test
  void shouldFailWhenBackupIsEnabledWithoutS3() {
    runner()
        .withPropertyValues("cortex.persistent=true", "cortex.backup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context).getFailure().hasStackTraceContaining("cortex.s3.enabled");
            });
  }

  @Test
  void shouldFailWhenBackupIsEnabledWithoutABucket() {
    runner()
        .withPropertyValues(
            "cortex.persistent=true", "cortex.backup.enabled=true", "cortex.s3.enabled=true")
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

  @Test
  void shouldFailWhenTheIntervalIsNotPositive() {
    enabledRunner()
        .withPropertyValues("cortex.backup.interval=0s")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context).getFailure().hasStackTraceContaining("cortex.backup.interval");
            });
  }

  @Test
  void shouldBuildTheS3ClientForEveryAuthMode() {
    for (String auth : new String[] {"default", "static", "anonymous"}) {
      enabledRunner()
          .withPropertyValues(
              "cortex.s3.auth=" + auth,
              "cortex.s3.access-key-id=key",
              "cortex.s3.secret-access-key=secret")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(S3Client.class);
              });
    }
  }

  @Test
  void shouldBuildTheS3ClientWithAnEndpointOverrideProxyAndPathStyleAccess() {
    enabledRunner()
        .withPropertyValues(
            "cortex.s3.endpoint=http://localhost:9000",
            "cortex.s3.path-style-access=true",
            "cortex.s3.proxy.endpoint=http://proxy.internal:3128",
            "cortex.s3.proxy.username=user",
            "cortex.s3.proxy.password=pass",
            "cortex.s3.proxy.non-proxy-hosts=localhost,127.0.0.1")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(S3Client.class);
            });
  }

  @Test
  void shouldScheduleTheTriggerAtTheConfiguredIntervalStartingOneIntervalOut() {
    enabledRunner()
        .withPropertyValues("cortex.backup.interval=30m")
        .run(
            context -> {
              Trigger trigger = context.getBean("cortexBackupTrigger", Trigger.class);
              JobDetail jobDetail = context.getBean("cortexBackupJobDetail", JobDetail.class);
              assertThat(trigger.getJobKey()).isEqualTo(jobDetail.getKey());
              assertThat(trigger.getStartTime())
                  .as("the first backup is one interval out, so restarts do not each leave one")
                  .isAfter(new java.util.Date());
            });
  }

  @Test
  void proxyGroupShouldNotBeNullWhenUnset() {
    // The nested-record convention CortexProperties relies on: a bare @DefaultValue keeps the group
    // non-null so "is a proxy configured?" is cleanly proxy.endpoint() != null. Web and Mcp each
    // have a primitive component to force instantiation; Proxy is all-String, so pin it here rather
    // than assume the cases are identical.
    enabledRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              ai.chatur.cortex.spring.CortexProperties properties =
                  context.getBean(ai.chatur.cortex.spring.CortexProperties.class);
              assertThat(properties.s3().proxy()).isNotNull();
              assertThat(properties.s3().proxy().endpoint()).isNull();
            });
  }
}
