plugins {
  id("cortex-library-conventions")
}

description = "Spring Boot starter for Cortex"

dependencies {
  api(project(":cortex-spring-boot-autoconfigure"))

  // cortex-spring-boot-autoconfigure ships Thymeleaf templates and @Controller beans gated only by
  // @ConditionalOnClass(Controller.class) (i.e. present whenever Spring MVC is on the classpath),
  // but declares Spring MVC and Thymeleaf as testImplementation only, so neither ever reaches a
  // consumer's runtime classpath. Bringing them in here as `api` is what makes "add the starter"
  // alone actually serve the UI, per README.md's claim.
  api(platform(libs.spring.boot.dependencies))
  api("org.springframework.boot:spring-boot-starter-webmvc")
  api("org.springframework.boot:spring-boot-starter-thymeleaf")
}
