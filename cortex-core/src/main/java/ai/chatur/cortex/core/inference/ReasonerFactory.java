package ai.chatur.cortex.core.inference;

import java.util.List;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;

/** Builds the {@link GenericRuleReasoner} used for rule-based inference. */
public final class ReasonerFactory {

  private ReasonerFactory() {}

  /**
   * Builds a rule reasoner over the given rules, with OWL translation and transitive-closure
   * caching enabled.
   *
   * @param rules the rules the reasoner applies
   * @return the configured reasoner, not yet bound to a schema
   */
  public static GenericRuleReasoner create(List<Rule> rules) {
    GenericRuleReasoner reasoner = new GenericRuleReasoner(rules);
    reasoner.setOWLTranslation(true);
    reasoner.setTransitiveClosureCaching(true);
    return reasoner;
  }
}
