package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionTargetView
}
import paradise3.api.ExpansionTargetBodyView.{DirectMemberKind, DirectVisibility}
import paradise3.api.ExpansionTargetTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

private[internal] object InstanceAbstractTypeMemberSourceShapeDecoder:
  final case class SourceShape(
      traitName: String,
      enclosingTypeParameterName: String,
      memberName: String,
      generatedTypeParameterName: String
  )

  def decode(
      classView: ExpansionTargetView,
      bodyView: ExpansionTargetBodyView,
      typeStructureView: ExpansionTargetTypeStructureView
  ): Either[ExpansionDiagnostic, SourceShape] =
    val traitName = classView.className
    for
      _ <- requireNormalizedName(
        traitName,
        traitName,
        "requires an available normalized trait name",
        classView.classPos
      )
      enclosing <- classView.typeParameters match
        case List(parameter) => Right(parameter)
        case parameters =>
          unsupported(
            traitName,
            s"abstract-type-member family requires exactly one enclosing type parameter; found ${parameters.size}",
            classView.classPos
          )
      _ <- requireNormalizedName(
        traitName,
        enclosing.name,
        "enclosing type parameter requires an available normalized name",
        enclosing.pos
      )
      _ <-
        if
          enclosing.variance == ExpansionTargetView.Variance.Invariant &&
            enclosing.isOrdinaryUnbounded &&
            !enclosing.hasContextBounds &&
            !enclosing.isOrdinaryUpperBounded
        then Right(())
        else
          unsupported(
            traitName,
            s"enclosing type parameter `${enclosing.name}` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds",
            enclosing.pos
          )
      direct <- bodyView.members match
        case List(member) => Right(member)
        case members =>
          unsupported(
            traitName,
            s"abstract-type-member family requires exactly one direct body member; found ${members.size}",
            bodyView.pos
          )
      _ <-
        if direct.kind == DirectMemberKind.Type then Right(())
        else
          unsupported(
            traitName,
            s"direct body member at index 0 must be an abstract type member; found ${memberKindLabel(direct.kind)}",
            direct.pos
          )
      member <- typeStructureView.directTypeMembers match
        case List(value) if value.bodyIndex == 0 => Right(value)
        case members =>
          unsupported(
            traitName,
            s"direct body member at index 0 must provide exactly one normalized type-member record at body index 0; found ${members.size}",
            members.headOption.map(_.pos).getOrElse(direct.pos)
          )
      _ <- eligibleMember(traitName, member)
      generated = freshTypeParameterName(
        member.name,
        Set(traitName, enclosing.name, member.name)
      )
    yield SourceShape(traitName, enclosing.name, member.name, generated)

  private def eligibleMember(
      traitName: String,
      member: DirectTypeMember
  ): Either[ExpansionDiagnostic, Unit] =
    val memberName = member.name
    if !normalizedNameAvailable(memberName) then
      unsupported(
        traitName,
        "abstract type member requires an available normalized name",
        member.pos
      )
    else if member.kind != DirectTypeMemberKind.AbstractBounds then
      unsupported(
        traitName,
        s"direct type member `$memberName` must be abstract, not a concrete alias",
        member.pos
      )
    else if member.typeParameters.nonEmpty then
      unsupported(
        traitName,
        s"abstract type member `$memberName` must not declare type parameters",
        member.pos
      )
    else if
      member.modifiers.visibility != DirectVisibility.Public ||
        member.modifiers.hasAnnotations ||
        member.modifiers.annotationCount != 0 ||
        member.modifiers.unsupportedFlags.nonEmpty
    then
      unsupported(
        traitName,
        s"abstract type member `$memberName` must be public, unannotated, and free of unsupported modifiers",
        member.pos
      )
    else if member.lowerBound != Bound.Absent || member.upperBound != Bound.Absent then
      unsupported(
        traitName,
        s"abstract type member `$memberName` must be unbounded",
        member.pos
      )
    else if member.aliasTarget.nonEmpty then
      unsupported(
        traitName,
        s"abstract type member `$memberName` must not provide an alias target",
        member.pos
      )
    else Right(())

  private def freshTypeParameterName(memberName: String, occupied: Set[String]): String =
    Iterator.from(0)
      .map(index => s"$memberName$index")
      .find(name => !occupied.contains(name))
      .getOrElse(s"${memberName}0")

  private def requireNormalizedName(
      traitName: String,
      name: String,
      reason: String,
      pos: SrcPos
  ): Either[ExpansionDiagnostic, Unit] =
    if normalizedNameAvailable(name) then Right(())
    else unsupported(String.valueOf(traitName), reason, pos)

  private def normalizedNameAvailable(name: String): Boolean =
    name != null && name.nonEmpty && name != "<error>" && name != "<unknown>"

  private def memberKindLabel(kind: DirectMemberKind): String =
    kind.toString.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase

  private def unsupported(
      traitName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, Nothing] =
    Left(
      ExpansionDiagnostic(
        s"unsupported @instance source shape for `$traitName`: $reason",
        pos
      )
    )
