# AUXify-scala3

AUXify-scala3 currently provides an experimental first Scala 3 `@apply`
milestone. The current proof is specific to Scala 3.8.4 on JDK 25.

## Related projects

- [quasiquotes-scala3](https://github.com/DmytroMitin/quasiquotes-scala3) —
  neutral and Scala 3 quasiquotes plus exact lowering used by AUXify.
- [macroparadise-scala3](https://github.com/DmytroMitin/macroparadise-scala3) —
  the Scala 3 Macro-Paradise compiler plugin that runs the annotations.
- [AUXify (Scala 2)](https://github.com/DmytroMitin/AUXify) — the original
  Scala 2 implementation whose delivered macro annotations define the main
  parity target.

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

## Using `@apply` from an sbt project

The current external-consumer proof is exact Scala 3.8.4 on JDK 25. All of the
artifacts below are development artifacts: AUXify, Macro-Paradise 0.1.1-SNAPSHOT,
and the required Quasiquotes integration are not claimed to be available from a
remote artifact repository.

From an AUXify checkout, first prepare the pinned Macro-Paradise and Quasiquotes
artifacts and publish the AUXify marker and handler to the local Ivy repository:

```sh
./scripts/prepare-ci-dependencies.sh
sbt -batch "macroAnnotations/publishLocal" "macroHandlers/publishLocal"
```

Despite its CI-oriented name, `prepare-ci-dependencies.sh` is also the checked-in,
reproducible helper for this local-development setup. It clones the exact pinned
public Macro-Paradise and Quasiquotes revisions into disposable temporary
directories, verifies those revisions, and publishes only the peer artifacts
that AUXify currently consumes into the local repository.

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

Use this `build.sbt` in that separate project:

```scala
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

ThisBuild / scalaVersion := "3.8.4"

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
      "com.github.dmytromitin" %% "macroannotations" % "0.1.0-SNAPSHOT",
      compilerPlugin(
        ("com.github.dmytromitin" % "macroparadise-scala3-plugin" % "0.1.1-SNAPSHOT")
          .cross(CrossVersion.full)
      ),
      ("com.github.dmytromitin" %% "macrohandlers" % "0.1.0-SNAPSHOT") % AuxifyHandler
    ),
    Compile / scalacOptions ++= {
      val handlerClasspath = (Compile / update).value
        .select(configurationFilter(AuxifyHandler.name))
        .map(_.getCanonicalFile)
        .distinct
      val markerJar = (Compile / dependencyClasspath).value.files
        .map(_.getCanonicalFile)
        .find(_.getName.startsWith("macroannotations_3"))
        .getOrElse(sys.error("AUXify marker JAR was not resolved"))
      val handlerJar = handlerClasspath
        .find(_.getName.startsWith("macrohandlers_3"))
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

- `macroannotations_3` is an ordinary compile dependency. User source imports
  `com.github.dmytromitin.auxify.macros.apply` from this JAR, which contains the
  annotation marker and its runtime-retained handler metadata.
- `macroparadise-scala3-plugin_3.8.4` is loaded by the Scala compiler through
  `compilerPlugin(...)`. `CrossVersion.full` is required because the compiler
  plugin is tied to the exact Scala 3 compiler line, not only Scala's binary
  version.
- `macrohandlers_3` contains AUXify's precompiled annotation-handler
  implementation. Macro-Paradise needs it while compiling annotated source,
  but the application does not use it as an ordinary compile or runtime
  dependency.

#### The hidden handler dependency configuration

`lazy val AuxifyHandler = config("auxifyHandler").hide` creates an sbt
dependency `Configuration` used by the build definition. It is not application
configuration visible to Scala source, and it is not a Macro-Paradise API
object. The dedicated configuration lets sbt resolve the handler JAR and its
transitive dependency closure without putting that closure into the ordinary
application `Compile` or `Runtime` dependency graph.

`.configs(AuxifyHandler)` attaches the custom configuration to this project,
and `% AuxifyHandler` places `macrohandlers_3` in it rather than in ordinary
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

After compilation, the generated result is ordinary Scala code. The verified
external consumer runs without `macrohandlers_3` on `Runtime / fullClasspath`:
the handler is a compilation-time transformation implementation, not an
application service.

### Future build-tool ergonomics

The current build is intentionally explicit because it proves the real marker,
compiler-plugin, handler-classloader, runtime, and Zinc boundaries. A future
AUXify sbt plugin could reduce the user setup to a small setting or plugin
enablement. Generic external-handler wiring may instead belong in a future
Macro-Paradise sbt plugin; if both exist, AUXify should compose with that
generic support and add only AUXify-specific artifacts and defaults rather than
reimplementing the same mechanics. No such plugin API is promised or
implemented yet.

For example, `src/main/scala/ShowApp.scala` can contain:

```scala
import com.github.dmytromitin.auxify.macros.apply

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[String] with
    def show(a: String): String = a

object ShowApp:
  def main(args: Array[String]): Unit =
    println(Show[String].show("external"))
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

The verified target is deliberately narrow: one top-level, non-sealed,
ordinary trait with exactly one invariant, unbounded type parameter and no
constructor or value parameters. Path-dependent type-member materialization
such as `Add.Out` is not supported or claimed by this milestone; it is reserved
for a later milestone.
