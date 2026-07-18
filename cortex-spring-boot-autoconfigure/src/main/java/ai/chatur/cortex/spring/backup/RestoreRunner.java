package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.core.store.RestoreService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Downloads the most recent backup under a key prefix and loads it into the assertions store.
 *
 * <p>The download counterpart to {@link BackupRunner}: that snapshots the store and uploads it,
 * this finds the latest upload and restores it. Like {@link BackupRunner} it is a plain object with
 * no Spring or scheduler dependency, so all of its behaviour can be exercised directly; {@link
 * RestoreExecutionConfiguration}'s bootstrap is the thin lifecycle adapter that runs it once, at
 * startup.
 *
 * <p>The store is treated as replaceable and S3 as the source of truth, so a restore is a
 * wipe-and-load (see {@link RestoreService}), and it runs on every boot. The one case it does
 * <em>not</em> touch the store is when the bucket holds no backup yet: a genuine first-ever
 * deployment has nothing to restore, so it logs and leaves the (empty) store alone rather than
 * failing. Every other failure — a missing bucket, bad credentials, a corrupt backup — propagates,
 * so an instance that was meant to come up seeded fails loudly instead of serving an empty graph.
 */
public class RestoreRunner {

  private static final Logger log = LoggerFactory.getLogger(RestoreRunner.class);

  private final RestoreService restoreService;
  private final S3Client s3Client;
  private final String bucket;
  private final String keyPrefix;

  /**
   * Creates the runner.
   *
   * @param restoreService loads the downloaded backup into the store
   * @param s3Client the client the backup is listed and downloaded through
   * @param bucket the bucket backups are read from
   * @param keyPrefix the prefix backups were uploaded under; only objects beneath it are considered
   */
  public RestoreRunner(
      RestoreService restoreService, S3Client s3Client, String bucket, String keyPrefix) {
    this.restoreService = restoreService;
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.keyPrefix = keyPrefix;
  }

  /**
   * Finds the latest backup under the key prefix, downloads it, and restores it.
   *
   * <p>"Latest" is by {@linkplain S3Object#lastModified() last-modified time}, breaking ties on the
   * key — which for the {@code backup_<yyyy-MM-dd_HHmmss>.nq.gz} names a backup carries is itself
   * chronological. When the prefix holds no objects the method returns without touching the store.
   *
   * @throws RuntimeException if listing, downloading, or loading the backup fails
   */
  public void run() {
    Optional<S3Object> latest = latestBackup();
    if (latest.isEmpty()) {
      log.info(
          "No backup found under s3://{}/{} — starting with an empty assertions store",
          bucket,
          keyPrefix);
      return;
    }
    String key = latest.get().key();
    Path dir = createTempDir();
    try {
      Path file = dir.resolve(fileName(key));
      s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), file);
      log.info("Downloaded backup s3://{}/{}, restoring assertions store", bucket, key);
      restoreService.restore(file);
    } finally {
      deleteRecursively(dir);
    }
  }

  private Optional<S3Object> latestBackup() {
    S3Object latest = null;
    Comparator<S3Object> byRecency =
        Comparator.comparing(S3Object::lastModified).thenComparing(S3Object::key);
    String continuationToken = null;
    do {
      ListObjectsV2Request.Builder request =
          ListObjectsV2Request.builder().bucket(bucket).prefix(keyPrefix);
      if (continuationToken != null) {
        request.continuationToken(continuationToken);
      }
      ListObjectsV2Response response = s3Client.listObjectsV2(request.build());
      for (S3Object object : response.contents()) {
        if (latest == null || byRecency.compare(object, latest) > 0) {
          latest = object;
        }
      }
      continuationToken =
          Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
    } while (continuationToken != null);
    return Optional.ofNullable(latest);
  }

  private static String fileName(String key) {
    return key.substring(key.lastIndexOf('/') + 1);
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("cortex-restore");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create a temporary directory for the restore", e);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (var paths = Files.walk(dir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  log.warn("Could not delete restore temp file {}", path, e);
                }
              });
    } catch (IOException e) {
      log.warn("Could not clean up restore temp directory {}", dir, e);
    }
  }
}
