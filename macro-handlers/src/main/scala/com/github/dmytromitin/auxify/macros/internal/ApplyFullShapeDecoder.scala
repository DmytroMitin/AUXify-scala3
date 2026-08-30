package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  AnnotatedClassTypeStructureView,
  AnnotatedClassView,
  ExpansionDiagnostic
}
import paradise3.api.AnnotatedClassBodyView.{DirectTypeShape, DirectVisibility}
import paradise3.api.AnnotatedClassTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

private[internal] object ApplyFullShapeDecoder:
  def decode(
      typeClassName: String,
      view: AnnotatedClassTypeStructureView
  ): Either[ExpansionDiagnostic, ApplyDefinitionBuilder.FullShape] =
    view.typeParameters match
      case List(first, second) =>
        for
          firstBound <- enclosingBound(typeClassName, first)
          secondBound <- enclosingBound(typeClassName, second)
          _ <-
            if firstBound == secondBound then Right(())
            else
              unsupported(
                typeClassName,
                "enclosing type-parameter upper bounds must be the same named type",
                second.pos
              )
          result <- singleResultMember(typeClassName, view)
          resultBound <- resultMemberBound(typeClassName, result)
          _ <-
            if resultBound == firstBound then Right(())
            else
              unsupported(
                typeClassName,
                s"result type member `${result.name}` upper bound must match enclosing bound `$firstBound`",
                result.pos
              )
        yield ApplyDefinitionBuilder.FullShape(
          typeClassName = typeClassName,
          firstTypeParameterName = first.name,
          secondTypeParameterName = second.name,
          upperBoundTypeName = firstBound,
          resultTypeMemberName = result.name
        )
      case parameters =>
        unsupported(
          typeClassName,
          s"requires exactly two enclosing type parameters; found ${parameters.size}",
          view.pos
        )

  private def enclosingBound(
      typeClassName: String,
      parameter: AnnotatedClassTypeStructureView.EnclosingTypeParameter
  ): Either[ExpansionDiagnostic, String] =
    if parameter.variance != AnnotatedClassView.Variance.Invariant then
      unsupported(
        typeClassName,
        s"enclosing type parameter `${parameter.name}` must be invariant",
        parameter.pos
      )
    else if parameter.lowerBound != Bound.Absent then
      unsupported(
        typeClassName,
        s"enclosing type parameter `${parameter.name}` must not define a lower bound",
        parameter.pos
      )
    else if parameter.hasContextBounds then
      unsupported(
        typeClassName,
        s"enclosing type parameter `${parameter.name}` must not define context bounds",
        parameter.pos
      )
    else
      parameter.upperBound match
        case Bound.Present(DirectTypeShape.NamedType(name, _)) => Right(name)
        case _ =>
          unsupported(
            typeClassName,
            "enclosing type-parameter upper bounds must be unqualified named types",
            parameter.pos
          )

  private def singleResultMember(
      typeClassName: String,
      view: AnnotatedClassTypeStructureView
  ): Either[ExpansionDiagnostic, DirectTypeMember] =
    view.directTypeMembers match
      case List(result) => Right(result)
      case members =>
        unsupported(
          typeClassName,
          s"requires exactly one direct type member; found ${members.size}",
          view.pos
        )

  private def resultMemberBound(
      typeClassName: String,
      result: DirectTypeMember
  ): Either[ExpansionDiagnostic, String] =
    if result.kind != DirectTypeMemberKind.AbstractBounds then
      val found = result.kind match
        case DirectTypeMemberKind.Alias => "alias"
        case DirectTypeMemberKind.Unsupported => "unsupported"
        case DirectTypeMemberKind.AbstractBounds => "abstract bounds"
      unsupported(
        typeClassName,
        s"result type member `${result.name}` must be abstract bounds, found $found",
        result.pos
      )
    else if result.typeParameters.nonEmpty then
      unsupported(
        typeClassName,
        s"result type member `${result.name}` must not declare type parameters",
        result.pos
      )
    else if result.lowerBound != Bound.Absent then
      unsupported(
        typeClassName,
        s"result type member `${result.name}` must not define a lower bound",
        result.pos
      )
    else if result.aliasTarget.nonEmpty then
      unsupported(
        typeClassName,
        s"result type member `${result.name}` must not define an alias target",
        result.pos
      )
    else if
      result.modifiers.visibility != DirectVisibility.Public ||
        result.modifiers.hasAnnotations ||
        result.modifiers.annotationCount != 0 ||
        result.modifiers.unsupportedFlags.nonEmpty
    then
      unsupported(
        typeClassName,
        s"result type member `${result.name}` must be public, unannotated, and free of unsupported modifiers",
        result.pos
      )
    else
      result.upperBound match
        case Bound.Present(DirectTypeShape.NamedType(name, _)) => Right(name)
        case _ =>
          unsupported(
            typeClassName,
            s"result type member `${result.name}` upper bound must be an unqualified named type",
            result.pos
          )

  private def unsupported[A](
      typeClassName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, A] =
    Left(
      ExpansionDiagnostic(
        s"unsupported full @apply source shape for `$typeClassName`: $reason",
        pos
      )
    )
