package ai.chatur.cortex.query

import org.apache.jena.query.Dataset
import org.springframework.stereotype.Repository

@Repository class QueryRepository(private val dataset: Dataset)
