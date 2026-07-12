plugins {
  id("cortex-library-conventions")
}

description = "Spring Boot starter for Cortex"

dependencies { api(project(":cortex-spring-boot-autoconfigure")) }
