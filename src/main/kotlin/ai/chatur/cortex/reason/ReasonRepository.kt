package ai.chatur.cortex.reason

import org.apache.jena.query.Dataset
import org.springframework.stereotype.Repository

@Repository class ReasonRepository(private val dataset: Dataset)
