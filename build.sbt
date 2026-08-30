ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "com.github.dmytromitin"
ThisBuild / version := "0.1.0-SNAPSHOT"

val macroParadiseVersion = "0.1.1-SNAPSHOT"
val quasiquotesVersion = "0.3.0-SNAPSHOT"
val scalaMetaVersion = "4.17.3"
val munitVersion = "1.0.4"

lazy val verifyPublicModuleCoordinates =
  taskKey[Unit]("Verify public AUXify module names and cross-version policy")

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
  .settings(
    name := "AUXify Scala 3 Macro Annotations",
    moduleName := "auxify-scala3-macro-annotations",
    crossVersion := CrossVersion.binary,
    libraryDependencies += macroParadiseApi
  )

lazy val macroHandlers = project
  .in(file("macro-handlers"))
  .settings(
    name := "AUXify Scala 3 Macro Handlers",
    moduleName := "auxify-scala3-macro-handlers",
    crossVersion := CrossVersion.full,
    libraryDependencies ++= Seq(
      macroParadiseApi,
      quasiquotesDottyInternal,
      "org.scalameta" %% "scalameta" % scalaMetaVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val consumerSettings = Seq(
  libraryDependencies += compilerPlugin(macroParadisePlugin),
  Compile / scalacOptions ++= {
    val handlerJar = (macroHandlers / Compile / packageBin).value
    val handlerDependencies =
      (macroHandlers / Compile / dependencyClasspath).value.files
    val handlerClasspath =
      (handlerJar +: handlerDependencies).distinct
    Seq(
      "-Xplugin-require:macroparadise",
      s"-P:macroparadise:handlerClasspath=${handlerClasspath.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)}"
    )
  }
)

lazy val integrationTests = project
  .in(file("integration-tests"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)
  .settings(
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test
  )

lazy val negativeUnsupported = project
  .in(file("negative-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeFullUnsupported = project
  .in(file("negative-full-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeSelfConflict = project
  .in(file("negative-self-conflict"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeSelfUnsupported = project
  .in(file("negative-self-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val root = project
  .in(file("."))
  .aggregate(macroAnnotations, macroHandlers, integrationTests)
  .settings(
    publish / skip := true,
    verifyPublicModuleCoordinates := {
      val markerModule = (macroAnnotations / moduleName).value
      val handlerModule = (macroHandlers / moduleName).value
      val markerCross = (macroAnnotations / crossVersion).value
      val handlerCross = (macroHandlers / crossVersion).value
      val markerArtifact =
        (macroAnnotations / Compile / packageBin / artifactPath).value.getName
      val handlerArtifact =
        (macroHandlers / Compile / packageBin / artifactPath).value.getName

      require(
        markerModule == "auxify-scala3-macro-annotations",
        s"unexpected public marker module: $markerModule"
      )
      require(
        handlerModule == "auxify-scala3-macro-handlers",
        s"unexpected public handler module: $handlerModule"
      )
      require(
        markerCross == CrossVersion.binary,
        s"marker must remain Scala-3 binary cross, found $markerCross"
      )
      require(
        handlerCross == CrossVersion.full,
        s"handler must use exact full cross, found $handlerCross"
      )
      require(
        markerArtifact.startsWith("auxify-scala3-macro-annotations_3-"),
        s"unexpected public marker artifact: $markerArtifact"
      )
      require(
        handlerArtifact.startsWith("auxify-scala3-macro-handlers_3.8.4-"),
        s"unexpected public handler artifact: $handlerArtifact"
      )
      require(
        !Seq(markerModule, handlerModule).exists(Set("macroannotations", "macrohandlers")),
        "old generic AUXify modules remain configured"
      )
      streams.value.log.info(
        s"AUXIFY_PUBLIC_MODULE_COORDINATES_PASS marker=$markerArtifact handler=$handlerArtifact"
      )
    }
  )
