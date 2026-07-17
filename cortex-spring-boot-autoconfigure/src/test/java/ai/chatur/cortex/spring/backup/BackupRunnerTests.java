package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.chatur.cortex.core.store.BackupService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Plain JUnit tests for {@link BackupRunner}, against a mocked {@link S3Client} and {@link
 * BackupService} rather than a real bucket or store — no Spring context, no network, no Docker.
 */
class BackupRunnerTests {

  @Test
  void runShouldUploadTheBackupUnderTheConfiguredBucketAndKeyPrefix(@TempDir Path dir)
      throws IOException {
    Path backup = Files.writeString(dir.resolve("backup_2026-07-17_020000.nq.gz"), "gzip-bytes");
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenReturn(backup.toString());
    S3Client s3Client = mock(S3Client.class);

    new BackupRunner(backupService, s3Client, "cortex-backups", "cortex/").run();

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("cortex-backups");
    assertThat(request.getValue().key())
        .as("the key is the prefix followed by the name TDB2 gave the backup")
        .isEqualTo("cortex/backup_2026-07-17_020000.nq.gz");
  }

  @Test
  void runShouldPrependTheKeyPrefixVerbatim(@TempDir Path dir) throws IOException {
    Path backup = Files.writeString(dir.resolve("backup_2026-07-17_020000.nq.gz"), "gzip-bytes");
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenReturn(backup.toString());
    S3Client s3Client = mock(S3Client.class);

    new BackupRunner(backupService, s3Client, "cortex-backups", "nightly-").run();

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().key())
        .as(
            "the prefix is prepended verbatim rather than joined with a separator, so it can name"
                + " an object as well as a folder")
        .isEqualTo("nightly-backup_2026-07-17_020000.nq.gz");
  }

  @Test
  void runShouldKeepTheLocalBackupAfterUploading(@TempDir Path dir) throws IOException {
    Path backup = Files.writeString(dir.resolve("backup_2026-07-17_020000.nq.gz"), "gzip-bytes");
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenReturn(backup.toString());

    new BackupRunner(backupService, mock(S3Client.class), "cortex-backups", "cortex/").run();

    assertThat(backup)
        .as("the upload is a copy, not a move: a bucket misconfiguration must never destroy data")
        .exists();
  }

  @Test
  void runShouldPropagateBackupFailures() {
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenThrow(new IllegalStateException("no container path"));
    S3Client s3Client = mock(S3Client.class);

    BackupRunner runner = new BackupRunner(backupService, s3Client, "cortex-backups", "cortex/");

    assertThatThrownBy(runner::run)
        .as("a backup that silently stopped working is worse than one that fails loudly")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runShouldPropagateUploadFailures(@TempDir Path dir) throws IOException {
    Path backup = Files.writeString(dir.resolve("backup_2026-07-17_020000.nq.gz"), "gzip-bytes");
    BackupService backupService = mock(BackupService.class);
    when(backupService.backup()).thenReturn(backup.toString());
    S3Client s3Client = mock(S3Client.class);
    doThrow(new IllegalStateException("no such bucket"))
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    BackupRunner runner = new BackupRunner(backupService, s3Client, "cortex-backups", "cortex/");

    assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);
  }
}
