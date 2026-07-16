---
name: generate-cortex-resources
description: Generate cortex's ontology.ttl, ontology.shapes, and ontology.rules from a plain-English description of classes, relations, and rules. Produces strict closed-world SHACL that rejects any triple not recognized by the ontology, including explicit assertion of inferred-only classes. Use when the user wants to define or change a cortex ontology/schema.
---

# Generate Cortex Resources

Generate a complete, mutually-consistent set of three cortex schema files from natural-language ontology requirements: an OWL ontology (`ontology.ttl`), strict SHACL shapes for pre-inference validation (`ontology.shapes`), and Jena rules for controlled inference (`ontology.rules`).

## Workflow

### 1. Gather Requirements

Ask the user for:

- **Classes**: What entity types does the ontology model? List each with a one-line description (e.g., "Rule", "Condition", "Action").
- **Subclass hierarchies**: Are any classes specializations of others? (e.g., "PositiveCondition ⊑ Condition").
- **Object properties**: Relations between entities (e.g., "hasCondition: Rule → Condition").
- **Datatype properties**: Relations to literals with a specific datatype (e.g., "name: Person → string", "age: Person → integer").
- **Property specialization**: Do any properties inherit from a more general property? (e.g., "activatedBy ⊑ hasCondition").
- **Cardinality constraints**: For each property per class, is it required ("at least 1"), bounded ("exactly 1"), or optional ("zero or more")? (e.g., "Rule must have at least 1 activatedBy, at least 1 hasAction, zero or more blockedBy").
- **Inferred-only classes**: Which classes (if any) should *never* be asserted directly by a client, but only derived by a rule? (e.g., "PositiveCondition — derived when a Condition is activated by").

Clarify any ambiguities. Treat "zero or more" (optional, unbounded) as the default cardinality if not stated.

### 2. Namespace Conventions

Nothing in Cortex enforces or expects any particular URI scheme — the ontology, shapes, and instance namespaces are entirely the user's choice. Ask the user what they'd like to use, or default to a pattern like the one this repository's own example app uses:

- **Ontology/schema namespace**: `@prefix : <example://ontology#>` — classes and properties.
- **Shapes namespace**: `@prefix s: <example://shapes#>` — SHACL node shapes.
- **Instance namespace**: `@prefix kb: <example://kb/>` — asserted instance IRIs, e.g. constrained by `sh:pattern` to `^example://kb/[^/?#]+$`.

**`cortex://` is reserved and must never be used for user data.** Cortex uses it internally for its own bookkeeping resources — `cortex://provenance` (the provenance graph) and `cortex://branch-<uuid>` (each branch pending review) — which live in the same dataset as user assertions. Minting ontology, shape, or instance IRIs under `cortex://` risks colliding with these reserved resources (an instance named `provenance` would collide with `cortex://provenance` exactly) and must be avoided regardless of what scheme is otherwise chosen.

Every class and property declaration must include `rdfs:label` (human-readable name) and `rdfs:comment` (brief description). These are enforced by SHACL validation.

### 3. Draft the Ontology (ontology.ttl)

Scaffold using OWL 2 DL (Jena OntAPI will parse it):

```turtle
@prefix :     <example://ontology#> .
@prefix owl:  <http://www.w3.org/2002/07/owl#> .
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

:Rule a owl:Class ;
    rdfs:label "Rule" ;
    rdfs:comment "A rule that can be activated or blocked by conditions and performs actions." .

:Condition a owl:Class ;
    rdfs:label "Condition" ;
    rdfs:comment "A condition evaluated to determine rule activation." .

:PositiveCondition a owl:Class ;
    rdfs:subClassOf :Condition ;
    rdfs:label "Positive Condition" ;
    rdfs:comment "A condition whose satisfaction activates a rule. Inferred-only: never asserted directly." .

:hasCondition a owl:ObjectProperty ;
    rdfs:domain :Rule ;
    rdfs:range :Condition ;
    rdfs:label "Has Condition" ;
    rdfs:comment "A rule has a condition." .

:activatedBy a owl:ObjectProperty ;
    rdfs:subPropertyOf :hasCondition ;
    rdfs:domain :Rule ;
    rdfs:range :Condition ;
    rdfs:label "Activated By" ;
    rdfs:comment "A rule is activated by a positive condition." .
```

