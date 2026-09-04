val supportedScalaVersions = Set("3.3.8", "3.8.4", "3.9.0")
val selectedScalaVersion =
  sys.props.getOrElse("auxify.scalaVersion", "3.8.4")

ThisBuild / scalaVersion := {
  require(
    supportedScalaVersions(selectedScalaVersion),
    s"unsupported exact AUXify Scala version: $selectedScalaVersion; expected one of ${supportedScalaVersions.toSeq.sorted.mkString(", ")}"
  )
  selectedScalaVersion
}
ThisBuild / organization := "com.github.dmytromitin"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organizationName := "Dmytro Mitin"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publish / skip := true
ThisBuild / publishMavenStyle := true
ThisBuild / Compile / packageSrc / publishArtifact := true
ThisBuild / Compile / packageDoc / publishArtifact := true
ThisBuild / Test / publishArtifact := false
ThisBuild / licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
)
ThisBuild / homepage := Some(url("https://github.com/DmytroMitin/AUXify-scala3"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/DmytroMitin/AUXify-scala3"),
    "scm:git:https://github.com/DmytroMitin/AUXify-scala3.git",
    Some("scm:git:ssh://git@github.com:DmytroMitin/AUXify-scala3.git")
  )
)
ThisBuild / developers := List(
  Developer(
    "DmytroMitin",
    "Dmytro Mitin",
    "dmitin3@gmail.com",
    url("https://github.com/DmytroMitin")
  )
)
ThisBuild / pomIncludeRepository := (_ => false)

val macroParadiseVersion =
  sys.props.getOrElse("macroparadise.version", "0.1.1-SNAPSHOT")
val quasiquotesVersion =
  sys.props.getOrElse("quasiquotes.version", "0.3.0-SNAPSHOT")
val scalaMetaVersion = "4.17.3"
val munitVersion = "1.0.4"

lazy val publicPublicationSettings = Seq(
  publish / skip := false,
  publishTo := sys.props.get("auxify.releaseSimulationRepository").map { path =>
    Resolver.file("auxify-release-simulation", file(path))(Resolver.mavenStylePatterns)
  },
  Compile / packageBin / mappings +=
    baseDirectory.value.getParentFile / "LICENSE" -> "META-INF/LICENSE",
  Compile / packageSrc / mappings +=
    baseDirectory.value.getParentFile / "LICENSE" -> "META-INF/LICENSE",
  Compile / packageDoc / mappings +=
    baseDirectory.value.getParentFile / "LICENSE" -> "META-INF/LICENSE"
)

lazy val verifyPublicModuleCoordinates =
  taskKey[Unit]("Verify public AUXify module names and cross-version policy")

lazy val verifyReleaseReadiness =
  taskKey[Unit]("Verify the bounded AUXify Maven Central artifact and POM contract")

val macroParadiseApi =
  ("com.github.dmytromitin" % "macroparadise-scala3-plugin-api" % macroParadiseVersion)
    .cross(CrossVersion.full)

lazy val MarkerBuild = config("marker-build").hide

val macroParadisePlugin =
  ("com.github.dmytromitin" % "macroparadise-scala3-plugin" % macroParadiseVersion)
    .cross(CrossVersion.full)

val quasiquotesDottyInternal =
  ("com.github.dmytromitin" % "quasiquotes-scala3-dotty-internal" % quasiquotesVersion)
    .cross(CrossVersion.full)

lazy val macroAnnotations = project
  .in(file("macro-annotations"))
  .configs(MarkerBuild)
  .settings(publicPublicationSettings)
  .settings(
    name := "AUXify Scala 3 Macro Annotations",
    description := "Scala 3 annotation markers for the bounded AUXify macro-annotation slices",
    moduleName := "auxify-scala3-macro-annotations",
    crossVersion := CrossVersion.binary,
    libraryDependencies += macroParadiseApi % MarkerBuild,
    Compile / unmanagedJars ++=
      update.value
        .select(configurationFilter(MarkerBuild.name))
        .map(Attributed.blank)
  )

