package ai.chatur.cortex.spring.backup;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.spring.CortexProperties;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.apache.ProxyConfiguration;

/**
 * Tests for the proxy the S3 client is built with.
 *
 * <p>These set real {@code *.proxyHost} system properties, because the behaviour worth pinning only
 * exists in their presence: the AWS SDK resolves them by default, so "no proxy configured" would
 * otherwise mean "whatever proxy the deployment environment happens to name". Each test restores
 * the properties it touched.
 */
class CortexS3ProxyTests {

  private static final Map<String, String> AMBIENT =
      Map.of(
          "http.proxyHost", "ambient.internal",
          "http.proxyPort", "3128",
          "https.proxyHost", "ambient.internal",
          "https.proxyPort", "3128");

  @BeforeEach
  void setAmbientProxy() {
    AMBIENT.forEach(System::setProperty);
  }

  @AfterEach
  void clearAmbientProxy() {
    AMBIENT.keySet().forEach(System::clearProperty);
  }

  @Test
  void shouldIgnoreAmbientProxySystemPropertiesByDefault() {
    ProxyConfiguration configuration = CortexS3AutoConfiguration.proxyConfiguration(proxy(null));

    assertThat(configuration.host())
        .as(
            "with no proxy configured, backups must not be routed through one the deployment"
                + " environment happens to name and cortex.s3.proxy says nothing about")
        .isNull();
    assertThat(configuration.port()).isZero();
  }

  @Test
  void shouldResolveAmbientProxySystemPropertiesWhenExplicitlyEnabled() {
    CortexProperties.S3.Proxy proxy =
        new CortexProperties.S3.Proxy(null, null, null, null, true, false);

    ProxyConfiguration configuration = CortexS3AutoConfiguration.proxyConfiguration(proxy);

    assertThat(configuration.host())
        .as("the SDK's system-property discovery is available to whoever asks for it")
        .isEqualTo("ambient.internal");
    assertThat(configuration.port()).isEqualTo(3128);
  }

  @Test
  void shouldUseTheConfiguredProxyOverAmbientSystemProperties() {
    ProxyConfiguration configuration =
        CortexS3AutoConfiguration.proxyConfiguration(proxy("http://configured.internal:8080"));

    assertThat(configuration.host()).isEqualTo("configured.internal");
    assertThat(configuration.port()).isEqualTo(8080);
    assertThat(configuration.username()).isEqualTo("user");
    assertThat(configuration.password()).isEqualTo("pass");
    assertThat(configuration.nonProxyHosts()).containsExactly("localhost");
  }

  private static CortexProperties.S3.Proxy proxy(String endpoint) {
    return new CortexProperties.S3.Proxy(
        endpoint, "user", "pass", Set.of("localhost"), false, false);
  }
}
