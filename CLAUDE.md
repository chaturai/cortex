# CLAUDE.md

Working notes for Cortex — an ontology-grounded RDF knowledge graph library (Apache Jena) published
to Maven Central. See `README.md` for what it does and how to use it; this file covers what the code
can't tell you by itself.

## Build gotchas

**Spotless gates the build.** `./gradlew build` fails on unformatted code (`googleJavaFormat` for
Java, `ktfmt` for `.gradle.kts`). Run `./gradlew spotlessApply` before building or committing. This
is the most likely thing to waste your time.

**`-parameters` is load-bearing, not incidental.** MCP tool schemas are derived from reflective
parameter names. Dropping the flag silently produces tools with unusable argument names. Set in
`buildSrc/src/main/kotlin/cortex-common-conventions.gradle.kts`.

**`-Xdoclint:all` is fully enabled** on the three published modules, including `missing`
(`buildSrc/src/main/kotlin/cortex-library-conventions.gradle.kts`). Undocumented public members and
stale `@link`s **fail the build**. This was `Xdoclint:none` until v0.1.0, which is exactly how ~45
undocumented public members and several broken links shipped unnoticed. Don't weaken it.

**JaCoCo gates at LINE ≥ 85%** (`check` depends on `jacocoTestCoverageVerification`). `cortex-api`
is skipped — it's records and interfaces with no test source of its own; it's covered transitively
via `cortex-core`'s tests, visible in `:cortex-spring-boot-autoconfigure:testCodeCoverageReport`.

Versions live in `gradle/libs.versions.toml`; `buildSrc` wires the same catalog through
`buildSrc/settings.gradle.kts`. Everything goes through `libs.*` — don't hardcode a version.

## Module boundaries — these are rules, not preferences

| Module | Constraint |
|---|---|
| `cortex-api` | **Zero runtime dependencies.** No Jena. Records + interfaces only. |
| `cortex-core` | Jena implementation. **Must not depend on Spring.** |
| `cortex-spring-boot-autoconfigure` | Three `@AutoConfiguration`s grouped by *delivery mechanism*. |
| `cortex-spring-boot-starter` | Brings `webmvc` + `thymeleaf` as `api`. |
| `cortex-spring-boot-starter-example` | Demo app. Not published. |
| `cortex-schema-plugin` | A Claude Code plugin, **not a Gradle module** — correctly absent from `settings.gradle.kts`. |

`cortex-api`'s zero-dependency rule has real consequences: `Terms` lives in `cortex-core`, not beside
`Term`, because building a `Term` needs Jena types.

`cortex-core`'s no-Spring rule is why the loaders take `List<String>` (file *content*) rather than
Spring's `Resource`. The Spring layer reads content as UTF-8 and passes strings down. Don't plumb a
`Resource` into core to save a line.

`CortexBuilder` (`cortex-core`, package `ai.chatur.cortex`) assembles a `Cortex` without Spring and
wires the same eleven services `CortexAutoConfiguration` does. If you add a core service, **both**
need updating or the two assembly paths drift.

The autoconfigure split is core / web / mcp (see `AutoConfiguration.imports`), grouped by how the
graph is *delivered*, not by domain. That grouping is the only reason `cortex.web.enabled` and
`cortex.mcp.enabled` can exist as single switches. Adding a domain-grouped config regresses it.

The starter carries `webmvc`/`thymeleaf` because the autoconfigure jar ships Thymeleaf templates and
`@Controller` beans. They were `testImplementation` until v0.1.0, so "add the starter" shipped a UI
that could not serve. Don't demote them.

