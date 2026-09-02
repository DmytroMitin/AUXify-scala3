# AUXify-scala3

AUXify-scala3 currently provides experimental first Scala 3 `@apply`, `@aux`,
`@self`, and `@delegated` development milestones. The current product is qualified on
exact Scala 3.3.8 and Scala 3.8.4 with JDK 25. Scala 3.8.4 remains the default
developer line.

## Related projects

- [quasiquotes-scala3](https://github.com/DmytroMitin/quasiquotes-scala3) —
  neutral and Scala 3 quasiquotes plus exact lowering used by AUXify.
- [macroparadise-scala3](https://github.com/DmytroMitin/macroparadise-scala3) —
  the Scala 3 Macro-Paradise compiler plugin that runs the annotations.
- [AUXify (Scala 2)](https://github.com/DmytroMitin/AUXify) — the original
  Scala 2 implementation whose delivered macro annotations define the main
  parity target.

## Annotation status

| Annotation | Public status | Current boundary |
| --- | --- | --- |
| Simple `@apply` for the proven `Show[A]`-style trait shape | Supported development milestone | Qualified on exact Scala 3.3.8 and Scala 3.8.4 with JDK 25 |
| Full `@apply` for the path-dependent/refined `Add.Out` form | Supported first development slice | Exactly two invariant parameters with the same simple named upper bound and one compatible abstract result type member; qualified on exact Scala 3.3.8 and Scala 3.8.4 with JDK 25 |
| `@aux` | Supported first development slice | Exactly two invariant parameters with the same unqualified named upper bound and one compatible abstract result type member; generates a companion `Aux` alias and is qualified on exact Scala 3.3.8 and Scala 3.8.4 with JDK 25 |
| `@instance` | Characterized / not yet implemented | Not part of the supported product milestone |
| `@delegated` for the first `Show[A]`-style one-method forwarding shape | Supported first development slice | One public abstract direct method with one ordinary parameter of the enclosing type and one simple named result; richer forwarding remains later parity work |
| Stacked `@apply` + `@delegated` | Supported bounded composition slice | Both source orders on the common one-invariant-unbounded-parameter, one-eligible-method family only; this is not arbitrary annotation composition |
| Stacked `@apply` + `@aux` | Supported bounded composition slice | Both source orders and independent direct `apply` / type `Aux` conflicts pass on the exact common `Add`-style first-slice family; both handlers consume one shared source decoder, making a first-success/second-source-decoder-rejection state structurally unreachable within that envelope |
| `@syntax` | Characterized / not yet implemented | The selected Scala 3 design uses native extension methods while preserving the `import TypeClass.syntax.*` and receiver-call style |
| `@self` for a plain zero-parameter trait with default semantics | Supported first development slice | Class/object/generic targets and `lowerBound` / `fBound` options are not yet supported |
| `@poly` | Postponed / not parity-blocking | Wait for a Scala 3 ad-hoc polymorphic-function abstraction adequate for the planned Shapeless `PolyN` / `Case.Aux` adapter |

Scala 2 AUXify never implemented `@poly`; its planned target was an adapter
from an AUXify type class to Shapeless `Poly1` / `Poly2` case dispatch, where
separate `Case.Aux` instances could select different implementations and
result types. Shapeless 3 currently does not provide that counterpart.
Standard Scala 3 polymorphic function types such as `[A] => List[A] => List[A]`
are parametric function values, not a direct replacement for that ad-hoc case
lookup model.

The annotation can be reconsidered if a suitable Scala 3 library abstraction
emerges, or if an independently justified abstraction is later designed in
AUXify or, preferably when it has broader value, a separate reusable project.
That prerequisite is a future design option, not a commitment by AUXify to
build it.

The supported `@apply`, `@aux`, `@self`, and `@delegated` slices and their dependencies
remain development, local-source-built artifacts. They are not claimed to be
stable or available from a remote artifact repository.

### Development module coordinates

- Marker: `com.github.dmytromitin:auxify-scala3-macro-annotations_3:0.1.0-SNAPSHOT`
  — Scala 3 binary-crossed (`_3`).
- Handler: `com.github.dmytromitin:auxify-scala3-macro-handlers_<exact-scala>:0.1.0-SNAPSHOT`
  — exact-full-cross, with separately built `_3.3.8` and `_3.8.4` artifacts,
  because it participates in the compiler-sensitive handler universe.

Both coordinates are development/local-only at this stage.

For a supported generic trait such as:

```scala
import com.github.dmytromitin.auxify.macros.apply

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[String] with
    def show(a: String): String = a

val stringShow: Show[String] = Show[String]
```

the annotation conceptually adds this contextual materializer to the
companion:

```scala
def apply[A](using inst: Show[A]): Show[A] = inst
```

The first full slice additionally supports this bounded result-member topology:

```scala
@apply
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out
```

It conceptually adds:

```scala
def apply[N <: Nat, M <: Nat](using inst: Add[N, M]):
  Add[N, M] { type Out = inst.Out } = inst
```

For this slice, the two invariant enclosing parameters must have the same
unqualified named upper bound and no lower or context bounds. The trait must
have exactly one direct, public, unannotated, non-polymorphic abstract type
member with no lower bound and that same named upper bound. Its source name is
preserved, so a renamed `Combine[L <: Natural, R <: Natural]` with abstract
`Result <: Natural` is supported too. Ordinary type-class methods are allowed;
multiple result members, aliases, modifiers, and differing or complex bounds
remain outside this first slice.

The first public `@aux` slice accepts that same exact bounded result-member
family and adds a direct companion type alias. For example:

```scala
import com.github.dmytromitin.auxify.macros.aux

@aux
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out
```

conceptually adds:

```scala
type Aux[N <: Nat, M <: Nat, Out0 <: Nat] =
  Add[N, M] { type Out = Out0 }
```

Trait, parameter, bound, and result-member names are source-derived. The added
result parameter is selected deterministically from the result-member stem to
avoid direct source-name collisions, but its exact spelling is not a public
compatibility guarantee. An existing direct companion type `Aux` is preserved;
a same-spelling term is in a separate namespace. Multiple result members,
aliases, any explicitly declared lower bounds, differing or compound bounds,
polymorphic result members, inherited discovery, and semantic alias expansion are outside
this first slice.

The first supported `@delegated` slice is:

```scala
import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait Show[A]:
  def show(a: A): String
```

It conceptually adds this direct companion forwarder:

```scala
def show[A](a: A)(using inst: Show[A]): String = inst.show(a)
```

The evidence name is generated deterministically and avoids the ordinary
parameter name; its exact spelling is not a compatibility contract. This slice
requires exactly one invariant unbounded enclosing type parameter and exactly
one public, abstract, unannotated direct method. That method must have no
method-owned type parameters, exactly one ordinary clause containing exactly
one non-defaulted unmodified parameter whose type is the enclosing type
parameter, and one simple unqualified named result type. The generated method
adds a final `using` instance and delegates directly to the same method name.
A direct same-name companion member is preserved under the current bounded
syntactic conflict policy, so no generated overload is added in that case.

Additional methods or clauses, method-owned type parameters, contextual or
default parameters, overloads, applied/qualified/function/path-dependent
results, abstract-member result rewriting, and wider historical forwarding
semantics remain later parity work.

The supported `@apply` and `@delegated` slices may be stacked in either source
order when the same trait independently satisfies both existing closed target
profiles:

```scala
@apply
@delegated
trait ApplyThenDelegated[A]:
  def show(a: A): String

@delegated
@apply
trait DelegatedThenApply[A]:
  def show(a: A): String
```

Both forms produce the contextual `apply` materializer and the delegated
forwarder. Existing unrelated companion members survive. A direct existing
`apply` suppresses only the generated materializer, while a direct existing
forwarding name suppresses only that forwarder. This qualification is limited
to this pair and their common one-invariant-unbounded-parameter,
one-eligible-method family; it does not admit arbitrary handlers, other
annotation stacks, broader target profiles, or overload-aware conflict
semantics.

The common bounded `Add` family also compiles with `@apply` and `@aux` in
either source order and exposes both the contextual materializer and the
`Aux` alias. Direct `apply` and direct type `Aux` conflicts are independent.
This bounded pair is qualified because both handlers now consume the same
production source-recognition result before feature-specific lowering and
placement. Within this exact common target envelope, there is therefore no
source-shape state where the first handler succeeds source decoding and the
second fails source decoding. Remaining lowering, placement, lifecycle, and
unexpected-failure classes retain the Macro-Paradise coordinator's existing
rollback contract, independently exercised by the real `@apply` +
`@delegated` late-rejection regression. This is not a claim about arbitrary
annotation composition, target profiles, failures, or semantic bound equality.

For a plain zero-parameter trait, the first supported `@self` slice is:

```scala
import com.github.dmytromitin.auxify.macros.self

@self
trait Nat:
  type Existing = String
```

It conceptually adds a collision-safe self alias and the default bounded member:

```scala
trait Nat { self =>
  type Self >: self.type <: Nat { type Self = self.Self }
  type Existing = String
}
```

An existing named self alias is retained. For an anonymous self, direct term
members named `self`, `self$1`, and so on are skipped deterministically when
selecting the generated alias. A direct existing type member named `Self` is a
controlled conflict. This first slice intentionally exposes no annotation
arguments: historical `lowerBound` / `fBound` options, generic traits, and
class or object targets remain later parity work.

## Using supported annotations from an sbt project

The current external-consumer proof covers exact Scala 3.3.8 and Scala 3.8.4
on JDK 25. All of the artifacts below are development artifacts: AUXify,
Macro-Paradise 0.1.1-SNAPSHOT, and the required Quasiquotes integration are not
claimed to be available from a remote artifact repository.

### Preferred development setup with the Macro-Paradise sbt plugin

From an AUXify checkout, prepare the pinned Macro-Paradise compiler/API and
Quasiquotes artifacts, publish the source-built Macro-Paradise sbt integration,
and publish the AUXify marker and handler to the local Ivy repository:

```sh
AUXIFY_SCALA_VERSION=3.8.4 ./scripts/prepare-ci-dependencies.sh
sbt -Dauxify.scalaVersion=3.8.4 -batch "macroAnnotations/publishLocal" "macroHandlers/publishLocal"
```

Use `3.3.8` consistently in both selectors to prepare the other qualified
line. Omitting both selectors retains the default Scala 3.8.4 behavior.

Despite its CI-oriented name, `prepare-ci-dependencies.sh` is also the
checked-in, reproducible helper for this local-development setup. It accepts
exactly `AUXIFY_SCALA_VERSION=3.3.8` or `AUXIFY_SCALA_VERSION=3.8.4`, clones the
exact pinned public Macro-Paradise and Quasiquotes revisions into disposable
temporary directories, verifies those revisions, and publishes only the peer
artifacts that AUXify currently consumes into the local repository.

Dependency preparation also invokes `prepare-macroparadise-sbt-integration.sh`,
which separately clones the exact committed source containing Macro-Paradise's
generic sbt integration, verifies its sbt 1.x/Scala 2.12 dependency and
local-publication policy, and runs `publishLocal` inside that source build. The
integration is currently source-built and unreleased: the local-development
step is required because no remote `sbt-macroparadise` coordinate is claimed.

The compiler-product source pin intentionally remains independent of the newer
sbt-integration source pin. The sbt plugin selects Macro-Paradise compiler/API
version `0.1.1-SNAPSHOT` through exact full-cross modules; the verified mixed
setup does not require an opportunistic compiler-product source upgrade.

The two sbt tasks then publish AUXify's own modules locally:

- `macroAnnotations/publishLocal` publishes the marker module imported by user
  source;
- `macroHandlers/publishLocal` publishes the precompiled handler together with
  dependency metadata that lets sbt resolve its transitive classpath.

All of these operations are local-only publication. They do not publish to
Maven Central or another remote repository. This preparation is development-era
machinery while the integrated artifacts are not all available as stable remote
releases.

Pin sbt in the external project's `project/build.properties`:

```text
sbt.version=1.12.15
```

Enable the locally published generic plugin in `project/plugins.sbt`:

```scala
addSbtPlugin(
  "com.github.dmytromitin" % "sbt-macroparadise" % "0.1.1-SNAPSHOT"
)
```

The preferred external `build.sbt` is:

```scala
enablePlugins(macroparadise.sbt.MacroParadisePrecompiledPlugin)

scalaVersion := "3.8.4"
val auxifyVersion = "0.1.0-SNAPSHOT"

macroParadiseMarkerModules := Seq(
  "com.github.dmytromitin" %% "auxify-scala3-macro-annotations" % auxifyVersion
)

macroParadiseHandlerModules := Seq(
  ("com.github.dmytromitin" % "auxify-scala3-macro-handlers" % auxifyVersion)
    .cross(CrossVersion.full)
)
```

### What the Macro-Paradise sbt plugin does

The two module settings preserve the same three roles as the original manual
proof. The marker modules become ordinary consumer dependencies. The plugin
selects exactly one full-cross Macro-Paradise compiler plugin, resolves handler
modules and their complete transitive closure through a hidden configuration,
and derives the fail-closed plugin requirement, platform-correct
`handlerClasspath`, and content-sensitive `externalArtifactIdentity` compiler
options. The identity covers the explicit marker artifacts and complete ordered
effective handler classpath; it is generated build-invalidation input, not an
authentication or security claim.

The plugin is generic Macro-Paradise tooling. AUXify does not currently provide
an AUXify-specific sbt plugin, and the external build should not duplicate the
derived `scalacOptions` manually while this plugin is enabled.

The compiler product version/module settings and the AUXify marker and handler
module settings are ordinary build inputs and may be overridden within the
generic plugin's supported contract. Advanced builds may also append labelled
entries through `macroParadiseAdditionalHandlerClasspath`. In contrast,
`macroParadiseExternalArtifactIdentity` is derived output in supported
AutoPlugin mode and is not an arbitrary identity setting to replace. Builds
that need complete ownership of these mechanics should avoid or disable the
AutoPlugin and use the manual path below.

### Manual wiring / escape hatch and architecture reference

When the source-built sbt plugin is unavailable or deliberately disabled, the
following explicit setup remains the supported manual escape hatch. It is also
the executable reference for the marker/compiler-plugin/handler/runtime and
Zinc boundaries hidden by the preferred plugin-backed setup. Manual users need
`prepare-ci-dependencies.sh` plus the two AUXify `publishLocal` tasks above, but
do not need to prepare `sbt-macroparadise`.

Use this `build.sbt` in the separate project:

```scala
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

ThisBuild / scalaVersion := "3.8.4"
val auxifyVersion = "0.1.0-SNAPSHOT"

lazy val AuxifyHandler = config("auxifyHandler").hide

def sha256(bytes: Array[Byte]): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString

def externalArtifactIdentity(marker: File, handler: File): String = {
  val markerHash = sha256(Files.readAllBytes(marker.toPath))
  val handlerHash = sha256(Files.readAllBytes(handler.toPath))
  sha256(s"marker=$markerHash\nhandler=$handlerHash\n".getBytes(StandardCharsets.UTF_8))
}

lazy val root = project
  .in(file("."))
  .configs(AuxifyHandler)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.dmytromitin" %% "auxify-scala3-macro-annotations" % auxifyVersion,
      compilerPlugin(
        ("com.github.dmytromitin" % "macroparadise-scala3-plugin" % "0.1.1-SNAPSHOT")
          .cross(CrossVersion.full)
      ),
      (("com.github.dmytromitin" % "auxify-scala3-macro-handlers" % auxifyVersion)
        .cross(CrossVersion.full)) % AuxifyHandler
    ),
    Compile / scalacOptions ++= {
      val handlerClasspath = (Compile / update).value
        .select(configurationFilter(AuxifyHandler.name))
        .map(_.getCanonicalFile)
        .distinct
      val markerJar = (Compile / dependencyClasspath).value.files
        .map(_.getCanonicalFile)
        .find(_.getName.startsWith("auxify-scala3-macro-annotations_3"))
        .getOrElse(sys.error("AUXify marker JAR was not resolved"))
      val handlerJar = handlerClasspath
        .find(_.getName.startsWith("auxify-scala3-macro-handlers_3.8.4"))
        .getOrElse(sys.error("AUXify handler JAR was not resolved"))

      Seq(
        "-Xplugin-require:macroparadise",
        s"-P:macroparadise:handlerClasspath=${handlerClasspath.map(_.getPath).mkString(File.pathSeparator)}",
        s"-P:macroparadise:externalArtifactIdentity=sha256:${externalArtifactIdentity(markerJar, handlerJar)}"
      )
    }
  )
```

### How the build wiring works

#### Three artifact roles

The dependencies are separate because compilation uses three different
classpaths:

- `auxify-scala3-macro-annotations_3` is an ordinary binary-crossed compile
  dependency. User source imports
  `com.github.dmytromitin.auxify.macros.apply` from this JAR, which contains the
  annotation marker and its runtime-retained handler metadata. Its packaged
  class exposes no Dotty compiler type: it extends Scala's annotation base and
  carries the Java `@expander` metadata descriptor, so ordinary Scala-3 binary
  crossing remains appropriate.
- `macroparadise-scala3-plugin_3.8.4` is loaded by the Scala compiler through
  `compilerPlugin(...)`. `CrossVersion.full` is required because the compiler
  plugin is tied to the exact Scala 3 compiler line, not only Scala's binary
  version.
- `auxify-scala3-macro-handlers_3.8.4` contains AUXify's exact-full-cross
  precompiled annotation-handler implementation. Its public JVM descriptors
  and implementation directly reference Dotty `Context`/raw trees, the exact
  Macro-Paradise handler API, and exact Quasiquotes lowering. Separate compiler
  lines therefore need separate module coordinates rather than overwriting one
  `_3` module/version. Macro-Paradise needs this artifact while compiling
  annotated source, but the application does not use it as an ordinary compile
  or runtime dependency.

#### The hidden handler dependency configuration

`lazy val AuxifyHandler = config("auxifyHandler").hide` creates an sbt
dependency `Configuration` used by the build definition. It is not application
configuration visible to Scala source, and it is not a Macro-Paradise API
object. The dedicated configuration lets sbt resolve the handler JAR and its
transitive dependency closure without putting that closure into the ordinary
application `Compile` or `Runtime` dependency graph.

`.configs(AuxifyHandler)` attaches the custom configuration to this project,
and `% AuxifyHandler` places the full-cross AUXify handler in it rather than in ordinary
`Compile`. `.hide` keeps the configuration out of normal user-facing
configuration delegation and aggregation surfaces. It does not encrypt,
sandbox, shade, or otherwise transform any JAR.

#### Resolving and loading the handler

`Compile / update` is sbt's resolved dependency metadata, produced through its
Ivy/Coursier resolution machinery. Selecting
`configurationFilter(AuxifyHandler.name)` returns the files resolved for the
handler configuration, including transitive dependencies. The complete
classpath matters because the AUXify handler uses the Macro-Paradise plugin
API, Quasiquotes exact and neutral artifacts, Scalameta, and Scala
compiler/runtime artifacts. Macro-Paradise's dedicated handler child
classloader needs that closure explicitly; it does not infer it from the
application's dependencies.

The `-P:macroparadise:handlerClasspath=...` option gives that resolved path list
to the compiler plugin so it can load the metadata-selected `ApplyHandler` and
its dependencies. This is distinct from Scala's ordinary source compile
classpath, the `-Xplugin` classpath installed by `compilerPlugin(...)`, and the
application runtime classpath. A path list is platform-specific, so the snippet
uses `File.pathSeparator` instead of hard-coding `:`.

`-Xplugin-require:macroparadise` makes compilation fail when the expected
Macro-Paradise plugin is not loaded, instead of silently compiling under a
different assumption.

#### Packaged-artifact identity and Zinc

The snippet finds the packaged marker in `Compile / dependencyClasspath` and
the packaged handler in the hidden handler classpath. Those are the two
concrete inputs to `externalArtifactIdentity`. Matching these known AUXify
module filename prefixes is the currently verified wiring, not a general sbt
best practice; hiding these lookups is a candidate for future build tooling.

The SHA-256 helper hashes the packaged marker and handler bytes into one
deterministic compiler-option token. Macro-Paradise does not interpret or
validate the digest as integrity or security evidence. Its purpose is build
invalidation: when locally republished SNAPSHOT bytes change without changing
their artifact version or stable path, the compiler option changes and Zinc
recompiles affected consumers.

For a first clean compile, the required Macro-Paradise options are
`-Xplugin-require:macroparadise` and `handlerClasspath`.
`externalArtifactIdentity` is recommended for incremental development against
repeatedly republished marker/handler SNAPSHOTs, but is not required merely for
that first clean build. A clean-build-only setup can omit the helper, the
`markerJar`/`handlerJar` lookups, and the identity option together.

After compilation, the generated result is ordinary Scala code. On both
qualified compiler lines, the verified external consumer runs without the
selected exact-line `auxify-scala3-macro-handlers_<exact-scala>` artifact on
`Runtime / fullClasspath`:
the handler is a compilation-time transformation implementation, not an
application service.

### Remaining build-tool ergonomics

The generic Macro-Paradise sbt plugin now supplies the preferred convenience
path while the explicit block above preserves the real
marker/compiler-plugin/handler/runtime and Zinc reference contract. A separate
AUXify sbt plugin is not currently required merely to wrap the two AUXify
coordinates. Reconsider one only if future evidence produces meaningful
AUXify-owned build policy—such as multiple handler bundles, feature selection,
cross-version coordination, or migration tooling—that cannot be expressed
cleanly through the generic settings. The peer has qualified its source-built
plugin in persistent sbt BSP sessions for exact Scala 3.3.8 and 3.8.4, but
stable remote plugin coordinates and release-grade dependency availability
remain future work. AUXify's own bounded development proof covers both exact
compiler lines with JDK 25.

For example, `src/main/scala/ShowApp.scala` can contain:

```scala
import com.github.dmytromitin.auxify.macros.{apply, delegated}

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[String] with
    def show(a: String): String = a

@delegated
trait Render[A]:
  def render(a: A): String

object Render:
  given Render[Int] with
    def render(a: Int): String = a.toString

object ShowApp:
  def main(args: Array[String]): Unit =
    println(Show[String].show("external"))
    println(Render.render(42))
```

Run it with `sbt -batch run`.

The proven milestone creates a missing companion, or preserves an existing
companion and adds the materializer when it has no direct member named
`apply`. An existing direct `apply` is preserved and is not duplicated.

The implementation depends on the experimental Scala 3 Macro-Paradise
compiler plugin and quasiquotes libraries. These dependencies are currently
unreleased for this integration: preparing them through local publication is
a development-only step. This README makes no claim that the required AUXify,
Macro-Paradise, or quasiquotes artifacts are available from a remote artifact
repository.

The verified `@apply` target remains deliberately narrow: a top-level,
non-sealed ordinary trait with no constructor or value parameters, using
either the one-invariant-unbounded-parameter simple shape or the exact
two-common-simple-upper-bound / one-abstract-result-member full shape described
above. This milestone does not claim arbitrary type-class derivation or full
historical `@apply` parity.

The verified `@delegated` target is likewise only the one-unbounded-parameter,
one-public-abstract-method shape documented above; the external example does
not imply full historical `@delegated` parity.
