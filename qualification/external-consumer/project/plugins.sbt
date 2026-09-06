addSbtPlugin(
  "com.github.dmytromitin" % "sbt-macroparadise" %
    sys.props.getOrElse("macroparadise.sbt.version", "0.1.1")
)
