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
| `cortex-spring-boot-autoconfigure` | Four `@AutoConfiguration`s grouped by *delivery mechanism*, plus one for the S3 client. |
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

The autoconfigure split is core / web / mcp / backup (see `AutoConfiguration.imports`), grouped by how
the graph is *delivered* — to the process, to humans, to agents, to durable storage — not by domain.
That grouping is the only reason `cortex.web.enabled`, `cortex.mcp.enabled`, and
`cortex.backup.enabled` can exist as single switches. Adding a domain-grouped config regresses it.

`CortexS3AutoConfiguration` is the one entry in `AutoConfiguration.imports` that is *not* a delivery
mechanism — it registers an S3 client and nothing else, gated on `cortex.s3.enabled`. It is separate
so that switch is real: backups are S3's only consumer today, and folding it into
`CortexBackupAutoConfiguration` would make `cortex.s3.enabled` a flag that must always equal
`cortex.backup.enabled`, which is no switch at all. `CortexBackupAutoConfiguration` still requires
it, and says so at startup.

**`BackupService` is deliberately not on `Cortex`.** It lives in `cortex-core`'s `core.store` package,
beside `AssertionStore` — that opens TDB2, this snapshots it — and is registered *only* by
`CortexBackupAutoConfiguration`, never by `CortexAutoConfiguration` or `CortexBuilder`. A backup is an
operation on the *store*, not the graph: TDB2-specific, returns a local filesystem path, and works
only on disk. On `Cortex` it would be the one method that throws for the default configuration
(`cortex.persistent` is `false`, and every `CortexBuilder.create().build()` is in-memory). So the
`Cortex` composite is still **nine roles**, `JenaCortex` still takes **eleven services**, and both
assembly paths still produce identical objects. Adding a role later is non-breaking; removing one
isn't.

The starter carries `webmvc`/`thymeleaf` because the autoconfigure jar ships Thymeleaf templates and
`@Controller` beans. They were `testImplementation` until v0.1.0, so "add the starter" shipped a UI
that could not serve. Don't demote them. The rule is *the starter brings what is on by default* —
which is why it deliberately does **not** bring Quartz or the AWS SDK: backups are off by default, so
those stay `compileOnly` and opting in means adding the dependency as well as setting the property.
Same principle, opposite conclusion; don't "fix" one into the other.

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

**The Quartz `JobDetail`/`Trigger` beans are name-matched for the same reason** —
`@ConditionalOnMissingBean(name = "cortexBackupJobDetail")` / `(name = "cortexBackupTrigger")` in
`BackupJobConfiguration`. A bare one matches the `JobDetail`/`Trigger` *type*, so it would back off for
any consumer who already has a Quartz job — silently disabling backups on exactly the applications
most likely to use Quartz, with nothing thrown. Boot collects `ObjectProvider<JobDetail>` precisely
because several job beans are normal.

**Backups only work on disk.** `DatabaseMgr.backup` → `DatabaseOps.checkSupportsAdmin` throws
`TDBException("Dataset does not support admin operations")` when the dataset has no container path —
always the case for `TDB2Factory.createDataset()`, i.e. `cortex.persistent=false`, the default.
`CortexBackupAutoConfiguration`'s **constructor** therefore validates persistence, the S3 settings,
the interval, and the presence of Quartz + the AWS SDK, and fails the context. Don't relax any of
that into a `@ConditionalOnClass` back-off: back-off is right for a property nobody set and wrong for
a consumer who explicitly asked for backups — the failure it produces is an app that boots healthy
and never backs anything up. `BackupServiceTests` pins the `TDBException`;
`CortexBackupAutoConfigurationTests` pins each fail-fast.

**`CortexBackupAutoConfiguration` must not name a Quartz or AWS type in a method signature.** They're
`compileOnly`, so a missing one would surface as a `NoClassDefFoundError` while Spring introspects the
class, pre-empting the constructor's explanation. That's why `BackupJobConfiguration` is a separate
`@ConditionalOnClass`-gated class it `@Import`s, and why the S3 client lives in
`CortexS3AutoConfiguration`.

**`software.amazon.awssdk:s3` ships no synchronous HTTP client.** It declares `apache-client` and
`url-connection-client` at *test* scope and brings only the async Netty client, so a sync `S3Client`
fails at `build()` with "Unable to load an HTTP implementation from any provider in the chain".
`apache-client` is a required dependency, and `CortexS3AutoConfiguration` names `ApacheHttpClient`
explicitly rather than trusting classpath discovery. It's also what `cortex.s3.proxy` hangs off.

**Not configuring a proxy is not the same as having no proxy.** `ApacheHttpClient.Builder` always
holds a `ProxyConfiguration`, and the SDK's own defaults resolve `useSystemPropertyValues` and
`useEnvironmentVariableValues` as `true` — so an ambient `https.proxyHost` or `HTTPS_PROXY` in the
deployment environment silently routes every upload through a proxy named nowhere in `cortex.s3.*`.
`CortexS3AutoConfiguration.proxyConfiguration` is therefore applied **unconditionally**, not only
when `cortex.s3.proxy.endpoint` is set, and both discovery settings default to `false` — deliberately
diverging from the SDK. Don't "simplify" it back to building the proxy only when an endpoint is
configured; that reopens the leak. `CortexS3ProxyTests` pins it with real system properties set.

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

**`/export` and `/import` are not inverses, and neither is a backup.** As of v0.1.1 `/export` serves
only the *approved assertions* (the default graph) as Turtle — no branches, no provenance, no
ontology. `/import` doesn't restore anything: it feeds the upload to `ingest`, so it is linted,
SHACL-validated, reduced to what is novel, staged on a branch, and redirected to `/branches` for a
human. Re-importing an export therefore stages nothing (it's all already approved) — which is what
`EndToEndIntegrationTests` asserts, and *why* the round-trip there is byte-identical. It is no longer
evidence of a restore. Backing up is `cortex.backup.enabled` (the whole dataset, via TDB2). Don't
reintroduce a write path that reaches the default graph without passing through review.

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
- Backups accumulate under `<cortex.assertionsLocation>/Backups/` forever — nothing prunes them. The
  upload is a copy, not a move, deliberately: deleting the local file on a successful upload would
  make a bucket misconfiguration destructive. This is the `.cortex/` residue problem below, now with
  a scheduler behind it. Retention belongs to the volume, or to an S3 lifecycle rule.
- ~335 MB of gitignored `.cortex/` runtime residue sits in the tree (repo root and the example) from
  prior runs with persistence on. Safe to delete when no process holds `db/tdb.lock`.
