# AUXify-scala3

AUXify-scala3 currently provides an experimental first Scala 3 `@apply`
milestone. The current proof is specific to Scala 3.8.4 on JDK 25.

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
reproducible helper for this local-development setup. It checks out the pinned
peer revisions in temporary directories and publishes their required artifacts
locally.

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

The three dependencies have intentionally different roles. The
`macroannotations_3` marker is an ordinary source dependency. The
`macroparadise-scala3-plugin_3.8.4` artifact is the compiler plugin. The
`macrohandlers_3` artifact and all of its transitive dependencies are resolved
in the hidden `auxifyHandler` configuration and passed to Macro-Paradise with
the platform-correct `File.pathSeparator`; the handler implementation is not an
ordinary application runtime dependency.

For a first clean compile, the required Macro-Paradise options are
`-Xplugin-require:macroparadise` and `handlerClasspath`. The
`externalArtifactIdentity` option and its SHA-256 helper are recommended for
incremental builds against repeatedly republished SNAPSHOT marker/handler JARs:
the value makes Zinc notice changed artifact bytes. It is a build input, not a
loading or security check, and can be omitted together with the helper and the
`markerJar`/`handlerJar` lookups for a minimal clean-build-only setup.

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
