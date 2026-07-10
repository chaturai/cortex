package ai.chatur.cortex.query

import java.util.concurrent.TimeUnit
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.rdf.model.Model

class QueryRepository(private val model: Model, private val timeoutMs: Long) {

  fun ask(query: Query): Boolean = execution(query) { execAsk() }

  private fun <T> execution(query: Query, block: QueryExecution.() -> T): T =
      QueryExecution.model(model)
          .query(query)
          .timeout(timeoutMs, TimeUnit.MILLISECONDS)
          .build()
          .block()
}
