package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionTargetBodyView,
  ExpansionTargetView
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

private[internal] object InstanceCurriedMethodSourceShapeDecoder:
  final case class SourceShape(
      traitName: String,
      enclosingTypeParameterName: String,
      methodName: String,
      firstParameterName: String,
      secondParameterName: String,
      carrierName: String
  )

  def decode(
      classView: ExpansionTargetView,
      bodyView: ExpansionTargetBodyView
  ): Either[ExpansionDiagnostic, SourceShape] =
    val traitName = classView.className
    for
      _ <- requireName(
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
            s"curried-method family requires exactly one enclosing type parameter; found ${parameters.size}",
            classView.classPos
          )
      _ <- requireName(
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
      member <- bodyView.members match
        case List(value) => Right(value)
        case members =>
          unsupported(
            traitName,
            s"curried-method family requires exactly one direct body member; found ${members.size}",
            bodyView.pos
          )
      method <- directMethod(traitName, member)
      _ <- eligibleMethod(traitName, method)
      clauses <- method.parameterClauses match
        case first :: second :: Nil => Right((first, second))
        case values =>
          unsupported(
            traitName,
            s"curried method `${method.name}` requires exactly two ordinary parameter clauses; found ${values.size}",
            method.pos
          )
      first <- unaryOrdinaryClause(traitName, method, "first", clauses._1)
      second <- unaryOrdinaryClause(traitName, method, "second", clauses._2)
      _ <- enclosingParameterType(
        traitName,
        method,
        "first",
        first,
        enclosing.name
      )
      _ <- enclosingParameterType(
        traitName,
        method,
        "second",
        second,
        enclosing.name
      )
      _ <- method.resultType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosing.name => Right(())
        case _ =>
          unsupported(
            traitName,
            s"curried method `${method.name}` result type must use enclosing type parameter `${enclosing.name}`",
            method.resultTypePos
          )
      carrier = freshCarrierName(
        "combineFunction",
        Set("instance", method.name, first.name, second.name)
      )
    yield SourceShape(
      traitName,
      enclosing.name,
      method.name,
      first.name,
      second.name,
      carrier
    )

  private def directMethod(
      traitName: String,
      member: DirectMember
  ): Either[ExpansionDiagnostic, DirectMethod] =
    if member.kind != DirectMemberKind.Method then
      unsupported(
        traitName,
        s"direct body member at index 0 must be a curried method; found ${memberKindLabel(member.kind)}",
        member.pos
      )
    else
      member.method match
        case Some(method) if normalizedNameAvailable(method.name) => Right(method)
        case Some(method) =>
          unsupported(
            traitName,
            "direct curried method must have an available normalized name",
            method.pos
          )
        case None =>
          unsupported(
            traitName,
            "direct body member at index 0 must provide normalized method evidence",
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
        s"curried method `${method.name}` must be public, unannotated, and free of unsupported modifiers",
        method.pos
      )
    else if method.status != DirectMethodStatus.Abstract then
      unsupported(
        traitName,
        s"curried method `${method.name}` must be abstract",
        method.pos
      )
    else if method.typeParameters.nonEmpty then
      unsupported(
        traitName,
        s"curried method `${method.name}` must not declare method type parameters",
        method.pos
      )
    else Right(())

  private def unaryOrdinaryClause(
      traitName: String,
      method: DirectMethod,
      role: String,
      clause: ExpansionTargetBodyView.DirectMethodParameterClause
  ): Either[ExpansionDiagnostic, DirectMethodParameter] =
    if clause.isContextual || clause.isImplicit || clause.isGiven then
      unsupported(
        traitName,
        s"curried method `${method.name}` $role parameter clause must be ordinary and non-contextual",
        clause.pos
      )
    else
      clause.parameters match
        case List(parameter) => Right(parameter)
        case parameters =>
          unsupported(
            traitName,
            s"curried method `${method.name}` $role parameter clause requires exactly one ordinary parameter; found ${parameters.size}",
            clause.pos
          )

  private def enclosingParameterType(
      traitName: String,
      method: DirectMethod,
      role: String,
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
        s"curried method `${method.name}` $role parameter `${parameter.name}` must be named, ordinary, non-defaulted, and unmodified",
        parameter.pos
      )
    else
      parameter.parameterType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosingTypeParameterName => Right(())
        case _ =>
          unsupported(
            traitName,
            s"curried method `${method.name}` $role parameter `${parameter.name}` must use enclosing type parameter `$enclosingTypeParameterName`",
            parameter.typePos
          )

  private def requireName(
      traitName: String,
      name: String,
      reason: String,
      pos: SrcPos
  ): Either[ExpansionDiagnostic, Unit] =
    if normalizedNameAvailable(name) then Right(())
    else unsupported(traitName, reason, pos)

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
