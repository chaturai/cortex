# Cortex

[![CI](https://github.com/chaturai/cortex/actions/workflows/gradle.yml/badge.svg?branch=main)](https://github.com/chaturai/cortex/actions/workflows/gradle.yml)
[![Coverage](https://codecov.io/gh/chaturai/cortex/branch/main/graph/badge.svg)](https://codecov.io/gh/chaturai/cortex)
[![Release](https://img.shields.io/github/v/tag/chaturai/cortex?label=release&sort=semver)](https://github.com/chaturai/cortex/tags)

An ontology-backed knowledge graph memory store with branch-based ingestion, provenance, inference, and full-text search — exposed to AI agents over MCP.

## What's in the box?

Adding the starter auto-configures a complete knowledge graph memory for your Spring Boot application:

- **A `Cortex` bean** — the entry point to the knowledge graph, backed by Apache Jena. Runs fully in memory by default, or persists to disk (TDB2 store + Lucene index) with `cortex.persistent=true`.
- **Ontology-grounded storage** — you supply the vocabulary (`ontology.ttl`), the SHACL shapes ingested data must conform to (`shapes.ttl`), and inference rules (`ontology.rules`) on the classpath; locations are configurable via `cortex.*` properties.
- **Branch-based ingestion** — incoming assertions are SHACL-validated and staged on a branch. Nothing enters the graph until a human approves it; approved statements are recorded with a provenance timestamp.
- **Rule-based inference** — derived statements are recomputed on startup and after every approval, and included in all query results.
- **Full-text search** — a Lucene index over `rdfs:label`.
- **An MCP server** — tools and resources that let AI agents read and write the graph (see below).
- **A web UI** — pages to explore the graph and review pending branches (see below).

## Requirements

| | Minimum version |
|---|---|
| JDK | 21 |
| Spring Boot | 4.0 |

## Getting started

Add the Spring Boot starter to your project.

**Gradle**

```kotlin
implementation("ai.chatur:cortex-spring-boot-starter:0.0.1")
```

**Maven**

```xml
<dependency>
  <groupId>ai.chatur</groupId>
  <artifactId>cortex-spring-boot-starter</artifactId>
  <version>0.0.1</version>
</dependency>
```

## MCP tools and resources

The starter registers an MCP server (via Spring AI) through which agents interact with the knowledge graph.

### Tools

| Tool | Parameters | Description |
|---|---|---|
| **Ingest** | `ttl` — RDF data in Turtle syntax | Validates the assertions against the SHACL shapes and stages them on a new branch for human review. Returns the branch name, or the validation errors if the data does not conform. Assertions must use the vocabulary of `cortex://ontology`. |
| **Query** | `sparql` — a SPARQL `SELECT` or `ASK` query | Runs the query against the knowledge graph, including statements derived by inference. Read-only and idempotent. |
| **Search** | `text` — text to search for | Finds resources by full-text search over their labels. Returns matches ranked by relevance. Read-only and idempotent. |

### Resources

| Resource | URI | Description |
|---|---|---|
| **Ontology** | `cortex://ontology` | The ontology the knowledge graph is built on, served in Turtle format (`text/turtle`). Agents should read it to ground the assertions and queries they produce. |

## Web UI

The starter also serves a small UI for exploring the graph and reviewing what agents have staged:

| Page | Description |
|---|---|
| `/` | Redirects to `/ontology`. |
| `/ontology` | The ontology in Turtle syntax. |
| `/assertions` | The class hierarchy of the ontology. |
| `/assertions?type={class}` | The known instances of a class, including inferred ones. |
| `/assertions/{id}` | Everything known about a resource — its statements with provenance timestamps. |
| `/branches` | The branches staged by ingestion and awaiting review, with actions to **approve** (merge into the graph with provenance, recompute inference) or **reject** (discard) each one. |
| `/branches/{branch}` | The assertions staged on a branch, in Turtle syntax. |
