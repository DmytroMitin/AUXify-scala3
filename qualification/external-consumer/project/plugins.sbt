addSbtPlugin(
  "com.github.dmytromitin" % "sbt-macroparadise" %
    sys.props.getOrElse("macroparadise.version", "0.1.1-SNAPSHOT")
)
