package ai.chatur.cortex.core.provenance;

import ai.chatur.cortex.core.PROV;
import java.util.Calendar;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Builds and closes the {@link PROV#Activity provenance activity} that records an ingestion, from
 * staging through approval — the only place in the codebase with knowledge of the PROV-O vocabulary
 * used for this bookkeeping.
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
}
