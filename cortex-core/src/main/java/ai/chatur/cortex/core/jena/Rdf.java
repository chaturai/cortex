package ai.chatur.cortex.core.jena;

import java.io.StringWriter;
import java.util.function.Supplier;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;

/**
 * Serializes a {@link Model} or {@link Dataset} to a string.
 *
 * <p>Every method writes to an in-memory {@link StringWriter}, whose {@code close()} is a no-op —
 * unlike an actual I/O writer, serializing here can never throw a checked exception, so none of
 * these methods declare one.
 */
public final class Rdf {

  private Rdf() {}

  /**
   * Serializes the model.
   *
   * @param model the model to serialize
   * @param lang the syntax to serialize in
   * @return the model serialized in the given syntax
   */
  public static String write(Model model, Lang lang) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, lang);
    return writer.toString();
  }

  /**
   * Serializes the dataset.
   *
   * @param dataset the dataset to serialize
   * @param lang the syntax to serialize in
   * @return the dataset serialized in the given syntax
   */
  public static String write(Dataset dataset, Lang lang) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, dataset, lang);
    return writer.toString();
  }

  /**
   * Serializes the model, read in a read transaction on the dataset.
   *
   * <p>Use this when the model to serialize is drawn from a transactional dataset — for example a
   * named graph within it — so the read and the serialization happen under the same transaction.
   *
   * @param dataset the dataset the model is read from
   * @param model supplies the model to serialize, called inside the read transaction
   * @param lang the syntax to serialize in
   * @return the model serialized in the given syntax
   */
  public static String writeReading(Dataset dataset, Supplier<Model> model, Lang lang) {
    return Txn.calculateRead(dataset, () -> write(model.get(), lang));
  }

  /**
   * Serializes the whole dataset, read in a read transaction.
   *
   * @param dataset the dataset to serialize
   * @param lang the syntax to serialize in
   * @return the dataset serialized in the given syntax
   */
  public static String writeReading(Dataset dataset, Lang lang) {
    return Txn.calculateRead(dataset, () -> write(dataset, lang));
  }
}
