package com.github.dmytromitin.auxify.macros.internal

import paradise3.api.{
  AnnotatedClassTypeStructureView,
  ExpansionDiagnostic
}

import scala.annotation.tailrec

private[internal] object AuxSourceShapeDecoder:
  final case class Shape(
      typeClassName: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundTypeName: String,
      resultTypeMemberName: String,
      generatedResultParameterName: String
  )

  def decode(
      typeClassName: String,
      view: AnnotatedClassTypeStructureView
  ): Either[ExpansionDiagnostic, Shape] =
    BoundedResultTypeClassShapeDecoder
      .decode(typeClassName, view)
      .left
      .map: rejection =>
        ExpansionDiagnostic(
          s"unsupported @aux source shape for `$typeClassName`: ${rejection.reason}",
          rejection.pos
        )
      .map: shape =>
        Shape(
          typeClassName = shape.typeClassName,
          firstTypeParameterName = shape.firstTypeParameterName,
          secondTypeParameterName = shape.secondTypeParameterName,
          upperBoundTypeName = shape.upperBoundTypeName,
          resultTypeMemberName = shape.resultTypeMemberName,
          generatedResultParameterName = freshResultParameterName(
            shape.resultTypeMemberName,
            view.typeParameters.map(_.name).toSet ++ view.directTypeMembers.map(_.name)
          )
        )

  private def freshResultParameterName(
      resultTypeMemberName: String,
      occupiedNames: Set[String]
  ): String =
    @tailrec
    def loop(index: Int): String =
      val candidate = s"$resultTypeMemberName$index"
      if occupiedNames.contains(candidate) then loop(index + 1)
      else candidate

    loop(0)
