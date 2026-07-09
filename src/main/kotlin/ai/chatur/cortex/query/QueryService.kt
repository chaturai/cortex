package ai.chatur.cortex.query

import ai.chatur.cortex.ingest.IngestRepository
import org.springframework.stereotype.Service

@Service class QueryService(private val repository: IngestRepository)
