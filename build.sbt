ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "com.github.dmytromitin"
ThisBuild / version := "0.1.0-SNAPSHOT"

val macroParadiseVersion = "0.1.0"
val quasiquotesVersion = "0.2.0"
val scalaMetaVersion = "4.17.3"
val munitVersion = "1.0.4"

val macroParadiseApi =
  ("com.github.dmytromitin" % "macroparadise-scala3-plugin-api" % macroParadiseVersion)
    .cross(CrossVersion.full)

val macroParadisePlugin =
  ("com.github.dmytromitin" % "macroparadise-scala3-plugin" % macroParadiseVersion)
    .cross(CrossVersion.full)

val quasiquotesDottyInternal =
  ("com.github.dmytromitin" % "quasiquotes-scala3-dotty-internal" % quasiquotesVersion)
    .cross(CrossVersion.full)

lazy val macroAnnotations = project
  .in(file("macro-annotations"))
  .settings(libraryDependencies += macroParadiseApi)

lazy val macroHandlers = project
  .in(file("macro-handlers"))
  .settings(
    libraryDependencies ++= Seq(
      macroParadiseApi,
      quasiquotesDottyInternal,
      "org.scalameta" %% "scalameta" % scalaMetaVersion
    )
  )

lazy val consumerSettings = Seq(
  libraryDependencies += compilerPlugin(macroParadisePlugin),
  Compile / scalacOptions ++= {
    val handlerJar = (macroHandlers / Compile / packageBin).value
    Seq(
      "-Xplugin-require:macroparadise",
      s"-P:macroparadise:handlerClasspath=${handlerJar.getAbsolutePath}"
    )
  }
)

lazy val integrationTests = project
  .in(file("integration-tests"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeUnsupported = project
  .in(file("negative-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val root = project
  .in(file("."))
  .aggregate(macroAnnotations, macroHandlers, integrationTests)
  .settings(publish / skip := true)
