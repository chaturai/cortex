package ai.chatur.cortex.core.lint;

import java.io.StringReader;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.Shapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the {@link Shapes} that incoming assertions are validated against. */
public final class ShapesLoader {

  private static final Logger log = LoggerFactory.getLogger(ShapesLoader.class);

  private ShapesLoader() {}

  /**
   * Builds the SHACL shapes by merging the given Turtle documents, in order.
   *
   * @param shapes the shapes documents, in Turtle syntax
   * @return the parsed, merged shapes
   */
  public static Shapes load(List<String> shapes) {
    Model model = ModelFactory.createDefaultModel();
    for (String shape : shapes) {
      RDFDataMgr.read(model, new StringReader(shape), null, Lang.TTL);
    }
    Shapes parsed = Shapes.parse(model.getGraph());
    log.info("Parsed {} shapes from {} document(s)", parsed.numRootShapes(), shapes.size());
    return parsed;
  }
}
