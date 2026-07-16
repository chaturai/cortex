package ai.chatur.cortex.core.jena;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.system.Txn;

/**
 * Runs a {@link Query} against a {@link Dataset} inside a read transaction.
 *
 * <p>Every query runs in its own {@link QueryExecution}, opened and closed around a single read
 * transaction on the dataset: {@link #forEachSolution} for queries whose results are accumulated
 * into a value the caller already holds, {@link #execute} for anything that needs the {@link
 * QueryExecution} itself — to distinguish {@code SELECT}/{@code ASK}/{@code DESCRIBE}, for
 * instance. {@link QuerySolution}s are only valid while the execution is open, so neither method
 * lets one escape the transaction.
 */
public final class Sparql {

  private final Dataset dataset;
  private final Query query;
  private final Map<String, RDFNode> substitutions = new HashMap<>();

  private Sparql(Dataset dataset, Query query) {
    this.dataset = dataset;
    this.query = query;
  }

  /**
   * Prepares the query to run against the dataset.
   *
   * @param dataset the dataset to query
   * @param query the query to run
   * @return the prepared query, ready to bind variables and run
   */
  public static Sparql on(Dataset dataset, Query query) {
    return new Sparql(dataset, query);
  }

  /**
   * Binds a variable of the query to a value.
   *
   * @param var the name of the variable, without the leading {@code ?}
   * @param value the value to substitute
   * @return this query, for chaining
   */
  public Sparql bind(String var, RDFNode value) {
    substitutions.put(var, value);
    return this;
  }

  /**
   * Runs the query as a {@code SELECT}, applying the action to each solution inside the
   * transaction.
   *
   * @param action applied to every solution in order, while the query execution is still open
   */
  public void forEachSolution(Consumer<QuerySolution> action) {
    Txn.executeRead(
        dataset,
        () -> {
          QueryExecution queryExecution = build();
          try (queryExecution) {
            queryExecution.execSelect().forEachRemaining(action::accept);
          }
        });
  }

  /**
   * Runs the query, applying the mapper to the execution inside the transaction.
   *
   * @param mapper computes the result from the query execution, while it is still open
   * @param <T> the type of the result
   * @return the value the mapper computed
   */
  public <T> T execute(Function<QueryExecution, T> mapper) {
    return Txn.calculateRead(
        dataset,
        () -> {
          QueryExecution queryExecution = build();
          try (queryExecution) {
            return mapper.apply(queryExecution);
          }
        });
  }

  private QueryExecution build() {
    QueryExecutionBuilder builder = QueryExecution.dataset(dataset).query(query);
    substitutions.forEach(builder::substitution);
    return builder.build();
  }
}
