package ai.chatur.cortex.spring.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Demo application wiring the Cortex starter into a runnable Spring Boot app. */
@SpringBootApplication
public class CortexSpringBootStarterExampleApplication {

  /** Creates the application. Spring instantiates this; consumers do not. */
  public CortexSpringBootStarterExampleApplication() {}

  /**
   * Runs the demo application.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(CortexSpringBootStarterExampleApplication.class, args);
  }
}
