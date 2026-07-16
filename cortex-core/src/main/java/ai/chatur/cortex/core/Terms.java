package ai.chatur.cortex.core;

import ai.chatur.cortex.Term;
import java.util.Comparator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shared.PrefixMapping;

/** Creates {@link Term}s from Jena nodes, resolving prefixes against a prefix mapping. */
public final class Terms {

  private Terms() {}

  /**
   * Returns the term for the given node: the longest matching namespace of the prefix mapping is
   * abbreviated, literals carry their lexical form, and URIs no prefix matches carry the full URI.
   *
   * <p>The longest match wins so the term does not depend on the iteration order of the prefix
   * mapping — unlike Jena's own {@code shortForm}, which returns the first matching namespace of a
   * {@code HashMap} and so can answer differently from run to run for a URI matched by more than
   * one declared prefix.
   *
   * @param node the node to describe
   * @param prefixes the prefix mapping to abbreviate the URI against
   * @return the term for the node
   */
  public static Term of(RDFNode node, PrefixMapping prefixes) {
    if (node.isLiteral()) return new Term(null, node.asLiteral().getLexicalForm(), null);
    if (!node.isURIResource()) return new Term(null, node.toString(), null);
    String uri = node.asResource().getURI();
    return prefixes.getNsPrefixMap().entrySet().stream()
        .filter(entry -> uri.startsWith(entry.getValue()))
        .max(Comparator.comparingInt(entry -> entry.getValue().length()))
        .map(entry -> new Term(entry.getKey(), uri.substring(entry.getValue().length()), uri))
        .orElseGet(() -> new Term(null, uri, uri));
  }
}
