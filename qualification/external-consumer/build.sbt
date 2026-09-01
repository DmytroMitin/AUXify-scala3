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

val auxifyVersion =
  sys.props.getOrElse("auxify.version", "0.1.0-SNAPSHOT")
val macroParadiseVersion =
  sys.props.getOrElse("macroparadise.version", "0.1.1-SNAPSHOT")
val releaseConsumerMode =
  sys.props.get("auxify.releaseConsumer").contains("true")

macroParadiseCompilerProductVersion := macroParadiseVersion

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
  val markerFilePrefix = "auxify-scala3-macro-annotations_3"
  val handlerFilePrefix =
    s"auxify-scala3-macro-handlers_${scalaVersion.value}"
  val resolved = update.value.allModules

  require(
    runtimeFiles.exists(file =>
      file.getName.startsWith(markerFilePrefix) && file.getName.endsWith(".jar")
    ),
    "AUXify marker is absent from the external runtime classpath"
  )
  require(
    !runtimeFiles.exists(file =>
      file.getName.startsWith(handlerFilePrefix) && file.getName.endsWith(".jar")
    ),
    s"AUXify handler leaked onto the external runtime classpath: $handlerFilePrefix"
  )
  require(
    compileOptions.count(_ == "-Xplugin-require:macroparadise") == 1,
    "external compile must require Macro-Paradise exactly once"
  )
  require(
    compileOptions.count(option =>
      option.startsWith("-P:macroparadise:handlerClasspath=") &&
        option.contains(handlerFilePrefix)
    ) == 1,
    "external compile must carry one exact-line AUXify handler classpath"
  )

  if (releaseConsumerMode) {
    require(auxifyVersion == "0.1.0", s"release consumer requires AUXify 0.1.0, found $auxifyVersion")
    require(
      macroParadiseVersion == "0.1.1",
      s"release consumer requires Macro-Paradise 0.1.1, found $macroParadiseVersion"
    )
    require(
      sys.props.get("sbt.override.build.repos").contains("true") &&
        sys.props.get("sbt.repository.config").exists(_.nonEmpty),
      "release consumer requires an explicit isolated repository configuration"
    )
    val snapshots = resolved.filter(_.revision.endsWith("-SNAPSHOT"))
    require(
      snapshots.isEmpty,
      s"release consumer resolved SNAPSHOT modules: ${snapshots.map(module => s"${module.organization}:${module.name}:${module.revision}").mkString(", ")}"
    )
    require(
      resolved.forall(module =>
        !module.organization.contains("control") && !module.name.contains("control")
      ),
      "release consumer resolved a private/control module"
    )
  }

  streams.value.log.info(
    s"AUXIFY_SCALA3_EXTERNAL_POLICY_PASS scala=${scalaVersion.value} release=$releaseConsumerMode runtime_handler=false"
  )
}
