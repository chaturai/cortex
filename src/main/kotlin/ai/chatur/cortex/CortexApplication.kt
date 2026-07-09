package ai.chatur.cortex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class CortexApplication

fun main(args: Array<String>) {
  runApplication<CortexApplication>(*args)
}
