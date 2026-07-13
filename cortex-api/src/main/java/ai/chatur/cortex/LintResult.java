package ai.chatur.cortex;

/**
 * The outcome of {@link Cortex#lint(String) linting} assertions against the ontology.
 *
 * @param valid whether every class and property used is defined in the ontology
 * @param ttl the validated assertions serialized in Turtle syntax, or {@code null} if linting
 *     failed
 * @param errors the lint violations, one per line, or {@code null} if linting passed
 */
public record LintResult(boolean valid, String ttl, String errors) {}