**Rules for the ontology:**
- Use `owl:Class` for all classes.
- Use `owl:ObjectProperty` for relations between IRIs, `owl:DatatypeProperty` for relations to literals.
- Every property must declare `rdfs:domain` and `rdfs:range`.
- Use `rdfs:subClassOf` to express class hierarchies; `rdfs:subPropertyOf` for property specialization.
- Every class and property must have `rdfs:label` and `rdfs:comment`.
- An `owl:Ontology` declaration is optional — Cortex's loader parses the file either way — so include one only if the user wants the standard ontology-metadata header; it is not required for anything downstream to work.

Ask the user to confirm the ontology before proceeding.

### 4. Draft the Rules (ontology.rules)

Rules use Jena's generic-rule-reasoner syntax: `[ruleName: bodyTriples -> headTriples]`.

Always include these four **generic entailment rules** first:

```
[subClass: (?x rdf:type ?c1) (?c1 rdfs:subClassOf ?c2) -> (?x rdf:type ?c2)]

[subProperty: (?x ?p1 ?y) (?p1 rdfs:subPropertyOf ?p2) -> (?x ?p2 ?y)]

[domain: (?p rdfs:domain ?c) (?x ?p ?y) -> (?x rdf:type ?c)]

[range: (?p rdfs:range ?c) (?x ?p ?y) -> (?y rdf:type ?c)]
```

Then, for each **inferred-only class** from step 1, add one rule. The rule's body must reference the triggering property, and the head must derive the inferred-only class type. Example: if `PositiveCondition` is inferred-only and triggered by `:activatedBy`, add:

```
[positiveCondition: (?rule rdf:type :Rule) (?rule :activatedBy ?condition) -> (?condition rdf:type :PositiveCondition)]
```

**No other rules are needed.** The generic rules handle subclass propagation, property inheritance, and domain/range closure. Specific rules only for inference.

Ask the user to confirm the rules before proceeding.

### 5. Draft the Shapes (ontology.shapes)

SHACL validation runs on the raw, pre-inference graph at ingest time. It enforces:
1. Every assertion subject is a valid instance IRI in the chosen instance namespace.
2. Every assertion has a declared type (class).
3. Only assertable (non-inferred) classes may be asserted.
4. Every assertion has `rdfs:label` and `rdfs:comment`.
5. Each class strictly limits its properties (closed-world).
6. Properties match their declared cardinality and type constraints.

**General shape (common to all assertions):**

```turtle
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix sh:   <http://www.w3.org/ns/shacl#> .
@prefix :     <example://ontology#> .
@prefix s:    <example://shapes#> .

s:AssertionShape a sh:NodeShape ;
    sh:targetSubjectsOf rdf:type ;
    sh:targetSubjectsOf :hasCondition ;
    sh:targetSubjectsOf :activatedBy ;
    sh:targetSubjectsOf :blockedBy ;
    # ... (sh:targetSubjectsOf for every property in the ontology)

    sh:nodeKind sh:IRI ;
    sh:pattern "^example://kb/[^/?#]+$" ;
    sh:message "assertions must be example://kb/{name} IRIs without sub-paths" ;

    sh:property [
        sh:path rdf:type ;
        sh:minCount 1 ;
        sh:in ( :Rule :Condition :Action :Expression :Coordinate :Dimension ) ;
        sh:message "unknown class: only assertable classes are allowed" ;
    ] ;

    sh:property [
        sh:path rdfs:label ;
        sh:minCount 1 ;
        sh:nodeKind sh:Literal ;
        sh:message "every assertion must have an rdfs:label" ;
    ] ;
    sh:property [
        sh:path rdfs:comment ;
        sh:minCount 1 ;
        sh:nodeKind sh:Literal ;
        sh:message "every assertion must have an rdfs:comment" ;
    ] .
```

**Critically:** The `sh:in ( ... )` list must include **every assertable class but exclude every inferred-only class**. This is the enforcement mechanism: asserting `:PositiveCondition` or `:NegativeCondition` directly will fail validation.

**Per-class shapes:** For each assertable class, add a `sh:closed true` `sh:NodeShape`:

