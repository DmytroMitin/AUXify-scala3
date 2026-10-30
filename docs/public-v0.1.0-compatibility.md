# Published 0.1.0 baseline and development boundary

On 30 October 2026, the original released starter and a small shared behavioral
probe passed independently against public AUXify 0.1.0 and development
0.2.0-SNAPSHOT on exact Scala 3.3.8, 3.8.4 and 3.9.0 with JDK 25.

The public source was
[AUXify-scala3.g8 at 6d442f5](https://github.com/DmytroMitin/AUXify-scala3.g8/tree/6d442f5b2b17a9dc8df713647b8c07a55250d696).
Qualification cloned that commit into a disposable directory, rendered it through
Giter8's supported file-URI route with explicit name, package and Scala options,
and compiled the original nine generated Scala files. Each compiler line had
separate empty Ivy, Coursier, sbt boot and sbt global directories and an explicit
Maven Central-only resolver configuration. No local producer repository or
snapshot supplied the public build.

All eight starter checks and `AUXIFY_RELEASE_STARTER_PASS` passed on each line.
`verifyReleaseStarterRuntimeClasspath` passed; an additional audit checked that
Macro-Paradise compiler implementation also stayed off the application runtime.
The runtime retained the binary-crossed marker and Scala libraries, with no
AUXify handler, Quasiquotes or Macro-Paradise compiler implementation. Compile
options contained exactly one plugin requirement, exact-line handler classpath
and SHA-256 external artifact identity.

The public graph used AUXify 0.1.0, public sbt-macroparadise/compiler 0.1.1 and
transitive Quasiquotes 0.3.0. Marker coordinates use `_3`; handlers use the full
compiler suffix. Downloaded artifact origin URLs and SHA-256 hashes were
recorded. The public `v0.1.0` tag still identifies
[`36b789d`](https://github.com/DmytroMitin/AUXify-scala3/tree/36b789dda567a09e7413fbfa4bdcdacc13efaf1b).

The same generated Scala source bytes were then compiled under the separately
source-built development graph. The supported intersection demonstrated:

| Released source profile | Shared observations |
| --- | --- |
| Simple `@apply Show[A]` | Contextual accessor returns the given object and preserves behavior |
| Bounded two-parameter `@aux` and full `@apply` with one abstract result | Generated Aux and dependent result refinements typecheck; full accessor preserves given identity |
| Historical `@instance` | Parameterless carrier remains by-name, strict binary carrier evaluates once, inherited unary method dispatches through the primary method |
| Unary `@delegated` | Ordinary companion invocation forwards correctly |
| Zero-parameter default `@self` | Generated Self type accepts the original object |
| `@apply+@delegated`, `@apply+@aux`, `@apply+@instance` | Released closed profiles work in both source orders |

These are bounded source and observable behavior checks. They do not promise
binary compatibility between exact compiler plugins, identical generated trees,
or compatibility for arbitrary source forms. In particular, the released
historical factory tail permits at most one later public monomorphic unary
`(A): A` concrete method. Current development accepts more validated tails.
Release near-miss diagnostics also lack later modifier-admission hardening.

Development-only features include `@syntax`, inherited alias chains and
alias-typed methods, abstract-type-first and curried factories, Out-rooted
abstract-factory tails, parameterless delegation, and the separately documented
wider composition families. The
[development ledger](real-consumer-workflow.md) uses these capabilities and is
not a public 0.1.0 consumer. The existing integration suites under
[`integration-tests`](../integration-tests/src/test/scala/com/github/dmytromitin/auxify/integration)
retain the detailed type, runtime and composition contracts.

Direct abstract-val factories, generic-owner or nondefault-option `@self`,
two-result aux/full-apply and binary delegated families remain unsupported.

The full development matrix also passed 662 handler and 150 integration tests
per exact line, controlled negative and atomic rollback checks, dependency
provenance, the external ledger and contributor bootstrap. This leaves future
0.2.0 release readiness **blocked**: stable peer releases, an actual frozen
signed candidate with fresh public-resolver consumers, and exact-candidate
hosted CI and compatibility/support review remain separate gates. No remote artifact publication
or release operation was performed for this baseline.
