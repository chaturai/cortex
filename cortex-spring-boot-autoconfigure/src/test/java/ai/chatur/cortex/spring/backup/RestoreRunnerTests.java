package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.chatur.cortex.core.store.RestoreService;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Plain JUnit tests for {@link RestoreRunner}, against a mocked {@link S3Client} and {@link
 * RestoreService} rather than a real bucket or store — no Spring context, no network, no Docker.
 */
class RestoreRunnerTests {

  @Test
  void runShouldDownloadTheMostRecentBackupAndRestoreIt() {
    RestoreService restoreService = mock(RestoreService.class);
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(
                    object("cortex/backup_2026-07-16_020000.nq.gz", "2026-07-16T02:00:00Z"),
                    object("cortex/backup_2026-07-18_020000.nq.gz", "2026-07-18T02:00:00Z"),
                    object("cortex/backup_2026-07-17_020000.nq.gz", "2026-07-17T02:00:00Z"))
                .isTruncated(false)
                .build());

    new RestoreRunner(restoreService, s3Client, "cortex-backups", "cortex/").run();

    ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
    ArgumentCaptor<Path> destination = ArgumentCaptor.forClass(Path.class);
    verify(s3Client).getObject(request.capture(), destination.capture());
    assertThat(request.getValue().bucket()).isEqualTo("cortex-backups");
    assertThat(request.getValue().key())
        .as("the most recent object under the prefix is the one restored")
        .isEqualTo("cortex/backup_2026-07-18_020000.nq.gz");
    assertThat(destination.getValue().getFileName()).hasToString("backup_2026-07-18_020000.nq.gz");
    verify(restoreService).restore(destination.getValue());
    assertThat(destination.getValue().getParent())
        .as("the temporary download directory is cleaned up after the restore")
        .doesNotExist();
  }

  @Test
  void runShouldFollowContinuationTokensAcrossPages() {
    RestoreService restoreService = mock(RestoreService.class);
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(object("cortex/backup_2026-07-16_020000.nq.gz", "2026-07-16T02:00:00Z"))
                .isTruncated(true)
                .nextContinuationToken("page-2")
                .build())
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(object("cortex/backup_2026-07-18_020000.nq.gz", "2026-07-18T02:00:00Z"))
                .isTruncated(false)
                .build());

    new RestoreRunner(restoreService, s3Client, "cortex-backups", "cortex/").run();

    ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObject(request.capture(), any(Path.class));
    assertThat(request.getValue().key())
        .as("the latest is chosen across every page, not just the first")
        .isEqualTo("cortex/backup_2026-07-18_020000.nq.gz");
  }

  @Test
  void runShouldDoNothingWhenNoBackupExists() {
    RestoreService restoreService = mock(RestoreService.class);
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

    new RestoreRunner(restoreService, s3Client, "cortex-backups", "cortex/").run();

    verify(s3Client, never()).getObject(any(GetObjectRequest.class), any(Path.class));
    verify(restoreService, never()).restore(any());
  }

  @Test
  void runShouldPropagateListingFailures() {
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenThrow(new IllegalStateException("no such bucket"));

    RestoreRunner runner =
        new RestoreRunner(mock(RestoreService.class), s3Client, "cortex-backups", "cortex/");

    assertThatThrownBy(runner::run)
        .as("an instance meant to come up seeded fails loudly rather than serving an empty graph")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runShouldPropagateDownloadFailures() {
    S3Client s3Client = mock(S3Client.class);
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(object("cortex/backup_2026-07-18_020000.nq.gz", "2026-07-18T02:00:00Z"))
                .isTruncated(false)
                .build());
    when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
        .thenThrow(new IllegalStateException("access denied"));

    RestoreRunner runner =
        new RestoreRunner(mock(RestoreService.class), s3Client, "cortex-backups", "cortex/");

    assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);
  }

  private static S3Object object(String key, String lastModified) {
    return S3Object.builder().key(key).lastModified(Instant.parse(lastModified)).build();
  }
}