`cortex-schema-plugin` has **no CI** — nothing validates its instructions against the code. Its docs
drifted badly once (it told users to write `shapes.ttl` while the loader reads `ontology.shapes`,
producing an app that couldn't boot). If you change resource names, property names, or namespaces,
check that plugin by hand.

## Traps

**`@ConditionalOnMissingBean` on the two `Dataset` beans must be name-based** —
`@ConditionalOnMissingBean(name = "assertions")` / `(name = "inferences")`. A bare one matches by
return *type* and is evaluated in bean-definition *order*, so `inferences` silently backs off because
`assertions` already registered a `Dataset`. The graph then boots with `inferences == assertions`,
every search returns nothing, and **nothing throws**. `CortexAutoConfigurationTests` has a regression
test asserting the two are distinct instances. Neither is `@Primary`, so injecting a bare `Dataset`
is deliberately a `NoUniqueBeanDefinitionException`.

**`cortex://` is reserved.** `CortexNamespace.NS` holds `cortex://provenance` and
`cortex://branch-<uuid>`. User ontologies use their own namespace (the example uses
`example://ontology#`). The schema plugin used to mandate the opposite — user instances at
`^cortex://[^/?#]+$`, which matches `cortex://provenance` exactly.

**The provenance graph is not a branch.** Every branch operation — readers *and* writers — goes
through `BranchRepository.onBranch`. It was previously enforced on writers only, so
`getBranch("provenance")` rendered the entire provenance graph as a reviewable branch. Don't add a
branch accessor that reaches for `assertions.getNamedModel(...)` directly; Jena returns an empty
model for a missing graph, so the bug is silent.

**`ingest` and `approve` are each one write transaction.** They used to be three, which made the
SHACL verdict and the novelty diff raceable, and could leave data merged with the branch still
pending — re-approving then wrote a second `prov:Activity`, corrupting `triplesAddedToday` and
`describe`'s `MIN(?ended)`. Don't split them. TDB2's single-writer serialization is what makes them
safe.

**All `Term` construction goes through `Terms.of(RDFNode, PrefixMapping)`** — exactly one place.
Three competing constructions previously produced contradictory encodings for the same node
(`/assertions` and `/describe` rendered it differently). It uses explicit longest-prefix-match rather
than Jena's `shortForm`, which returns the first `startsWith` hit over a `HashMap` and so isn't
deterministic. Don't hand-roll a fourth.

**`cortex.persistent` means assertions only.** The inference closure and the Lucene text index are
*always* in-memory and rebuilt at startup — they're a derived cache. There is no `indexLocation`.

**TDB2 is single-JVM.** Two JVMs on one directory corrupt each other.

**`JenaCortex.approve` can't be fully atomic** — `BranchMergeService` and `InferenceService` guard
independent `Dataset` instances, so the merge and the closure extension can't share a transaction. On
failure it recomputes the closure from the committed assertions and rethrows. Read its Javadoc before
touching it.

## Testing

- **Core behavior tests** → `cortex-core/src/test/`, plain JUnit + AssertJ over `CortexBuilder`, with
  an **isolated graph per test**. That isolation is why they can use stable readable IRIs; the old
  suite needed UUID-freshness crutches because every test shared one dataset.
- **Controller/tool tests** → hand-rolled fakes of the narrow role interfaces, **no Spring context**.
  This is the payoff of the nine-role split (`CortexSearch` is a lambda; `Cortex` is 23 stubs).
- **`EndToEndIntegrationTests`** is the one test that boots a full context, and it earns it.
- **AssertJ only — zero bare `assert`.** The suite had 136 of them; bare `assert` silently no-ops
  without `-ea`, so any runner without it passed everything. Don't reintroduce one.
- `*IntegrationTests` boots Spring; `*Tests` is isolated. The names are honest now — keep them so.

## Known open items

- `InferenceInitializer` swallows `RuntimeException` on startup (fail-soft). The app then reports
  healthy with an empty inference closure: every query and search returns empty, indistinguishable
  from an empty graph. Recommendation on record: a `HealthIndicator`, not failing the boot.
- `IngestResult` encodes three legal states as flag + nulls (`valid=true,branch!=null`;
  `valid=true,branch==null` meaning "nothing novel"; `valid=false,errors!=null`). A sealed interface
  would make them exhaustive. Recommended, not done.
- `BranchEditService.updateBranch` and `removeDanglingReferences` are two separate write
  transactions; a concurrent reject/approve between them makes the cleanup a silent no-op. Not
  corrupting, just skipped.
- `BranchRepository.onBranch` has a check-then-act TOCTOU shared by all write paths (pre-existing).
- ~335 MB of gitignored `.cortex/` runtime residue sits in the tree (repo root and the example) from
  prior runs with persistence on. Safe to delete when no process holds `db/tdb.lock`.
