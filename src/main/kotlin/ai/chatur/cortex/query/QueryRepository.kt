package ai.chatur.cortex.query

import ai.chatur.cortex.core.CortexGraphs.INSTANCES
import java.util.concurrent.TimeUnit
import org.apache.jena.query.Dataset
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution

class QueryRepository(private val dataset: Dataset, private val timeoutMs: Long) {

  fun ask(query: Query): Boolean = execution(query) { execAsk() }

  private fun <T> execution(query: Query, block: QueryExecution.() -> T): T =
      dataset.calculateRead {
        val model = dataset.getNamedModel(INSTANCES.uri)
        val queryExecution =
            QueryExecution.model(model)
                .query(query)
                .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        queryExecution.block()
      }
}
