package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.BackupService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Takes a backup of the assertions store and uploads it to S3.
 *
 * <p>All of the backup feature's behavior lives here, in a plain object with no Spring or Quartz
 * dependency, so it can be exercised directly; {@link BackupJob} is a thin scheduler adapter over
 * it, and {@link CortexBackupAutoConfiguration} is the only thing that knows how it is scheduled.
 *
 * <p>The local backup file is deliberately left in place after a successful upload: the copy in S3
 * is a copy, not a move, so a bucket misconfiguration can never be silently destructive. Backups
 * therefore accumulate beside the store, and pruning them is left to whatever manages that volume.
 *
 * <p>Failures propagate rather than being logged and swallowed — a backup that silently stopped
 * working would be worse than one that fails loudly, and {@link BackupJob} is where the decision to
 * report rather than retry is made.
 */
public class BackupRunner {

  private static final Logger log = LoggerFactory.getLogger(BackupRunner.class);

  private final BackupService backupService;
  private final S3Client s3Client;
  private final String bucket;
  private final String keyPrefix;

  /**
   * Creates the runner.
   *
   * @param backupService takes the TDB2 snapshot
   * @param s3Client the client the snapshot is uploaded through
   * @param bucket the bucket to upload to
   * @param keyPrefix prepended verbatim to the backup's file name to form the object key
   */
  public BackupRunner(
      BackupService backupService, S3Client s3Client, String bucket, String keyPrefix) {
    this.backupService = backupService;
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.keyPrefix = keyPrefix;
  }

  /**
   * Takes a backup and uploads it.
   *
   * <p>The object key is {@code cortex.backup.key-prefix} followed by the file's name. TDB2 already
   * makes that name unique per backup ({@code backup_<yyyy-MM-dd_HHmmss>.nq.gz}), so an upload
   * never overwrites an earlier one.
   *
   * @throws RuntimeException if the backup cannot be taken or the upload fails
   */
  public void run() {
    Path file = Path.of(backupService.backup());
    String key = keyPrefix + file.getFileName();
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/gzip").build(),
        RequestBody.fromFile(file));
    log.info("Uploaded backup {} to s3://{}/{}", file.getFileName(), bucket, key);
  }
}
