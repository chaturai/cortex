# Cortex

[![CI](https://github.com/chaturai/cortex/actions/workflows/gradle.yml/badge.svg?branch=main)](https://github.com/chaturai/cortex/actions/workflows/gradle.yml)
[![Coverage](https://codecov.io/gh/chaturai/cortex/branch/main/graph/badge.svg)](https://codecov.io/gh/chaturai/cortex)
[![Release](https://img.shields.io/github/v/tag/chaturai/cortex?label=release&sort=semver)](https://github.com/chaturai/cortex/tags)

An ontology-backed knowledge graph memory store with branch-based ingestion, provenance, inference, and full-text search — exposed to AI agents over MCP.

## What's in the box?

Adding the starter auto-configures a complete knowledge graph memory for your Spring Boot application:

- **A `Cortex` bean** — the entry point to the knowledge graph, backed by Apache Jena. The approved assertions always live in a TDB2 store — in memory by default, or on disk with `cortex.persistent=true`. The inference closure and the full-text index built over it are derived data: an in-memory cache, rebuilt from the assertions on every startup, regardless of `cortex.persistent`.
- **Ontology-grounded storage** — you supply the vocabulary (`ontology.ttl`), the SHACL shapes ingested data must conform to (`ontology.shapes`), and inference rules (`ontology.rules`) on the classpath. The `cortex.ontologies`, `cortex.shapes`, and `cortex.rules` properties each accept a **list** of resources, merged in order, so a graph can be built from several ontology, shape, and rule files.
- **Ontology linting** — assertions are checked against the ontology: every class and property used must be defined there, with only `rdf:type`, `rdfs:label`, and `rdfs:comment` allowed beyond it. Linting returns the validated Turtle, ready to ingest, and ingestion fails fast on anything that does not lint clean.
- **Branch-based ingestion** — incoming assertions are linted, SHACL-validated **together with the already approved assertions** (so new statements may rely on approved ones to conform), trimmed of triples the graph already contains, and staged on a branch as an RDF patch. Nothing enters the graph until a human approves it; if every incoming triple is already approved, nothing is staged.
- **PROV-O provenance** — every ingestion is recorded as a `prov:Activity` (started when the branch is staged, ended when it is approved), and every approved statement is reified and linked to its activity with `prov:wasGeneratedBy`.
- **Rule-based inference** — derived statements are recomputed in full on startup, and extended incrementally with each approval's newly-added statements (not recomputed from scratch), and included in all query results.
- **Full-text search** — a Lucene index over `rdfs:label` and `rdfs:comment` (`rdf:type` is mapped as well, but Jena's text index only indexes literal values, so type IRIs are skipped).
- **An MCP server** — tools and resources that let AI agents read and write the graph (see below).
- **A web UI** — pages to explore the graph, search it from the navbar, and review, edit, and approve pending branches (see below).
- **Backup and restore** — the entire assertions dataset (approved assertions and every staged branch) can be downloaded as a TriG file from `/export` and restored from `/import`.

## Requirements

| | Minimum version |
|---|---|
| JDK | 21 |
| Spring Boot | 4.0 |

## Getting started

Add the Spring Boot starter to your project.

**Gradle**

```kotlin
implementation("ai.chatur:cortex-spring-boot-starter:0.1.0")
```

**Maven**

```xml
<dependency>
  <groupId>ai.chatur</groupId>
  <artifactId>cortex-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Configuration properties

All properties are bound under the `cortex` prefix. `ontologies`, `shapes`, and `rules` each accept a list of `classpath:`/`file:` resources, merged in order.

| Property | Default | Description |
|---|---|---|
| `cortex.persistent` | `false` | Whether the approved assertions are persisted to a TDB2 store on disk. When `false`, they are held in an in-memory TDB2 dataset instead — either way, the inference closure and full-text index are always an in-memory cache, rebuilt from the assertions on every startup. |
| `cortex.assertionsLocation` | `.cortex/db` | The directory of the TDB2 store for assertions, used only when `cortex.persistent=true`. |
| `cortex.ontologies` | `classpath:ontology.ttl` | The ontologies the knowledge graph is built on, in Turtle syntax, merged in order. |
| `cortex.shapes` | `classpath:ontology.shapes` | The SHACL shapes ingested assertions are validated against, in Turtle syntax, merged in order. |
| `cortex.rules` | `classpath:ontology.rules` | The Jena rules used for inference, concatenated in order. |
| `cortex.web.enabled` | `true` | Whether the web UI controllers are registered. Set to `false` to get the knowledge graph without the UI, without excluding individual controller beans. |
| `cortex.mcp.enabled` | `true` | Whether the MCP tools and resources are registered. Set to `false` to get the knowledge graph without an MCP server, without excluding individual tool beans. |

> **Single-JVM constraint:** when `cortex.persistent=true`, the TDB2 store is opened with `TDB2Factory.connectDataset`, which does not support two JVMs concurrently opening the same directory. Run one instance per persistent store directory.

## Modules

Cortex is split across five Gradle modules; the first four are published independently to Maven Central.

| Module | Purpose |
|---|---|
| `cortex-api` | The public contract: the `Cortex` interface and its nine role interfaces (`CortexOntology`, `CortexLinter`, `CortexIngestor`, `CortexBranches`, `CortexArchive`, `CortexQuery`, `CortexSearch`, `CortexStatistics`, `CortexInference`), plus the data records (`Term`, `BranchInfo`, `CortexStats`, etc). Pure Java — no dependencies. |
| `cortex-core` | The Jena-backed implementation (`JenaCortex`) and `CortexBuilder`, which assembles a `Cortex` without Spring — useful for embedding Cortex in a non-Spring application or for testing against a fresh, isolated graph. |
| `cortex-spring-boot-autoconfigure` | Spring Boot auto-configuration for the `Cortex` bean, the web UI controllers, and the MCP tools and resources. |
| `cortex-spring-boot-starter` | The single dependency consumers add to their project; brings in the autoconfigure module along with the Spring MVC and Thymeleaf dependencies the web UI needs. |
| `cortex-spring-boot-starter-example` | A runnable example Spring Boot application demonstrating the starter, with a sample ontology, shapes, and rules. Not published. |

## MCP tools and resources

The starter registers an MCP server (via Spring AI) through which agents interact with the knowledge graph.

### Tools

| Tool | Parameters | Description |
|---|---|---|
| **Lint** | `ttl` — RDF data in Turtle syntax | Checks the assertions against `cortex://ontology`: classes and properties not defined in the ontology are rejected, and only `rdf:type`, `rdfs:label`, and `rdfs:comment` are allowed beyond it. Returns the validated TTL, or the violations to fix. **Must be called before Ingest**; only the validated TTL it returns should be ingested. Read-only and idempotent. |
| **Ingest** | `ttl` — RDF data in Turtle syntax | Lints the assertions against the ontology (failing fast on lint violations), validates them against the SHACL shapes together with the approved assertions, trims triples the graph already contains, and stages the rest on a new branch for human review. Returns the branch name (`null` if every triple was already approved), or the lint or validation errors if the data does not conform. Assertions must use the vocabulary of `cortex://ontology` and should be linted with **Lint** first. The tool instructs agents to **Search** or **Query** existing assertions before generating new data and to reuse existing IRIs, so the same instance is never ingested under multiple names. |
| **Query** | `sparql` — a SPARQL `SELECT` query | Runs the query against the knowledge graph, including statements derived by inference. Returns the results as a text table. Read-only and idempotent. |
| **Ask** | `sparql` — a SPARQL `ASK` query | Answers a yes/no question against the knowledge graph, including statements derived by inference. Returns `true` or `false`. Read-only and idempotent. |
| **Describe** | `sparql` — a SPARQL `DESCRIBE` query | Fetches everything known about the described resources, including statements derived by inference. Returns the results in Turtle syntax. Read-only and idempotent. |
| **Search** | `text` — text to search for | Finds resources by fuzzy full-text search over their labels, tolerating small typos and spelling variations. Returns matches ranked by relevance. Read-only and idempotent. |

### Resources

| Resource | URI | Description |
|---|---|---|
| **Ontology** | `cortex://ontology` | The ontology the knowledge graph is built on, served in Turtle format (`text/turtle`). Agents should read it to ground the assertions and queries they produce. |

## Claude Code plugin

The repository also ships **cortex-schema** ([`cortex-schema-plugin/`](cortex-schema-plugin/README.md)), a [Claude Code plugin](https://code.claude.com/docs/en/plugins) that authors the three schema files a Cortex application needs — `ontology.ttl`, `ontology.shapes`, and `ontology.rules` — from a plain-English description of your classes, relations, and rules.

Its `/generate-cortex-resources` skill gathers requirements interactively, then generates a mutually-consistent set of files: an OWL 2 DL ontology, closed-world SHACL shapes that reject anything the ontology doesn't recognize (including direct assertion of inferred-only classes), and Jena reasoner rules for controlled inference — cross-checked for consistency before being written to your application's `src/main/resources/`. The namespaces are yours to choose; see the [plugin README](cortex-schema-plugin/README.md) for the one namespace Cortex itself reserves.

To use it, add the plugin directory as a marketplace in Claude Code and install the plugin:

```
/plugin marketplace add ./cortex-schema-plugin
/plugin install cortex-schema@cortex-schema
```

Then invoke `/generate-cortex-resources` and describe your ontology. See the [plugin README](cortex-schema-plugin/README.md) for the full workflow and an example.

## Web UI

The starter also serves a small UI for exploring the graph and reviewing what agents have staged. The navbar carries a search bar — press Enter to search, no button needed.

| Page | Description |
|---|---|
| `/` | Home page with knowledge graph statistics: triples added today, pending branches, asserted and inferred triples, ontology classes, SHACL shapes, and inference rules. |
| `/ontology` | The ontology in Turtle syntax. |
| `/assertions` | The class hierarchy of the ontology. |
| `/assertions?type={class}` | The known instances of a class, including inferred ones. |
| `/describe?uri={uri}` | Everything known about a resource — its statements with provenance timestamps. |
| `/search?q={text}` | Fuzzy full-text search results — matching resources linked to their describe pages, with the matched text alongside. |
| `/branches` | The branches staged by ingestion and awaiting review, each with its provenance activity rendered as badges: `prov:Activity`, when it was staged, and how many triples it carries. |
| `/branches/{branch}` | The assertions staged on a branch, grouped by subject with each statement shown as on the describe page. Reviewers can delete statements, edit objects, and rename subjects inline; changes stay in the browser until **Save changes** pushes them to the staged graph as an RDF patch (`POST /branches/{branch}/update` for deletions and object edits, `POST /branches/{branch}/rename` for subject renames — both JSON), and **Reset** discards them. **Approve** (`POST /branches/{branch}/approve` — merge into the graph with provenance, extending inference incrementally) and **Reject** (`POST /branches/{branch}/reject` — discard the branch) sit in the top-right corner. |
| `/export` | Downloads the entire assertions dataset — the approved assertions and every staged branch — as a dated `cortex-assertions-<date>.trig` file. |
| `/import` (`POST`, multipart `file`) | Restores the assertions dataset from a file previously downloaded from `/export`. |

## Migrating to 0.1.0

`0.1.0` is a breaking release. Cortex is pre-1.0, so the following were changed rather than deprecated:

- **`throws IOException` removed from the entire `Cortex` API.** Every occurrence was a phantom, traced to serializing into an in-memory `StringWriter`/`ByteArrayOutputStream` whose `close()` cannot fail but still declares the checked exception. `Cortex`, `JenaCortex`, and every method across all nine role interfaces no longer declare it. This is **source-breaking**: a `catch (IOException)` around a call that can no longer throw one is now a compile error, since `IOException` is a checked exception the `try` block no longer produces. Remove the `catch` (or the `throws` clause of your own method, if it only existed to relay Cortex's).
- **`Cortex` split into nine role interfaces**: `CortexOntology`, `CortexLinter`, `CortexIngestor`, `CortexBranches`, `CortexArchive`, `CortexQuery`, `CortexSearch`, `CortexStatistics`, and `CortexInference`. `Cortex` still extends all nine, so existing code that depends on `Cortex` keeps compiling and the single `Cortex` bean still satisfies every role by type — this is not source-breaking. New and existing consumers are encouraged to narrow their constructor parameter to the role they actually use (e.g. a search controller takes `CortexSearch`, not `Cortex`), which is also what makes them trivially fakeable in tests without stubbing the whole surface.
- **`Term` encoding changed for unprefixed IRIs.** For a URI with no matching namespace prefix, `Term.localName()` is now the **full URI** — matching `Term`'s own documented contract — instead of the bare local name after the last `#`/`/`. This is user-visible wherever unprefixed resources are rendered: `/assertions?type=`, `/describe?uri=`, `/search` link text, and `searchSubjects`/`describe` results returned to MCP agents. Prefixed IRIs (the common case, if your ontology declares prefixes for its namespaces) are unaffected.
- **`cortex.indexLocation` removed.** The full-text index is now always an in-memory Lucene index (`ByteBuffersDirectory`), regardless of `cortex.persistent`, since it is derived data rebuilt from the assertions on every startup. If you set this property, remove it — it is silently ignored.
- **`OntologyClass`'s `term` component renamed to `type`.** Update any code constructing or destructuring `OntologyClass` positionally or by component name.
- **The `/assertions/{id}` route was removed**; use `/describe?uri={uri}` instead (see [Web UI](#web-ui)).
- **The branch-discard operation is now called "Reject" everywhere, including in the UI.** The API (`CortexBranches#reject`) and route (`POST /branches/{branch}/reject`) were already named `reject`; only the web UI button previously read "Delete" and has been relabeled "Reject" to match. No API or route change, but update any UI automation asserting on the old button text.
