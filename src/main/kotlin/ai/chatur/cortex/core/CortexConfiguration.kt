package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class CortexConfiguration {

  @Bean
  fun dataset(@Value("assembler.ttl") assembler: Resource): Dataset {
    return DatasetFactory.assemble(assembler.file.canonicalPath)
  }

  @Bean
  fun resourceFactory(dataset: Dataset): CortexResourceFactory {
    return CortexResourceFactory(dataset)
  }
}