```turtle
s:RuleShape a sh:NodeShape ;
    sh:targetClass :Rule ;

    sh:closed true ;
    sh:property [ sh:path rdf:type ] ;
    sh:property [ sh:path rdfs:label ] ;
    sh:property [ sh:path rdfs:comment ] ;

    sh:property [
        sh:path :activatedBy ;
        sh:class :Condition ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "Rule must have exactly 1 activatedBy" ;
    ] ;

    sh:property [
        sh:path :hasAction ;
        sh:class :Action ;
        sh:minCount 1 ;
        sh:message "Rule must have at least 1 hasAction" ;
    ] ;

    sh:property [
        sh:path :blockedBy ;
        sh:class :Condition ;
        sh:message "blockedBy, if present, must reference a Condition" ;
    ] .
```

**Rules:**
- One `sh:NodeShape` per assertable class, `sh:targetClass` to that class.
- `sh:closed true` on each per-class shape to reject unknown properties.
- Always include `[ sh:path rdf:type ]`, `[ sh:path rdfs:label ]`, `[ sh:path rdfs:comment ]` in the allowed properties list.
- For properties on that class:
  - `sh:minCount 1` if the property is required; omit if optional.
  - `sh:maxCount 1` if the property has exactly-one cardinality; omit if unbounded.
  - `sh:class :ClassName` if the property is an object property (range is a class).
  - `sh:datatype xsd:string` (or other XSD type) if the property is a datatype property; use `sh:nodeKind sh:Literal` if any literal datatype is acceptable.
- **Never add a shape for inferred-only classes** — they never appear in the pre-inference graph.

Ask the user to confirm the shapes before proceeding.

### 6. Consistency Validation

Before writing files, run these checks:

1. Every property referenced in `ontology.rules` or `ontology.shapes` exists in `ontology.ttl`.
2. Every property that has `rdfs:domain ?c` in `ontology.ttl` appears in shape `s:CShape` (the per-class shape for class `?c`).
3. Every inferred-only class exists in `ontology.ttl` as a subclass of some assertable class, has a rule in `ontology.rules`, and is **absent from** `s:AssertionShape`'s `sh:in` list.
4. No typos in prefixes, and none of the chosen namespaces is `cortex://` (reserved — see Namespace Conventions above).

If any check fails, report the violation with line numbers and ask the user to clarify.

### 7. Write to Files

Ask the user for the target location (or confirm if the repo has only one cortex Spring Boot app example). Write the three files:

- `ontology.ttl` to the target module's `src/main/resources/ontology.ttl`.
- `ontology.shapes` to the target module's `src/main/resources/ontology.shapes`.
- `ontology.rules` to the target module's `src/main/resources/ontology.rules`.

Confirm success: "Three files written to `{module}/src/main/resources/`. Ontology is live."

## Guard-rails

- **Never invent vocabulary:** Only use classes and properties explicitly requested by the user.
- **Never add inferred-only classes to `sh:in`:** A class in `sh:in` means it is allowed to be asserted; inferred classes are derived by rules only.
- **Always keep per-class shapes in sync:** If a class has a property with `rdfs:domain :ClassName`, that property must appear in `s:ClassNameShape`'s allowed list. If `sh:closed true`, omitting it will cause valid instances to fail.
- **Never use `cortex://` for the ontology, shapes, or instance namespace:** it is reserved for Cortex's own `cortex://provenance` and `cortex://branch-<uuid>` resources. Any other scheme the user prefers — `http://`, a custom URN, etc. — is fine.
- **Trust the domain/range declaration:** The `domain` and `range` rules in `ontology.rules` will automatically infer the subject/object type from a property assertion; you do not need to repeat the type check in shapes.

## Conventions

- **Naming**: Use `PascalCase` for class names (`:Rule`, `:Condition`), `camelCase` for property names (`:hasCondition`, `:activatedBy`).
- **Labels**: Every class and property must have an `rdfs:label` (a Literal, often the same as the local name) and an `rdfs:comment` (a longer description).
- **Hierarchy**: Use `rdfs:subClassOf` to express "is-a" relationships; `rdfs:subPropertyOf` for property specialization.
- **Cardinality**: Express as `sh:minCount n` / `sh:maxCount m` in the shape, keyed to the property's role in each class. "At least 1" = `sh:minCount 1`; "exactly 1" = `sh:minCount 1 ; sh:maxCount 1`; "optional" = no minCount; "unbounded" = no maxCount.
- **Object vs. data properties**: If the property points to a class (an IRI), use `owl:ObjectProperty` and `sh:class` in shapes; if it points to a literal (a string, number, date), use `owl:DatatypeProperty` and `sh:datatype` or `sh:nodeKind sh:Literal`.
