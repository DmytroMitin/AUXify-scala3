package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView
}
import paradise3.api.ExpansionTargetBodyView.{
  DirectMember,
  DirectMemberKind,
  DirectMethod,
  DirectMethodParameter,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}
import paradise3.api.ExpansionTargetTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

private[internal] object InstanceInheritedConcreteTailValidator:
  private final case class TailState(
      occupiedTermNames: Set[String],
      earlierValidatedTypeAliases: Set[String]
  )

  def validate(
      traitName: String,
      enclosingTypeParameterName: String,
      members: List[DirectMember],
      firstBodyIndex: Int,
      typeStructureView: Option[ExpansionTargetTypeStructureView],
      diagnosticAnnotation: String = "@instance",
      permittedRequiredAbstractRoot: Option[String] = None
  ): Either[ExpansionDiagnostic, Set[String]] =
    val earlierDirectTypeNames = typeStructureView.toList
      .flatMap(_.directTypeMembers)
      .collect:
        case member if member.bodyIndex < firstBodyIndex => member.name
      .toSet
    members.zipWithIndex.foldLeft[Either[ExpansionDiagnostic, TailState]](
      Right(TailState(Set.empty, Set.empty))
    ): (validated, indexedMember) =>
      val (member, offset) = indexedMember
      validated.flatMap: state =>
        inheritedMemberState(
          diagnosticAnnotation,
          traitName,
          index = firstBodyIndex + offset,
          member,
          enclosingTypeParameterName,
          typeStructureView,
          earlierDirectTypeNames,
          permittedRequiredAbstractRoot,
          state
        )
    .map(_.occupiedTermNames)

  private def inheritedMemberState(
      diagnosticAnnotation: String,
      traitName: String,
      index: Int,
      member: DirectMember,
      enclosingTypeParameterName: String,
      typeStructureView: Option[ExpansionTargetTypeStructureView],
      earlierDirectTypeNames: Set[String],
      permittedRequiredAbstractRoot: Option[String],
      state: TailState
  ): Either[ExpansionDiagnostic, TailState] =
    member.kind match
      case DirectMemberKind.Method =>
        inheritedMethodOccupiedNames(
          diagnosticAnnotation,
          traitName,
          index,
          member,
          enclosingTypeParameterName,
          state.earlierValidatedTypeAliases,
          permittedRequiredAbstractRoot
        ).map: occupied =>
          state.copy(occupiedTermNames = state.occupiedTermNames ++ occupied)
      case DirectMemberKind.Type =>
        for
          typeStructure <- typeStructureView.toRight(
            diagnostic(
              diagnosticAnnotation,
              traitName,
              s"direct body member at index $index must provide normalized type-member evidence",
              member.pos
            )
          )
          alias <- concreteAliasAtIndex(
            diagnosticAnnotation,
            traitName,
            index,
            typeStructure,
            member
          )
          _ <- eligibleConcreteAlias(
            diagnosticAnnotation,
            traitName,
            alias,
            enclosingTypeParameterName,
            earlierDirectTypeNames,
            state.earlierValidatedTypeAliases,
            permittedRequiredAbstractRoot
          )
        yield state.copy(
          earlierValidatedTypeAliases =
            state.earlierValidatedTypeAliases + alias.name
        )
      case _ =>
        directMethod(diagnosticAnnotation, traitName, index, member).map(_ => state)

  private def inheritedMethodOccupiedNames(
      diagnosticAnnotation: String,
      traitName: String,
      index: Int,
      member: DirectMember,
      enclosingTypeParameterName: String,
      earlierValidatedTypeAliases: Set[String],
      permittedRequiredAbstractRoot: Option[String]
  ): Either[ExpansionDiagnostic, Set[String]] =
    for
      inheritedMethod <- directMethod(diagnosticAnnotation, traitName, index, member)
      _ <- eligibleInheritedMethod(diagnosticAnnotation, traitName, inheritedMethod)
      inheritedParameters <- inheritedTopology(diagnosticAnnotation, traitName, inheritedMethod)
      _ <- inheritedParameters.foldLeft[Either[ExpansionDiagnostic, Unit]](
        Right(())
      ): (validated, parameter) =>
        validated.flatMap: _ =>
          inheritedParameterType(
            diagnosticAnnotation,
            traitName,
            inheritedMethod,
            parameter,
            enclosingTypeParameterName,
            earlierValidatedTypeAliases,
            permittedRequiredAbstractRoot
          )
      _ <- enclosingResult(
        diagnosticAnnotation,
        traitName,
        "inherited concrete",
        inheritedMethod,
        enclosingTypeParameterName,
        earlierValidatedTypeAliases,
        permittedRequiredAbstractRoot
      )
    yield Set(inheritedMethod.name) ++ inheritedParameters.map(_.name)

  private def concreteAliasAtIndex(
      diagnosticAnnotation: String,
      traitName: String,
      index: Int,
      typeStructureView: ExpansionTargetTypeStructureView,
      member: DirectMember
  ): Either[ExpansionDiagnostic, DirectTypeMember] =
    typeStructureView.directTypeMembers.filter(_.bodyIndex == index) match
      case List(alias) if alias.name == member.name => Right(alias)
      case List(alias) =>
        unsupported(
          diagnosticAnnotation,
          traitName,
          s"normalized direct type member `${alias.name}` at body index $index must match actual direct type member `${member.name}`",
          alias.pos
        )
      case Nil =>
        unsupported(
          diagnosticAnnotation,
          traitName,
          s"direct body member at index $index must provide normalized type-member evidence",
          member.pos
        )
      case aliases =>
        unsupported(
          diagnosticAnnotation,
          traitName,
          s"direct body member at index $index must provide exactly one normalized direct type member; found ${aliases.size}",
          aliases.headOption.map(_.pos).getOrElse(member.pos)
        )

  private def eligibleConcreteAlias(
      diagnosticAnnotation: String,
      traitName: String,
      alias: DirectTypeMember,
      enclosingTypeParameterName: String,
      earlierDirectTypeNames: Set[String],
      earlierValidatedTypeAliases: Set[String],
      permittedRequiredAbstractRoot: Option[String]
  ): Either[ExpansionDiagnostic, Unit] =
    val aliasName = alias.name
    if !normalizedNameAvailable(aliasName) then
      unsupported(
        diagnosticAnnotation,
        traitName,
        "inherited concrete type alias must have an available normalized name",
        alias.pos
      )
    else if
      aliasName == enclosingTypeParameterName ||
        earlierDirectTypeNames.contains(aliasName) ||
        earlierValidatedTypeAliases.contains(aliasName)
    then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete type alias `$aliasName` must not duplicate the enclosing type parameter or an earlier direct type member",
        alias.pos
      )
    else if alias.kind != DirectTypeMemberKind.Alias then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited type member `$aliasName` must be a concrete alias",
        alias.pos
      )
    else if alias.typeParameters.nonEmpty then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete type alias `$aliasName` must not declare type parameters",
        alias.pos
      )
    else if
      alias.modifiers.visibility != DirectVisibility.Public ||
        alias.modifiers.hasAnnotations ||
        alias.modifiers.annotationCount != 0 ||
        alias.modifiers.unsupportedFlags.nonEmpty
    then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete type alias `$aliasName` must be public, unannotated, and free of unsupported modifiers",
        alias.pos
      )
    else if alias.lowerBound != Bound.Absent || alias.upperBound != Bound.Absent then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete type alias `$aliasName` must not declare lower or upper bounds",
        alias.pos
      )
    else
      alias.aliasTarget match
        case Some(DirectTypeShape.EnclosingTypeParameter(name, _))
            if name == enclosingTypeParameterName => Right(())
        case Some(DirectTypeShape.NamedType(name, _))
            if earlierValidatedTypeAliases.contains(name) ||
              permittedRequiredAbstractRoot.contains(name) => Right(())
        case _ =>
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"inherited concrete type alias `$aliasName` must target enclosing type parameter `$enclosingTypeParameterName` or an earlier validated concrete alias" + requiredRootDescription(permittedRequiredAbstractRoot),
            alias.pos
          )

  private def directMethod(
      diagnosticAnnotation: String,
      traitName: String,
      index: Int,
      member: DirectMember
  ): Either[ExpansionDiagnostic, DirectMethod] =
    if member.kind != DirectMemberKind.Method then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"direct body member at index $index must be a method; found ${memberKindLabel(member.kind)}",
        member.pos
      )
    else
      member.method match
        case Some(method) if normalizedNameAvailable(method.name) => Right(method)
        case Some(method) =>
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"direct method at index $index must have an available normalized name",
            method.pos
          )
        case None =>
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"direct body member at index $index must provide normalized method evidence",
            member.pos
          )

  private def eligibleInheritedMethod(
      diagnosticAnnotation: String,
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, Unit] =
    if
      method.modifiers.visibility != DirectVisibility.Public ||
        method.modifiers.hasAnnotations ||
        method.modifiers.annotationCount != 0 ||
        method.modifiers.unsupportedFlags.nonEmpty
    then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete method `${method.name}` must be public, unannotated, and free of unsupported modifiers",
        method.pos
      )
    else if method.status != DirectMethodStatus.Concrete then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited method `${method.name}` must be concrete",
        method.pos
      )
    else if method.typeParameters.nonEmpty then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete method `${method.name}` must not declare method type parameters",
        method.pos
      )
    else Right(())

  private def inheritedTopology(
      diagnosticAnnotation: String,
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, List[DirectMethodParameter]] =
    method.parameterClauses match
      case Nil => Right(Nil)
      case List(clause) =>
        if clause.isContextual || clause.isImplicit || clause.isGiven then
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"inherited concrete method `${method.name}` parameter clause must be ordinary and non-contextual",
            clause.pos
          )
        else if clause.parameters.isEmpty then
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"inherited concrete method `${method.name}` requires one or more ordinary parameters in its single clause; found 0",
            clause.pos
          )
        else Right(clause.parameters)
      case clauses =>
        unsupported(
          diagnosticAnnotation,
          traitName,
          s"inherited concrete method `${method.name}` requires exactly one ordinary parameter clause; found ${clauses.size}",
          method.pos
        )

  private def inheritedParameterType(
      diagnosticAnnotation: String,
      traitName: String,
      method: DirectMethod,
      parameter: DirectMethodParameter,
      enclosingTypeParameterName: String,
      earlierValidatedTypeAliases: Set[String],
      permittedRequiredAbstractRoot: Option[String]
  ): Either[ExpansionDiagnostic, Unit] =
    if
      !normalizedNameAvailable(parameter.name) ||
        parameter.hasDefault ||
        parameter.isContextual ||
        parameter.isImplicit ||
        parameter.isGiven ||
        parameter.isVal ||
        parameter.isVar
    then
      unsupported(
        diagnosticAnnotation,
        traitName,
        s"inherited concrete method `${method.name}` parameter `${parameter.name}` must be ordinary, non-defaulted, and unmodified",
        parameter.pos
      )
    else
      parameter.parameterType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosingTypeParameterName => Right(())
        case DirectTypeShape.NamedType(name, _)
            if earlierValidatedTypeAliases.contains(name) ||
              permittedRequiredAbstractRoot.contains(name) => Right(())
        case _ =>
          unsupported(
            diagnosticAnnotation,
            traitName,
            s"inherited concrete method `${method.name}` parameter `${parameter.name}` must use enclosing type parameter `$enclosingTypeParameterName` or an earlier validated concrete alias" + requiredRootDescription(permittedRequiredAbstractRoot),
            parameter.typePos
          )

  private def enclosingResult(
      diagnosticAnnotation: String,
      traitName: String,
      role: String,
      method: DirectMethod,
      enclosingTypeParameterName: String,
      earlierValidatedTypeAliases: Set[String],
      permittedRequiredAbstractRoot: Option[String]
  ): Either[ExpansionDiagnostic, Unit] =
    method.resultType match
      case DirectTypeShape.EnclosingTypeParameter(name, _)
          if name == enclosingTypeParameterName => Right(())
      case DirectTypeShape.NamedType(name, _)
          if earlierValidatedTypeAliases.contains(name) ||
              permittedRequiredAbstractRoot.contains(name) => Right(())
      case _ =>
        unsupported(
          diagnosticAnnotation,
          traitName,
          s"$role method `${method.name}` result type must use enclosing type parameter `$enclosingTypeParameterName` or an earlier validated concrete alias" + requiredRootDescription(permittedRequiredAbstractRoot),
          method.resultTypePos
        )

  private def requiredRootDescription(root: Option[String]): String =
    root.fold("")(name => s" or required abstract type member `$name`")

  private def normalizedNameAvailable(name: String): Boolean =
    name != null && name.nonEmpty && name != "<error>" && name != "<unknown>"

  private def memberKindLabel(kind: DirectMemberKind): String = kind match
    case DirectMemberKind.Method => "method"
    case DirectMemberKind.Val => "val"
    case DirectMemberKind.Var => "var"
    case DirectMemberKind.Type => "type"
    case DirectMemberKind.NestedClass => "nested class"
    case DirectMemberKind.NestedTrait => "nested trait"
    case DirectMemberKind.NestedObject => "nested object"
    case DirectMemberKind.Other => "other"

  private def unsupported[A](
      diagnosticAnnotation: String,
      traitName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, A] =
    Left(diagnostic(diagnosticAnnotation, traitName, reason, pos))

  private def diagnostic(
      diagnosticAnnotation: String,
      traitName: String,
      reason: String,
      pos: SrcPos
  ): ExpansionDiagnostic =
    ExpansionDiagnostic(
      s"unsupported $diagnosticAnnotation source shape for `$traitName`: $reason",
      pos
    )
