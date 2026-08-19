package ai.chatur.cortex.core.branch;

import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchStatement;
import ai.chatur.cortex.BranchSubject;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.PROV;
import ai.chatur.cortex.core.jena.Rdf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.system.Txn;

/**
 * Reads the assertions staged on a branch pending review.
 *
 * <p>Every reader is routed through {@link BranchRepository#onBranch}, the same guard the write
 * operations in {@link BranchEditService} and {@link BranchMergeService} use, so the {@link
 * CortexNamespace#PROVENANCE provenance graph} — a named graph of the same dataset, but never a
 * branch — is never rendered as one: a branch named {@code provenance} is treated exactly like an
 * unknown branch. Each method's {@code missing} value is chosen to match what it already returns
 * for a genuinely unknown branch, so "provenance" and "unknown" are indistinguishable to callers:
 * {@link #getBranch} returns the shared prefix header with no data, {@link #getBranchInfo} returns
 * the requested name with a {@code null} start time and both counts {@code 0}, and {@link
 * #getBranchSubjects} returns an empty list.
 */
public class BranchQueryService {

  /**
   * A branch name guaranteed never to match a real branch (named {@code cortex://branch-<uuid>}) or
   * the provenance graph (named {@code cortex://provenance}), used to obtain the empty-model,
   * prefixes-only Turtle serialization {@link #getBranch} returns for a branch that does not exist.
   */
  private static final Resource MISSING = CortexNamespace.getResource("__no-such-branch__");

  private final Dataset assertions;
  private final OntModel ontModel;
  private final BranchRepository branchRepository;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param ontModel the ontology model, used to abbreviate terms for display
   * @param branchRepository guards every read against an unknown branch or the provenance graph
   */
  public BranchQueryService(
      Dataset assertions, OntModel ontModel, BranchRepository branchRepository) {
    this.assertions = assertions;
    this.ontModel = ontModel;
    this.branchRepository = branchRepository;
  }

  /**
   * Returns the assertions staged on the given branch.
   *
   * @param branch the branch name
   * @return the staged assertions serialized in Turtle syntax, or just the shared prefix header
   *     with no data if the branch does not exist or is the reserved provenance graph name
   */
  public String getBranch(String branch) {
    return branchRepository.onBranch(
        branch,
        "read",
        namedModel ->
            Rdf.writeReading(assertions, () -> assertions.getNamedModel(namedModel), Lang.TTL),
        Rdf.writeReading(assertions, () -> assertions.getNamedModel(MISSING), Lang.TTL));
  }

  /**
   * Summarizes a branch pending review from its staged provenance activity.
   *
   * @param branch the branch name
   * @return the branch summary, counting separately what the branch stages for addition and what it
   *     stages for removal, or {@code (branch, null, 0, 0)} if the branch does not exist or is a
   *     reserved graph name
   */
  public BranchInfo getBranchInfo(String branch) {
    return branchRepository.onBranch(
        branch,
        "summarize",
        namedModel ->
            Txn.calculateRead(
                assertions,
                () -> {
                  Model model = assertions.getNamedModel(namedModel);
                  Statement started = model.getProperty(namedModel, PROV.startedAtTime);
                  long activitySize =
                      Iter.count(model.listStatements(namedModel, null, (RDFNode) null));
                  Model retractions =
                      assertions.getNamedModel(CortexNamespace.getRetractions(namedModel));
                  return new BranchInfo(
                      branch,
                      started == null ? null : started.getLiteral().getLexicalForm(),
                      model.size() - activitySize,
                      retractions.size());
                }),
        new BranchInfo(branch, null, 0, 0));
  }

  /**
   * Returns the assertions staged on the given branch grouped by subject, excluding the provenance
   * activity of the ingestion.
   *
   * @param branch the branch name
   * @return the staged subjects sorted by name, each with its statements sorted by predicate and
   *     each statement flagged with whether the branch stages it for addition or for removal, or an
   *     empty list if the branch does not exist or is a reserved graph name
   */
  public List<BranchSubject> getBranchSubjects(String branch) {
    return branchRepository.onBranch(
        branch,
        "read subjects on",
        namedModel ->
            Txn.calculateRead(
                assertions,
                () -> {
                  Map<Resource, List<BranchStatement>> subjects =
                      new TreeMap<>(Comparator.comparing(Resource::toString));
                  collect(subjects, assertions.getNamedModel(namedModel), namedModel, false);
                  collect(
                      subjects,
                      assertions.getNamedModel(CortexNamespace.getRetractions(namedModel)),
                      namedModel,
                      true);
                  return subjects.entrySet().stream()
                      .map(
                          entry ->
                              new BranchSubject(
                                  entry.getKey().isURIResource()
                                      ? entry.getKey().getLocalName()
                                      : entry.getKey().toString(),
                                  entry.getKey().toString(),
                                  entry.getValue().stream()
                                      .sorted(
                                          Comparator.comparing(BranchStatement::predicate)
                                              .thenComparing(BranchStatement::object))
                                      .toList()))
                      .toList();
                }),
        List.of());
  }

  /**
   * Groups the statements of one staged graph under their subjects, skipping the branch's own
   * provenance activity.
   *
   * @param subjects receives the statements, keyed by subject
   * @param model the staged graph to read
   * @param activity the branch's own provenance activity resource, whose statements are skipped
   * @param retracted whether {@code model} stages its statements for removal rather than addition
   */
  void collect(
      Map<Resource, List<BranchStatement>> subjects,
      Model model,
      Resource activity,
      boolean retracted) {
    model
        .listStatements()
        .forEach(
            statement -> {
              Resource subject = statement.getSubject();
              if (activity.equals(subject)) return;
              subjects
                  .computeIfAbsent(subject, key -> new ArrayList<>())
                  .add(getBranchStatement(statement, retracted));
            });
  }

  /**
   * Converts a staged statement into its display form, abbreviating the predicate against the
   * ontology.
   *
   * @param statement the staged statement
   * @param retracted whether the branch stages the statement for removal rather than addition
   * @return the statement's display form
   */
  BranchStatement getBranchStatement(Statement statement, boolean retracted) {
    RDFNode object = statement.getObject();
    boolean literal = object.isLiteral();
    String language = literal ? object.asLiteral().getLanguage() : null;
    return new BranchStatement(
        ontModel.shortForm(statement.getPredicate().getURI()),
        statement.getPredicate().getURI(),
        literal ? object.asLiteral().getLexicalForm() : object.toString(),
        literal,
        literal ? object.asLiteral().getDatatypeURI() : null,
        language == null || language.isEmpty() ? null : language,
        retracted);
  }
}
