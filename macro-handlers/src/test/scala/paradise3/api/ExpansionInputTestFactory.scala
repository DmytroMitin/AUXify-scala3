package paradise3.api

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.Context

object ExpansionInputTestFactory:
  def apply(
      annotationName: String,
      primary: untpd.TypeDef,
      companion: Option[untpd.ModuleDef],
      occupiedDefinitionNames: Set[String],
      currentAnnotation: Option[untpd.Tree]
  )(using Context): ExpansionInput =
    new ExpansionInput(
      ExpansionTarget.fromTree(primary).fold(
        diagnostic => throw new IllegalArgumentException(diagnostic.message),
        identity
      ),
      companion.map(ExpansionTarget.Object.apply),
      new ExpansionContainerContext(occupiedDefinitionNames),
      currentAnnotation.getOrElse(untpd.EmptyTree),
      List(annotationName)
    )
