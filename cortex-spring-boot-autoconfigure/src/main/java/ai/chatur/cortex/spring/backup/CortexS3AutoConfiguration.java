package ai.chatur.cortex.spring.backup;

import ai.chatur.cortex.spring.CortexProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Registers the S3 client, switched by {@code cortex.s3.enabled}.
 *
 * <p>Separate from {@link CortexBackupAutoConfiguration} so the client can be switched on and off
 * on its own: a consumer who does not want S3 leaves it off and pays for nothing, and the AWS SDK
 * stays a {@code compileOnly} dependency that the starter never forces on anyone.
 *
 * <p>Backups are the only thing in Cortex that uses this client today, and {@link
 * CortexBackupAutoConfiguration} refuses to start without it — but the switch is genuinely
 * independent, so an application may enable the client for its own purposes with backups off.
 */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "cortex.s3", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CortexProperties.class)
public class CortexS3AutoConfiguration {

  /**
   * Creates the S3 client backups are uploaded through.
   *
   * <p>Building the client opens no connection and resolves no credentials, so a bad key is not
   * discovered here — only at the first upload.
   *
   * <p>The HTTP client is built explicitly rather than left to the SDK's classpath discovery,
   * because {@code software.amazon.awssdk:s3} ships only the asynchronous Netty client at runtime
   * and declares the synchronous ones at test scope. Left to itself the synchronous {@link
   * S3Client} fails at {@code build()} with "Unable to load an HTTP implementation from any
   * provider in the chain"; naming {@link ApacheHttpClient} makes the requirement explicit, and it
   * is what {@code cortex.s3.proxy} hangs off.
   *
   * @param properties the Cortex configuration properties
   * @return the configured S3 client
   */
  @Bean
  @ConditionalOnMissingBean
  S3Client cortexS3Client(CortexProperties properties) {
    CortexProperties.S3 s3 = properties.s3();
    ApacheHttpClient.Builder httpClient = ApacheHttpClient.builder();
    if (s3.proxy().endpoint() != null) {
      httpClient.proxyConfiguration(proxyConfiguration(s3.proxy()));
    }
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(s3.region()))
            .credentialsProvider(credentialsProvider(s3))
            .forcePathStyle(s3.pathStyleAccess())
            .httpClientBuilder(httpClient);
    if (s3.endpoint() != null) {
      builder.endpointOverride(URI.create(s3.endpoint()));
    }
    return builder.build();
  }

  private static AwsCredentialsProvider credentialsProvider(CortexProperties.S3 s3) {
    return switch (s3.auth()) {
      case DEFAULT -> DefaultCredentialsProvider.create();
      case ANONYMOUS -> AnonymousCredentialsProvider.create();
      case STATIC ->
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey()));
    };
  }

  private static ProxyConfiguration proxyConfiguration(CortexProperties.S3.Proxy proxy) {
    ProxyConfiguration.Builder builder =
        ProxyConfiguration.builder()
            .endpoint(URI.create(proxy.endpoint()))
            .username(proxy.username())
            .password(proxy.password());
    if (proxy.nonProxyHosts() != null && !proxy.nonProxyHosts().isEmpty()) {
      builder.nonProxyHosts(proxy.nonProxyHosts());
    }
    return builder.build();
  }
}
