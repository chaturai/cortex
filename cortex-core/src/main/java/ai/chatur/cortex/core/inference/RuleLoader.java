package ai.chatur.cortex.core.inference;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses the Jena rules used for rule-based inference. */
public final class RuleLoader {

  private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);

  private RuleLoader() {}

  /**
   * Parses the given rule documents, in order.
   *
   * @param rules the rule documents, in Jena rules syntax
   * @return the parsed rules, concatenated in order
   */
  public static List<Rule> load(List<String> rules) {
    List<Rule> parsed = new ArrayList<>();
    for (String rule : rules) {
      parsed.addAll(Rule.parseRules(rule));
    }
    log.info("Loaded {} inference rules from {} document(s)", parsed.size(), rules.size());
    return parsed;
  }
}
