package ai.chatur.cortex.core.provenance;

import ai.chatur.cortex.core.PROV;
import java.util.Calendar;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Builds and closes the {@link PROV#Activity provenance activity} that records an ingestion, from
 * staging through approval, and clears the provenance of statements an approval retracts — the only
 * place in the codebase with knowledge of the PROV-O vocabulary used for this bookkeeping.
 */
public class ProvenanceRecorder {

  /** Creates a recorder. It is stateless: every method builds a fresh, self-contained model. */
  public ProvenanceRecorder() {}

  /**
   * Returns the assertions to stage when a branch is opened: the newly staged {@code novel}
   * statements plus a freshly started {@link PROV#Activity} recording the ingestion.
   *
   * @param novel the newly staged assertions
   * @param branch the branch's own provenance activity resource
   * @return {@code novel} together with the activity's start triples
   */
  public Model getStagedModel(Model novel, Resource branch) {
    Model staged = ModelFactory.createDefaultModel();
    staged.add(novel);
    Literal now = staged.createTypedLiteral(Calendar.getInstance());
    staged.add(branch, RDF.type, PROV.Activity);
    staged.add(branch, RDFS.label, branch.getLocalName());
    staged.add(branch, RDFS.comment, "Ingestion of the assertions staged on this branch");
    staged.add(branch, PROV.startedAtTime, now);
    return staged;
  }

  /**
   * Returns the provenance to record when a branch is approved: the activity's own closing triples
   * — {@link PROV#endedAtTime} and whatever the activity itself carried on the branch — plus a
   * {@link PROV#wasGeneratedBy} reification linking every newly approved statement back to the
   * activity.
   *
   * @param diff the branch's staged statements not already present in the default graph
   * @param data the subset of {@code diff} that is actual data, excluding the activity's own
   *     triples
   * @param activity the branch's own provenance activity resource
   * @return the provenance triples to record in the {@link
   *     ai.chatur.cortex.core.CortexNamespace#PROVENANCE provenance graph}
   */
  public Model getProvenance(Model diff, Model data, Resource activity) {
    Model provenance = ModelFactory.createDefaultModel();
    Literal now = provenance.createTypedLiteral(Calendar.getInstance());
    provenance.add(activity, PROV.endedAtTime, now);
    diff.listStatements(activity, null, (RDFNode) null).forEach(provenance::add);
    data.listStatements()
        .forEach(
            statement -> {
              Resource reifier = provenance.createReifier(statement);
              provenance.add(reifier, PROV.wasGeneratedBy, activity);
            });
    return provenance;
  }

  /**
   * Returns the provenance to remove when statements are retracted from the knowledge graph: every
   * triple of the reifier of each retracted statement.
   *
   * <p>Leaving them behind would not just accumulate orphans. {@link
   * ai.chatur.cortex.core.query.QueryService#describe describe} reports a statement's creation time
   * as the {@code MIN} of its activities' end times, so a statement that is retracted and later
   * asserted again would report the original creation time and hide that it ever went away.
   *
   * @param retracted the statements being removed from the knowledge graph
   * @param provenance the provenance graph to look the reifiers up in
   * @return the triples to remove from the provenance graph
   */
  public Model getStaleProvenance(Model retracted, Model provenance) {
    Model stale = ModelFactory.createDefaultModel();
    retracted
        .listStatements()
        .forEach(
            statement ->
                provenance
                    .listResourcesWithProperty(
                        RDF.reifies, ResourceFactory.createStatementTerm(statement))
                    .forEach(
                        reifier ->
                            provenance
                                .listStatements(reifier, null, (RDFNode) null)
                                .forEach(stale::add)));
    return stale;
  }
}
