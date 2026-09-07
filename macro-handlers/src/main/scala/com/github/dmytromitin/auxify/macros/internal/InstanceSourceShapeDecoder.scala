package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  AnnotatedClassBodyView,
  AnnotatedClassTypeStructureView,
  AnnotatedClassView,
  ExpansionDiagnostic
}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMember,
  DirectMemberKind,
  DirectMethod,
  DirectMethodParameter,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}
import paradise3.api.AnnotatedClassTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

private[internal] object InstanceSourceShapeDecoder:
  final case class SourceShape(
      traitName: String,
      enclosingTypeParameterName: String,
      parameterlessMethodName: String,
      binaryMethodName: String,
      binaryFirstParameterName: String,
      binarySecondParameterName: String,
      parameterlessCarrierName: String,
      binaryCarrierName: String
  )

  def decode(
      classView: AnnotatedClassView,
      bodyView: AnnotatedClassBodyView,
      typeStructureView: Option[AnnotatedClassTypeStructureView] = None
  ): Either[ExpansionDiagnostic, SourceShape] =
    val traitName = classView.className
    if !normalizedNameAvailable(traitName) then
      unsupported(
        String.valueOf(traitName),
        "requires an available normalized trait name",
        classView.classPos
      )
    else
      classView.typeParameters match
        case List(typeParameter)
            if normalizedNameAvailable(typeParameter.name) &&
              typeParameter.variance == AnnotatedClassView.Variance.Invariant &&
              typeParameter.isOrdinaryUnbounded &&
              !typeParameter.hasContextBounds &&
              !typeParameter.isOrdinaryUpperBounded =>
          bodyView.members match
            case List(parameterlessMember, binaryMember) =>
              for
                shape <- decodeAbstractRoles(
                  traitName,
                  typeParameter.name,
                  parameterlessMember,
                  binaryMember,
                  Set.empty
                )
              yield shape
            case List(parameterlessMember, binaryMember, inheritedMember) =>
              inheritedMember.kind match
                case DirectMemberKind.Method =>
                  for
                    inheritedMethod <- directMethod(
                      traitName,
                      index = 2,
                      inheritedMember
                    )
                    _ <- eligibleInheritedMethod(traitName, inheritedMethod)
                    inheritedParameter <- inheritedTopology(
                      traitName,
                      inheritedMethod
                    )
                    _ <- inheritedParameterType(
                      traitName,
                      inheritedMethod,
                      inheritedParameter,
                      typeParameter.name
                    )
                    _ <- enclosingResult(
                      traitName,
                      "inherited concrete",
                      inheritedMethod,
                      typeParameter.name
                    )
                    shape <- decodeAbstractRoles(
                      traitName,
                      typeParameter.name,
                      parameterlessMember,
                      binaryMember,
                      Set(inheritedMethod.name, inheritedParameter.name)
                    )
                  yield shape
                case DirectMemberKind.Type =>
                  for
                    typeStructure <- typeStructureView.toRight(
                      ExpansionDiagnostic(
                        s"unsupported @instance source shape for `$traitName`: direct body member at index 2 must provide normalized type-member evidence",
                        inheritedMember.pos
                      )
                    )
                    alias <- thirdPositionConcreteAlias(
                      traitName,
                      typeStructure,
                      inheritedMember.pos
                    )
                    _ <- eligibleConcreteAlias(
                      traitName,
                      alias,
                      typeParameter.name
                    )
                    shape <- decodeAbstractRoles(
                      traitName,
                      typeParameter.name,
                      parameterlessMember,
                      binaryMember,
                      Set.empty
                    )
                  yield shape
                case _ =>
                  directMethod(traitName, index = 2, inheritedMember).flatMap(_ =>
                    unsupported(
                      traitName,
                      "unreachable third-member classification",
                      inheritedMember.pos
                    )
                  )
            case members =>
              unsupported(
                traitName,
                if members.size < 2 then
                  s"requires exactly two direct body members; found ${members.size}"
                else
                  s"requires exactly two direct body members or exactly three with one supported inherited concrete method; found ${members.size}",
                bodyView.pos
              )
        case _ =>
          unsupported(
            traitName,
            "requires exactly one invariant unbounded enclosing type parameter",
            classView.classPos
          )

  private def thirdPositionConcreteAlias(
      traitName: String,
      typeStructureView: AnnotatedClassTypeStructureView,
      fallbackPos: SrcPos
  ): Either[ExpansionDiagnostic, DirectTypeMember] =
    typeStructureView.directTypeMembers match
      case List(alias) if alias.bodyIndex == 2 => Right(alias)
      case members =>
        unsupported(
          traitName,
          "direct body member at index 2 must be the only normalized direct type member",
          members.find(_.bodyIndex == 2).map(_.pos).getOrElse(fallbackPos)
        )

  private def eligibleConcreteAlias(
      traitName: String,
      alias: DirectTypeMember,
      enclosingTypeParameterName: String
  ): Either[ExpansionDiagnostic, Unit] =
    val aliasName = alias.name
    if !normalizedNameAvailable(aliasName) then
      unsupported(
        traitName,
        "inherited concrete type alias must have an available normalized name",
        alias.pos
      )
    else if alias.kind != DirectTypeMemberKind.Alias then
      unsupported(
        traitName,
        s"inherited type member `$aliasName` must be a concrete alias",
        alias.pos
      )
    else if alias.typeParameters.nonEmpty then
      unsupported(
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
        traitName,
        s"inherited concrete type alias `$aliasName` must be public, unannotated, and free of unsupported modifiers",
        alias.pos
      )
    else if alias.lowerBound != Bound.Absent || alias.upperBound != Bound.Absent then
      unsupported(
        traitName,
        s"inherited concrete type alias `$aliasName` must not declare lower or upper bounds",
        alias.pos
      )
    else
      alias.aliasTarget match
        case Some(DirectTypeShape.EnclosingTypeParameter(name, _))
            if name == enclosingTypeParameterName => Right(())
        case _ =>
          unsupported(
            traitName,
            s"inherited concrete type alias `$aliasName` must target enclosing type parameter `$enclosingTypeParameterName`",
            alias.pos
          )

  private def decodeAbstractRoles(
      traitName: String,
      enclosingTypeParameterName: String,
      parameterlessMember: DirectMember,
      binaryMember: DirectMember,
      additionallyOccupied: Set[String]
  ): Either[ExpansionDiagnostic, SourceShape] =
    for
      parameterlessMethod <- directMethod(
        traitName,
        index = 0,
        parameterlessMember
      )
      _ <- eligibleMethod(traitName, parameterlessMethod)
      _ <- parameterlessTopology(traitName, parameterlessMethod)
      _ <- enclosingResult(
        traitName,
        "parameterless",
        parameterlessMethod,
        enclosingTypeParameterName
      )
      binaryMethod <- directMethod(
        traitName,
        index = 1,
        binaryMember
      )
      _ <- eligibleMethod(traitName, binaryMethod)
      binaryParameters <- binaryTopology(traitName, binaryMethod)
      _ <- binaryParameter(
        traitName,
        binaryMethod,
        binaryParameters.head,
        enclosingTypeParameterName
      )
      _ <- binaryParameter(
        traitName,
        binaryMethod,
        binaryParameters(1),
        enclosingTypeParameterName
      )
      _ <- enclosingResult(
        traitName,
        "binary",
        binaryMethod,
        enclosingTypeParameterName
      )
      occupied = Set(
        "instance",
        parameterlessMethod.name,
        binaryMethod.name,
        binaryParameters.head.name,
        binaryParameters(1).name
      ) ++ additionallyOccupied
      parameterlessCarrier = freshCarrierName("emptyValue", occupied)
      binaryCarrier = freshCarrierName(
        "combineFunction",
        occupied + parameterlessCarrier
      )
    yield SourceShape(
      traitName = traitName,
      enclosingTypeParameterName = enclosingTypeParameterName,
      parameterlessMethodName = parameterlessMethod.name,
      binaryMethodName = binaryMethod.name,
      binaryFirstParameterName = binaryParameters.head.name,
      binarySecondParameterName = binaryParameters(1).name,
      parameterlessCarrierName = parameterlessCarrier,
      binaryCarrierName = binaryCarrier
    )

  private def directMethod(
      traitName: String,
      index: Int,
      member: DirectMember
  ): Either[ExpansionDiagnostic, DirectMethod] =
    if member.kind != DirectMemberKind.Method then
      unsupported(
        traitName,
        s"direct body member at index $index must be a method; found ${memberKindLabel(member.kind)}",
        member.pos
      )
    else
      member.method match
        case Some(method) if normalizedNameAvailable(method.name) => Right(method)
        case Some(method) =>
          unsupported(
            traitName,
            s"direct method at index $index must have an available normalized name",
            method.pos
          )
        case None =>
          unsupported(
            traitName,
            s"direct body member at index $index must provide normalized method evidence",
            member.pos
          )

  private def eligibleMethod(
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
        traitName,
        s"direct method `${method.name}` must be public, unannotated, and free of unsupported modifiers",
        method.pos
      )
    else if method.status != DirectMethodStatus.Abstract then
      unsupported(
        traitName,
        s"direct method `${method.name}` must be abstract",
        method.pos
      )
    else if method.typeParameters.nonEmpty then
      unsupported(
        traitName,
        s"direct method `${method.name}` must not declare method type parameters",
        method.pos
      )
    else Right(())

  private def eligibleInheritedMethod(
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
        traitName,
        s"inherited concrete method `${method.name}` must be public, unannotated, and free of unsupported modifiers",
        method.pos
      )
    else if method.status != DirectMethodStatus.Concrete then
      unsupported(
        traitName,
        s"inherited method `${method.name}` must be concrete",
        method.pos
      )
    else if method.typeParameters.nonEmpty then
      unsupported(
        traitName,
        s"inherited concrete method `${method.name}` must not declare method type parameters",
        method.pos
      )
    else Right(())

  private def inheritedTopology(
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, DirectMethodParameter] =
    method.parameterClauses match
      case List(clause) =>
        if clause.isContextual || clause.isImplicit || clause.isGiven then
          unsupported(
            traitName,
            s"inherited concrete method `${method.name}` parameter clause must be ordinary and non-contextual",
            clause.pos
          )
        else if clause.parameters.size != 1 then
          unsupported(
            traitName,
            s"inherited concrete method `${method.name}` requires exactly one ordinary parameter; found ${clause.parameters.size}",
            clause.pos
          )
        else Right(clause.parameters.head)
      case clauses =>
        unsupported(
          traitName,
          s"inherited concrete method `${method.name}` requires exactly one ordinary parameter clause; found ${clauses.size}",
          method.pos
        )

  private def inheritedParameterType(
      traitName: String,
      method: DirectMethod,
      parameter: DirectMethodParameter,
      enclosingTypeParameterName: String
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
        traitName,
        s"inherited concrete method `${method.name}` parameter `${parameter.name}` must be ordinary, non-defaulted, and unmodified",
        parameter.pos
      )
    else
      parameter.parameterType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosingTypeParameterName => Right(())
        case _ =>
          unsupported(
            traitName,
            s"inherited concrete method `${method.name}` parameter `${parameter.name}` must use enclosing type parameter `$enclosingTypeParameterName`",
            parameter.typePos
          )

  private def parameterlessTopology(
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, Unit] =
    if method.parameterClauses.isEmpty then Right(())
    else
      unsupported(
        traitName,
        s"parameterless method `${method.name}` must declare no parameter clauses; found ${method.parameterClauses.size}",
        method.pos
      )

  private def binaryTopology(
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, List[DirectMethodParameter]] =
    method.parameterClauses match
      case List(clause) =>
        if clause.isContextual || clause.isImplicit || clause.isGiven then
          unsupported(
            traitName,
            s"binary method `${method.name}` parameter clause must be ordinary and non-contextual",
            clause.pos
          )
        else if clause.parameters.size != 2 then
          unsupported(
            traitName,
            s"binary method `${method.name}` requires exactly two ordinary parameters; found ${clause.parameters.size}",
            clause.pos
          )
        else Right(clause.parameters)
      case clauses =>
        unsupported(
          traitName,
          s"binary method `${method.name}` requires exactly one ordinary parameter clause; found ${clauses.size}",
          method.pos
        )

  private def binaryParameter(
      traitName: String,
      method: DirectMethod,
      parameter: DirectMethodParameter,
      enclosingTypeParameterName: String
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
        traitName,
        s"binary method `${method.name}` parameter `${parameter.name}` must be ordinary, non-defaulted, and unmodified",
        parameter.pos
      )
    else
      parameter.parameterType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosingTypeParameterName => Right(())
        case _ =>
          unsupported(
            traitName,
            s"binary method `${method.name}` parameter `${parameter.name}` must use enclosing type parameter `$enclosingTypeParameterName`",
            parameter.typePos
          )

  private def enclosingResult(
      traitName: String,
      role: String,
      method: DirectMethod,
      enclosingTypeParameterName: String
  ): Either[ExpansionDiagnostic, Unit] =
    method.resultType match
      case DirectTypeShape.EnclosingTypeParameter(name, _)
          if name == enclosingTypeParameterName => Right(())
      case _ =>
        unsupported(
          traitName,
          s"$role method `${method.name}` result type must use enclosing type parameter `$enclosingTypeParameterName`",
          method.resultTypePos
        )

  private def freshCarrierName(stem: String, occupied: Set[String]): String =
    (0 to occupied.size)
      .iterator
      .map(index => if index == 0 then stem else s"$stem$index")
      .find(name => !occupied.contains(name))
      .getOrElse(stem)

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
      traitName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, A] =
    Left(
      ExpansionDiagnostic(
        s"unsupported @instance source shape for `$traitName`: $reason",
        pos
      )
    )
