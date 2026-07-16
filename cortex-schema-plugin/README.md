# Cortex Schema Plugin

Generate cortex's three foundational schema files from natural-language ontology requirements.

## What It Does

This Claude Code plugin provides the **`generate-cortex-resources`** skill, which guides you through authoring a complete, mutually-consistent set of cortex schema files:

- **`ontology.ttl`** — OWL 2 DL ontology (classes, properties, hierarchies)
- **`ontology.shapes`** — SHACL shapes for strict pre-inference validation (closed-world, cardinality, type constraints)
- **`ontology.rules`** — Jena generic-reasoner rules for controlled inference (including subclass/subproperty propagation and specific derived-class rules)

## Why Use It

Hand-authoring these three files is error-prone:

- **Consistency**: keeping per-class SHACL property allow-lists in sync with the ontology when classes/properties change
- **Closed-world enforcement**: correctly excluding inferred-only classes from the type allow-list so they can never be asserted directly
- **Rule completeness**: remembering to add generic entailment rules (subClass, subProperty, domain, range) plus specific rules for each inferred class
- **Cardinality mapping**: translating English-language cardinality requirements ("at least 1", "exactly 1") into SHACL constraints

The skill walks you through each step, generates the three files, and ensures they are internally consistent before writing.

## Quick Start

Invoke the skill with `/generate-cortex-resources` and describe your ontology in natural language:

```
Classes: Rule, Condition (with PositiveCondition and NegativeCondition as inferred subclasses), Action
Properties:
  - Rule → hasCondition → Condition (general)
  - Rule → activatedBy → Condition (subproperty of hasCondition; derives PositiveCondition)
  - Rule → blockedBy → Condition (subproperty of hasCondition; derives NegativeCondition)
  - Rule → hasAction → Action
  - Condition → hasExpression → Expression
  - Expression → hasCoordinate → Coordinate
  - Coordinate → hasDimension → Dimension (exactly 1)
  - Coordinate → hasValue → Literal (exactly 1, any datatype)

Cardinality:
  - Rule needs ≥1 activatedBy, ≥1 hasAction, optional blockedBy
  - Condition needs ≥1 hasExpression
  - Expression needs ≥1 hasCoordinate
```

The skill will ask clarifying questions, scaffold the three files, and validate them before writing to your cortex Spring Boot app's `src/main/resources/` directory.

## Architecture

### Closed-World Validation

SHACL validation runs **before inference** at ingest time (`LintService.validate`, invoked as part of ingestion). This design allows the `sh:in` type allow-list to be the enforcement mechanism for "never assert inferred classes directly":

- Every assertable (non-inferred) class is listed in `AssertionShape`'s `sh:in`.
- Every inferred-only class is **omitted** from that list.
- Any ingest that asserts `rdf:type :InferredClass` fails the type check.
- The rule reasoner derives the inferred class *later*, during `InferenceService.recomputeInference` (after approval).

### Rules

The generated `ontology.rules` file includes:

1. **Generic entailment rules** (always included):
   - `subClass`: propagate instance types down the class hierarchy
   - `subProperty`: propagate assertions down the property specialization hierarchy
   - `domain`: infer subject type from property domains
   - `range`: infer object type from property ranges

2. **Specific inference rules** (one per inferred-only class):
   - Keyed to the triggering property (e.g., `:activatedBy` → `:PositiveCondition`)
   - Simple forward-chaining: when a property is asserted, derive the corresponding type

### Namespace Conventions

Nothing in Cortex enforces or expects any particular namespace for your ontology, shapes, or instances — they are entirely yours to choose. (The repository's own example app, for instance, uses `example://ontology#` and `example://kb/`.) Pick namespaces that make sense for your application; HTTP URLs, URNs, and any other scheme are all fine.

**The one namespace that is off-limits is `cortex://`.** Cortex reserves it internally for its own bookkeeping resources — `cortex://provenance` (the provenance graph) and `cortex://branch-<uuid>` (each pending branch) — which are not part of your ontology or data. Do not mint instance, class, or shape IRIs under `cortex://`: a user-chosen name could collide with a reserved resource (an instance literally named `provenance` would collide with `cortex://provenance`) and would be confusing regardless.

## Testing

After generating resources with the skill, verify them by running your cortex Spring Boot app's test suite:

```bash
./gradlew :your-cortex-app:test
```

This boots the Spring context, which:
1. Parses `ontology.ttl` via Jena OntAPI
2. Parses `ontology.shapes` via Jena SHACL
3. Loads `ontology.rules` via Jena's generic rule reasoner

If any file has syntax or consistency errors, the context load fails with a clear error message.

## References

- [Jena OWL Ontology API](https://jena.apache.org/documentation/ontology/)
- [Jena SHACL](https://jena.apache.org/documentation/shacl/)
- [Jena Generic Rule Reasoner](https://jena.apache.org/documentation/inference/index.html#rules)
- [SHACL Specification](https://www.w3.org/TR/shacl/)
- [OWL 2 DL](https://www.w3.org/TR/owl2-overview/)
