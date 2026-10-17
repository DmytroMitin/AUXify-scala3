package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionTargetView
}
import paradise3.api.ExpansionTargetBodyView.{
  DirectMemberKind,
  DirectTypeShape,
  DirectVisibility
}
import paradise3.api.ExpansionTargetTypeStructureView.{
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
      bodyView: ExpansionTargetBodyView,
      view: ExpansionTargetTypeStructureView
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
          members <- resultMemberAndConcreteAliases(bodyView, view)
          (result, aliases) = members
          resultBound <- resultMemberBound(result)
          _ <-
            if resultBound == firstBound then Right(())
            else
              reject(
                s"result type member `${result.name}` upper bound must match enclosing bound `$firstBound`",
                result.pos
              )
          _ <- aliases.foldLeft[Either[Rejection, Unit]](Right(())):
            (validated, alias) =>
              validated.flatMap: _ =>
                concreteBoundedAlias(
                  alias,
                  Set(first.name, second.name),
                  result.name
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
      parameter: ExpansionTargetTypeStructureView.EnclosingTypeParameter
  ): Either[Rejection, String] =
    if parameter.variance != ExpansionTargetView.Variance.Invariant then
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

  private def resultMemberAndConcreteAliases(
      bodyView: ExpansionTargetBodyView,
      view: ExpansionTargetTypeStructureView
  ): Either[Rejection, (DirectTypeMember, List[DirectTypeMember])] =
    val members = view.directTypeMembers
    val bodyTypeIndices = bodyView.members.zipWithIndex.collect {
      case (member, index) if member.kind == DirectMemberKind.Type => index
    }
    val recordIndices = members.map(_.bodyIndex)

    for
      _ <- members.foldLeft[Either[Rejection, Unit]](Right(())):
        (validated, member) =>
          validated.flatMap: _ =>
            if member.bodyIndex < 0 || member.bodyIndex >= bodyView.members.size then
              reject(
                s"direct type member `${member.name}` references invalid body index ${member.bodyIndex}",
                member.pos
              )
            else if bodyView.members(member.bodyIndex).kind != DirectMemberKind.Type then
              reject(
                s"direct type member `${member.name}` at body index ${member.bodyIndex} must match an actual direct type member",
                member.pos
              )
            else Right(())
      _ <- recordIndices.groupBy(identity).collectFirst {
        case (index, duplicates) if duplicates.size > 1 => index -> duplicates.size
      } match
        case Some((index, count)) =>
          reject(
            s"direct body type member at index $index must provide exactly one normalized direct type record; found $count",
            members.find(_.bodyIndex == index).map(_.pos).getOrElse(bodyView.pos)
          )
        case None => Right(())
      _ <- bodyTypeIndices.find(index => !recordIndices.contains(index)) match
        case Some(index) =>
          reject(
            s"direct body type member at index $index must provide exactly one normalized direct type record; found 0",
            bodyView.members(index).pos
          )
        case None => Right(())
      _ <-
        if recordIndices == bodyTypeIndices then Right(())
        else
          reject(
            s"normalized direct type-member records must preserve source body order; expected ${bodyTypeIndices.mkString("[", ", ", "]")}, found ${recordIndices.mkString("[", ", ", "]")}",
            members.headOption.map(_.pos).getOrElse(bodyView.pos)
          )
      _ <- members.foldLeft[Either[Rejection, Unit]](Right(())):
        (validated, member) =>
          validated.flatMap: _ =>
            if bodyView.members(member.bodyIndex).name == member.name then Right(())
            else
              reject(
                s"normalized direct type member `${member.name}` at body index ${member.bodyIndex} must match actual direct type member `${bodyView.members(member.bodyIndex).name}`",
                member.pos
              )
      resultAndAliases <- members match
        case result :: aliases => Right((result, aliases))
        case Nil =>
          reject(
            "requires exactly one direct type member; found 0",
            view.pos
          )
    yield resultAndAliases

  private def concreteBoundedAlias(
      alias: DirectTypeMember,
      enclosingParameterNames: Set[String],
      resultMemberName: String
  ): Either[Rejection, Unit] =
    if alias.name == null || alias.name.isEmpty || alias.name == "<error>" || alias.name == "<unknown>" then
      reject(
        "concrete bounded-result alias must have an available normalized name",
        alias.pos
      )
    else if alias.kind != DirectTypeMemberKind.Alias then
      val found = alias.kind match
        case DirectTypeMemberKind.Alias => "alias"
        case DirectTypeMemberKind.Unsupported => "unsupported"
        case DirectTypeMemberKind.AbstractBounds => "abstract bounds"
      reject(
        s"additional direct type member `${alias.name}` must be a concrete alias, found $found",
        alias.pos
      )
    else if alias.typeParameters.nonEmpty then
      reject(
        s"concrete bounded-result alias `${alias.name}` must not declare type parameters",
        alias.pos
      )
    else if alias.lowerBound != Bound.Absent || alias.upperBound != Bound.Absent then
      reject(
        s"concrete bounded-result alias `${alias.name}` must not declare lower or upper bounds",
        alias.pos
      )
    else if
      alias.modifiers.visibility != DirectVisibility.Public ||
        alias.modifiers.hasAnnotations ||
        alias.modifiers.annotationCount != 0 ||
        alias.modifiers.unsupportedFlags.nonEmpty
    then
      reject(
        s"concrete bounded-result alias `${alias.name}` must be public, unannotated, and free of unsupported modifiers",
        alias.pos
      )
    else
      alias.aliasTarget match
        case Some(DirectTypeShape.EnclosingTypeParameter(name, _))
            if enclosingParameterNames.contains(name) => Right(())
        case Some(DirectTypeShape.NamedType(name, _)) if name == resultMemberName =>
          Right(())
        case _ =>
          reject(
            s"concrete bounded-result alias `${alias.name}` must target one of the enclosing type parameters ${enclosingParameterNames.toList.sorted.mkString("`", "` or `", "`")} or the abstract result member `$resultMemberName`",
            alias.pos
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
