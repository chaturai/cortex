package ai.chatur.cortex.spring;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Cortex, bound to the {@code cortex.*} prefix.
 *
 * @param persistent whether the approved assertions are persisted to a TDB2 store on disk; when
 *     {@code false} (the default) they are held in memory. Either way, the inference closure and
 *     the full-text index built over it are always an in-memory cache, rebuilt from the assertions
 *     on every startup.
 * @param assertionsLocation the directory of the TDB2 store for assertions, used when persistent
 * @param ontologies the ontologies the knowledge graph is built on, in Turtle syntax, merged in
 *     order
 * @param shapes the SHACL shapes ingested assertions are validated against, in Turtle syntax,
 *     merged in order
 * @param rules the Jena rules used for inference, concatenated in order
 * @param web the web UI settings
 * @param mcp the MCP server settings
 * @param s3 the S3 client settings
 * @param backup the scheduled backup settings
 */
@ConfigurationProperties(prefix = "cortex")
public record CortexProperties(
    @DefaultValue("false") boolean persistent,
    @DefaultValue(".cortex/db") String assertionsLocation,
    @DefaultValue("classpath:ontology.ttl") List<Resource> ontologies,
    @DefaultValue("classpath:ontology.shapes") List<Resource> shapes,
    @DefaultValue("classpath:ontology.rules") List<Resource> rules,
    @DefaultValue Web web,
    @DefaultValue Mcp mcp,
    @DefaultValue S3 s3,
    @DefaultValue Backup backup) {

  /**
   * Settings for the web UI auto-configured by {@link
   * ai.chatur.cortex.spring.web.CortexWebAutoConfiguration}.
   *
   * @param enabled whether the web UI controllers are registered; disabling this is how a consumer
   *     gets the knowledge graph without the UI, without excluding individual controller beans
   */
  public record Web(@DefaultValue("true") boolean enabled) {}

  /**
   * Settings for the MCP server auto-configured by {@link
   * ai.chatur.cortex.spring.mcp.CortexMcpAutoConfiguration}.
   *
   * @param enabled whether the MCP tools and resources are registered; disabling this is how a
   *     consumer gets the knowledge graph without an MCP server, without excluding individual tool
   *     beans
   */
  public record Mcp(@DefaultValue("true") boolean enabled) {}

  /**
   * Settings for the S3 client auto-configured by {@link
   * ai.chatur.cortex.spring.backup.CortexBackupAutoConfiguration}, used to upload backups.
   *
   * <p>Disabled by default, and requires {@code software.amazon.awssdk:s3} and {@code
   * software.amazon.awssdk:apache-client} on the classpath — neither is brought in by the starter,
   * since consumers that do not back up to S3 should not pay for the AWS SDK.
   *
   * @param enabled whether the {@code S3Client} bean is registered
   * @param endpoint the S3 endpoint to talk to, for example {@code http://localhost:9000} for
   *     MinIO; when unset, the AWS endpoint for the configured region is used
   * @param bucket the bucket backups are uploaded to; required when backups are enabled
   * @param region the AWS region; still required by the SDK when talking to an S3-compatible
   *     service, where it is usually arbitrary
   * @param auth how the client authenticates, see {@link Auth}
   * @param accessKeyId the access key, used only when {@code auth} is {@link Auth#STATIC}
   * @param secretAccessKey the secret key, used only when {@code auth} is {@link Auth#STATIC}
   * @param pathStyleAccess whether to address buckets as a path segment ({@code
   *     endpoint/bucket/key}) rather than a subdomain; needed by MinIO and most S3-compatible
   *     services
   * @param proxy the HTTP proxy settings
   */
  public record S3(
      @DefaultValue("false") boolean enabled,
      String endpoint,
      String bucket,
      @DefaultValue("us-east-1") String region,
      @DefaultValue("default") Auth auth,
      String accessKeyId,
      String secretAccessKey,
      @DefaultValue("false") boolean pathStyleAccess,
      @DefaultValue Proxy proxy) {

    /** How the S3 client obtains credentials. */
    public enum Auth {
      /**
       * The AWS default credentials chain: environment variables, system properties, the profile
       * file, then container and instance credentials.
       */
      DEFAULT,
      /** The {@code accessKeyId} and {@code secretAccessKey} configured here. */
      STATIC,
      /** No credentials at all, for an unauthenticated S3-compatible endpoint. */
      ANONYMOUS
    }

    /**
     * HTTP proxy settings for the S3 client.
     *
     * <p>The proxy is applied when {@code endpoint} is set; there is deliberately no separate
     * enabling flag, so a configured proxy can never be silently ignored.
     *
     * @param endpoint the proxy endpoint, for example {@code http://proxy.internal:3128}; when
     *     unset, no proxy is used and every other component here is ignored
     * @param username the proxy username, when it requires authentication
     * @param password the proxy password, when it requires authentication
     * @param nonProxyHosts hosts to reach directly, bypassing the proxy; when unset, everything
     *     goes through it
     */
    public record Proxy(
        String endpoint, String username, String password, Set<String> nonProxyHosts) {}
  }

  /**
   * Settings for the scheduled backup job auto-configured by {@link
   * ai.chatur.cortex.spring.backup.CortexBackupAutoConfiguration}.
   *
   * <p>Disabled by default, and requires {@code
   * org.springframework.boot:spring-boot-starter-quartz} on the classpath as well as an enabled
   * {@link S3} client. Backups also require {@link CortexProperties#persistent()} to be {@code
   * true}: TDB2 cannot back up an in-memory store, so enabling backups without persistence fails at
   * startup rather than never backing anything up.
   *
   * @param enabled whether the backup job is scheduled
   * @param interval how often a backup is taken; the first runs one interval after startup, not at
   *     startup, so a restart loop does not upload a backup per restart
   * @param keyPrefix prepended verbatim to the backup's file name to form the S3 object key;
   *     include a trailing {@code /} to group the objects under a folder
   */
  public record Backup(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("24h") Duration interval,
      @DefaultValue("cortex/") String keyPrefix) {}
}
