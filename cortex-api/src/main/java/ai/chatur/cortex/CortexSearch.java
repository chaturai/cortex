package ai.chatur.cortex;

import java.util.List;

/** Finds resources in the knowledge graph by fuzzy full-text search over their labels. */
public interface CortexSearch {

  /**
   * Finds resources by fuzzy full-text search over their labels.
   *
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matches with their relevance scores, formatted as text and ranked best first
   */
  String search(String text);

  /**
   * Searches the knowledge graph by free text and returns the matching subjects.
   *
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matching subjects ranked best first, empty if nothing matches
   */
  List<SearchResult> searchSubjects(String text);
}
