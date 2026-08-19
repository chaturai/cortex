package ai.chatur.cortex;

import java.util.List;

/**
 * A proposed edit to an already-approved resource, staged for review by {@link
 * CortexIngestor#revise}.
 *
 * <p>The changes address the resource by its current IRI; the rename, if any, is applied afterwards
 * and rewrites every approved statement in which the IRI appears, as subject or as object.
 *
 * @param subject the full IRI of the resource being edited
 * @param newSubject the IRI to rename the resource to, or {@code null} to leave it alone
 * @param changes the changes to the resource's statements, empty for a rename on its own
 */
public record ResourceEdit(String subject, String newSubject, List<BranchChange> changes) {}