lazy val macroHandlers = project
  .in(file("macro-handlers"))
  .settings(publicPublicationSettings)
  .settings(
    name := "AUXify Scala 3 Macro Handlers",
    description := "Exact-Scala-version precompiled handlers for the bounded AUXify macro-annotation slices",
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

lazy val negativeDelegatedUnsupported = project
  .in(file("negative-delegated-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeCompositionLateRejection = project
  .in(file("negative-composition-late-rejection"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeApplyInstanceComposition = project
  .in(file("negative-apply-instance-composition"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeAuxUnsupported = project
  .in(file("negative-aux-unsupported"))
  .dependsOn(macroAnnotations)
  .settings(consumerSettings)

lazy val negativeInstanceUnsupported = project
  .in(file("negative-instance-unsupported"))
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
        handlerArtifact.startsWith(s"auxify-scala3-macro-handlers_${scalaVersion.value}-"),
        s"unexpected public handler artifact: $handlerArtifact"
      )
      require(
        !Seq(markerModule, handlerModule).exists(Set("macroannotations", "macrohandlers")),
        "old generic AUXify modules remain configured"
      )
      streams.value.log.info(
        s"AUXIFY_PUBLIC_MODULE_COORDINATES_PASS marker=$markerArtifact handler=$handlerArtifact"
      )
    },
    verifyReleaseReadiness := {
      val intendedPublicModuleCount = 1 + supportedScalaVersions.size
      val primaryArtifactCount = intendedPublicModuleCount * 4
      val expectedReleaseInventoryFiles = primaryArtifactCount * 6
      require(
        intendedPublicModuleCount == 4 && expectedReleaseInventoryFiles == 96,
        s"unexpected three-line release inventory: modules=$intendedPublicModuleCount files=$expectedReleaseInventoryFiles"
      )
      val internalProjects = Vector(
        "root" -> (publish / skip).value,
        "integrationTests" -> (integrationTests / publish / skip).value,
        "negativeUnsupported" -> (negativeUnsupported / publish / skip).value,
        "negativeFullUnsupported" -> (negativeFullUnsupported / publish / skip).value,
        "negativeSelfConflict" -> (negativeSelfConflict / publish / skip).value,
        "negativeSelfUnsupported" -> (negativeSelfUnsupported / publish / skip).value,
        "negativeDelegatedUnsupported" -> (negativeDelegatedUnsupported / publish / skip).value,
        "negativeCompositionLateRejection" -> (negativeCompositionLateRejection / publish / skip).value,
        "negativeApplyInstanceComposition" -> (negativeApplyInstanceComposition / publish / skip).value,
        "negativeAuxUnsupported" -> (negativeAuxUnsupported / publish / skip).value,
        "negativeInstanceUnsupported" -> (negativeInstanceUnsupported / publish / skip).value
      )
      val publicProjects = Vector(
        "macroAnnotations" -> (macroAnnotations / publish / skip).value,
        "macroHandlers" -> (macroHandlers / publish / skip).value
      )

      require(
        internalProjects.forall(_._2),
        s"internal AUXify publication enabled: ${internalProjects.filterNot(_._2).map(_._1).mkString(", ")}"
      )
      require(
        publicProjects.forall(!_._2),
        s"intended AUXify artifact remains skipped: ${publicProjects.filter(_._2).map(_._1).mkString(", ")}"
      )
      require(
        (macroAnnotations / credentials).value.isEmpty &&
          (macroHandlers / credentials).value.isEmpty,
        "AUXify build must not configure publication credentials"
      )
      val publishDestinations = Vector(
        (macroAnnotations / publishTo).value,
        (macroHandlers / publishTo).value
      )
      val simulationRepository = sys.props
        .get("auxify.releaseSimulationRepository")
        .map(path => file(path).getCanonicalFile)
      simulationRepository match {
        case None =>
          require(
            publishDestinations.forall(_.isEmpty),
            "normal development state must configure no publication destination"
          )
        case Some(repository) =>
          val tmpRoot = file(sys.props.getOrElse("java.io.tmpdir", "/tmp")).getCanonicalFile
          require(
            repository.toPath.startsWith(tmpRoot.toPath),
            s"release simulation repository must be task-owned under $tmpRoot, found $repository"
          )
          require(
            publishDestinations.forall(_.exists { resolver =>
              resolver.name == "auxify-release-simulation" &&
                resolver.toString.contains(repository.getPath + "/")
            }),
            s"release simulation publish destination does not match $repository"
          )
      }

      val packaged = Vector(
        (macroAnnotations / Compile / packageBin).value,
        (macroAnnotations / Compile / packageSrc).value,
        (macroAnnotations / Compile / packageDoc).value,
        (macroHandlers / Compile / packageBin).value,
        (macroHandlers / Compile / packageSrc).value,
        (macroHandlers / Compile / packageDoc).value
      )
      require(
        packaged.forall(file => file.isFile && file.length > 0L),
        s"missing or empty public artifact: ${packaged.filterNot(file => file.isFile && file.length > 0L).mkString(", ")}"
      )
      def containsLicense(archive: File): Boolean = {
        val jar = new java.util.jar.JarFile(archive)
        try jar.getEntry("META-INF/LICENSE") != null
        finally jar.close()
      }
      require(
        packaged.forall(containsLicense),
        s"public artifact missing META-INF/LICENSE: ${packaged.filterNot(containsLicense).mkString(", ")}"
      )

      val markerPom = IO.read((macroAnnotations / Compile / makePom).value)
      val handlerPom = IO.read((macroHandlers / Compile / makePom).value)
      val metadataTokens = Vector(
        "<name>",
        "<description>",
        "<url>https://github.com/DmytroMitin/AUXify-scala3</url>",
        "<licenses>",
        "<scm>",
        "<developers>",
        "<info.versionScheme>early-semver</info.versionScheme>"
      )
      val missingMetadata = Vector("marker" -> markerPom, "handler" -> handlerPom).flatMap {
        case (label, pom) => metadataTokens.filterNot(pom.contains).map(token => s"$label:$token")
      }
      require(missingMetadata.isEmpty, s"generated POM metadata is incomplete: ${missingMetadata.mkString(", ")}")

      val line = scalaVersion.value
      require(
        !markerPom.contains("macroparadise-scala3-plugin-api_"),
        "binary-cross marker POM must not expose an exact-Scala-version Macro-Paradise API dependency"
      )
      Vector(
        "<artifactId>macroparadise-scala3-plugin-api_" + line + "</artifactId>",
        "<artifactId>quasiquotes-scala3-dotty-internal_" + line + "</artifactId>",
        "<artifactId>scalameta_3</artifactId>",
        "<version>4.17.3</version>"
      ).foreach(token => require(handlerPom.contains(token), s"handler POM missing required dependency token: $token"))
      val handlerPomXml = scala.xml.XML.loadString(handlerPom)
      val munitDependencies = (handlerPomXml \\ "dependency").filter(node =>
        (node \ "artifactId").text == "munit_3"
      )
      require(
        munitDependencies.forall(node => (node \ "scope").text == "test"),
        "MUnit leaked into the handler POM outside test scope"
      )

      val forbiddenPomTokens = Vector(
        "SNAPSHOT-",
        "Project" + "Ref",
        "Root" + "Project",
        "file:/",
        "AUXify-scala3-control",
        "/home/"
      )
      val contaminated = Vector("marker" -> markerPom, "handler" -> handlerPom).flatMap {
        case (label, pom) => forbiddenPomTokens.filter(pom.contains).map(token => s"$label:$token")
      }
      require(contaminated.isEmpty, s"generated POM contamination: ${contaminated.mkString(", ")}")

      if (version.value == "0.1.0") {
        require(macroParadiseVersion == "0.1.1", s"release-shaped build requires Macro-Paradise 0.1.1, found $macroParadiseVersion")
        require(quasiquotesVersion == "0.3.0", s"release-shaped build requires Quasiquotes 0.3.0, found $quasiquotesVersion")
        require(
          !markerPom.contains("SNAPSHOT") && !handlerPom.contains("SNAPSHOT"),
          "release-shaped POMs must contain no SNAPSHOT coordinate"
        )
      }

      streams.value.log.info(
        s"AUXIFY_RELEASE_READINESS_PASS scala=$line public=${publicProjects.map(_._1).mkString(",")} internalSkipped=${internalProjects.size} intendedModules=$intendedPublicModuleCount expectedFiles=$expectedReleaseInventoryFiles simulation=${simulationRepository.nonEmpty} credentials=none"
      )
    }
  )
