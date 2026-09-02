package com.github.dmytromitin.auxify.macros.internal

import paradise3.api.{
  AnnotatedClassTypeStructureView,
  ExpansionDiagnostic
}

private[internal] object ApplyFullShapeDecoder:
  def decode(
      typeClassName: String,
      view: AnnotatedClassTypeStructureView
  ): Either[ExpansionDiagnostic, ApplyDefinitionBuilder.FullShape] =
    BoundedResultTypeClassShapeDecoder
      .decode(typeClassName, view)
      .left
      .map: rejection =>
        ExpansionDiagnostic(
          s"unsupported full @apply source shape for `$typeClassName`: ${rejection.reason}",
          rejection.pos
        )
      .map: shape =>
        ApplyDefinitionBuilder.FullShape(
          typeClassName = shape.typeClassName,
          firstTypeParameterName = shape.firstTypeParameterName,
          secondTypeParameterName = shape.secondTypeParameterName,
          upperBoundTypeName = shape.upperBoundTypeName,
          resultTypeMemberName = shape.resultTypeMemberName
        )
