import macroparadise.sbt.MacroParadisePrecompiledPlugin.autoImport._

val selectedScalaVersion =
  sys.props.getOrElse(
    "auxify.scalaVersion",
    sys.error("external qualification requires -Dauxify.scalaVersion")
  )

enablePlugins(macroparadise.sbt.MacroParadisePrecompiledPlugin)

scalaVersion := {
  require(
    Set("3.3.8", "3.8.4")(selectedScalaVersion),
    s"unsupported exact external-consumer Scala version: $selectedScalaVersion"
  )
  selectedScalaVersion
}

val auxifyVersion = "0.1.0-SNAPSHOT"

macroParadiseMarkerModules := Seq(
  "com.github.dmytromitin" %% "auxify-scala3-macro-annotations" % auxifyVersion
)

macroParadiseHandlerModules := Seq(
  ("com.github.dmytromitin" % "auxify-scala3-macro-handlers" % auxifyVersion)
    .cross(CrossVersion.full)
)

lazy val verifyExternalPolicy = taskKey[Unit](
  "Verify the standalone plugin-backed AUXify compile/runtime boundary"
)

verifyExternalPolicy := {
  val runtimeFiles = (Runtime / fullClasspath).value.files
  val compileOptions = (Compile / scalacOptions).value
  val markerFileName = "auxify-scala3-macro-annotations_3.jar"
  val handlerFileName =
    s"auxify-scala3-macro-handlers_${scalaVersion.value}.jar"

  require(
    runtimeFiles.exists(_.getName == markerFileName),
    "AUXify marker is absent from the external runtime classpath"
  )
  require(
    !runtimeFiles.exists(_.getName == handlerFileName),
    s"AUXify handler leaked onto the external runtime classpath: $handlerFileName"
  )
  require(
    compileOptions.count(_ == "-Xplugin-require:macroparadise") == 1,
    "external compile must require Macro-Paradise exactly once"
  )
  require(
    compileOptions.count(option =>
      option.startsWith("-P:macroparadise:handlerClasspath=") &&
        option.contains(handlerFileName)
    ) == 1,
    "external compile must carry one exact-line AUXify handler classpath"
  )

  streams.value.log.info(
    s"AUXIFY_SCALA3_EXTERNAL_POLICY_PASS scala=${scalaVersion.value} runtime_handler=false"
  )
}
