package ai.chatur.cortex.ingest

import org.apache.jena.query.Dataset
import org.springframework.stereotype.Repository

@Repository class IngestRepository(private val dataset: Dataset)
