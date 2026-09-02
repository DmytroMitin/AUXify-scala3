package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{AnnotatedClassTypeStructureView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{DirectTypeShape, DirectVisibility}
import paradise3.api.AnnotatedClassTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

private[internal] final case class BoundedResultTypeClassShape(
    typeClassName: String,
    firstTypeParameterName: String,
    secondTypeParameterName: String,
    upperBoundTypeName: String,
    resultTypeMemberName: String
)

private[internal] object BoundedResultTypeClassShapeDecoder:
  final case class Rejection(reason: String, pos: SrcPos)

  def decode(
      typeClassName: String,
      view: AnnotatedClassTypeStructureView
  ): Either[Rejection, BoundedResultTypeClassShape] =
    view.typeParameters match
      case List(first, second) =>
        for
          firstBound <- enclosingBound(first)
          secondBound <- enclosingBound(second)
          _ <-
            if firstBound == secondBound then Right(())
            else
              reject(
                "enclosing type-parameter upper bounds must be the same named type",
                second.pos
              )
          result <- singleResultMember(view)
          resultBound <- resultMemberBound(result)
          _ <-
            if resultBound == firstBound then Right(())
            else
              reject(
                s"result type member `${result.name}` upper bound must match enclosing bound `$firstBound`",
                result.pos
              )
        yield BoundedResultTypeClassShape(
          typeClassName = typeClassName,
          firstTypeParameterName = first.name,
          secondTypeParameterName = second.name,
          upperBoundTypeName = firstBound,
          resultTypeMemberName = result.name
        )
      case parameters =>
        reject(
          s"requires exactly two enclosing type parameters; found ${parameters.size}",
          view.pos
        )

  private def enclosingBound(
      parameter: AnnotatedClassTypeStructureView.EnclosingTypeParameter
  ): Either[Rejection, String] =
    if parameter.variance != AnnotatedClassView.Variance.Invariant then
      reject(
        s"enclosing type parameter `${parameter.name}` must be invariant",
        parameter.pos
      )
    else if parameter.lowerBound != Bound.Absent then
      reject(
        s"enclosing type parameter `${parameter.name}` must not define a lower bound",
        parameter.pos
      )
    else if parameter.hasContextBounds then
      reject(
        s"enclosing type parameter `${parameter.name}` must not define context bounds",
        parameter.pos
      )
    else
      parameter.upperBound match
        case Bound.Present(DirectTypeShape.NamedType(name, _)) => Right(name)
        case _ =>
          reject(
            "enclosing type-parameter upper bounds must be unqualified named types",
            parameter.pos
          )

  private def singleResultMember(
      view: AnnotatedClassTypeStructureView
  ): Either[Rejection, DirectTypeMember] =
    view.directTypeMembers match
      case List(result) => Right(result)
      case members =>
        reject(
          s"requires exactly one direct type member; found ${members.size}",
          view.pos
        )

  private def resultMemberBound(
      result: DirectTypeMember
  ): Either[Rejection, String] =
    if result.kind != DirectTypeMemberKind.AbstractBounds then
      val found = result.kind match
        case DirectTypeMemberKind.Alias => "alias"
        case DirectTypeMemberKind.Unsupported => "unsupported"
        case DirectTypeMemberKind.AbstractBounds => "abstract bounds"
      reject(
        s"result type member `${result.name}` must be abstract bounds, found $found",
        result.pos
      )
    else if result.typeParameters.nonEmpty then
      reject(
        s"result type member `${result.name}` must not declare type parameters",
        result.pos
      )
    else if result.lowerBound != Bound.Absent then
      reject(
        s"result type member `${result.name}` must not define a lower bound",
        result.pos
      )
    else if result.aliasTarget.nonEmpty then
      reject(
        s"result type member `${result.name}` must not define an alias target",
        result.pos
      )
    else if
      result.modifiers.visibility != DirectVisibility.Public ||
        result.modifiers.hasAnnotations ||
        result.modifiers.annotationCount != 0 ||
        result.modifiers.unsupportedFlags.nonEmpty
    then
      reject(
        s"result type member `${result.name}` must be public, unannotated, and free of unsupported modifiers",
        result.pos
      )
    else
      result.upperBound match
        case Bound.Present(DirectTypeShape.NamedType(name, _)) => Right(name)
        case _ =>
          reject(
            s"result type member `${result.name}` upper bound must be an unqualified named type",
            result.pos
          )

  private def reject[A](reason: String, pos: SrcPos): Left[Rejection, A] =
    Left(Rejection(reason, pos))
