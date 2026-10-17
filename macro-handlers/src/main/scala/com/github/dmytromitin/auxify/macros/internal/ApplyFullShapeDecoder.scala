package com.github.dmytromitin.auxify.macros.internal

import paradise3.api.{
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionDiagnostic
}

private[internal] object ApplyFullShapeDecoder:
  def decode(
      typeClassName: String,
      bodyView: ExpansionTargetBodyView,
      view: ExpansionTargetTypeStructureView
  ): Either[ExpansionDiagnostic, ApplyDefinitionBuilder.FullShape] =
    BoundedResultTypeClassShapeDecoder
      .decode(typeClassName, bodyView, view)
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
