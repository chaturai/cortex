package ai.chatur.cortex.lint

import org.apache.jena.query.Dataset
import org.springframework.stereotype.Repository

@Repository class LintRepository(private val dataset: Dataset)
