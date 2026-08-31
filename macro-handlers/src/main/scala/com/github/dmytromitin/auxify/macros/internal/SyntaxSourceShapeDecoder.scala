package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  AnnotatedClassBodyView,
  AnnotatedClassView,
  ExpansionDiagnostic
}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMemberKind,
  DirectMethod,
  DirectMethodParameter,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}
import paradise3.api.AnnotatedClassView.DefinitionKind

private[internal] object SyntaxSourceShapeDecoder:
  final case class SourceShape(
      traitName: String,
      enclosingTypeParameterName: String,
      extensionTypeParameterName: String,
      methodName: String,
      receiverParameterName: String,
      remainingParameterName: String,
      evidenceParameterName: String
  )

  def decode(
      classView: AnnotatedClassView,
      bodyView: AnnotatedClassBodyView
  ): Either[ExpansionDiagnostic, SourceShape] =
    val traitName = classView.className
    if !normalizedNameAvailable(traitName) then
      unsupported(
        String.valueOf(traitName),
        "requires an available normalized trait name",
        classView.classPos
      )
    else if traitName == "syntax" then
      unsupported(
        traitName,
        "trait name `syntax` conflicts with the fixed generated nested object name `syntax`",
        classView.classPos
      )
    else if !restrictedTraitEnvelope(classView) then
      unsupported(
        traitName,
        "requires the restricted top-level ordinary trait profile",
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
            case List(member) =>
              for
                method <- directMethod(traitName, member)
                _ <- eligibleMethod(traitName, method)
                parameters <- binaryTopology(traitName, method)
                _ <- enclosingParameter(
                  traitName,
                  method,
                  parameters.head,
                  typeParameter.name
                )
                _ <- enclosingParameter(
                  traitName,
                  method,
                  parameters(1),
                  typeParameter.name
                )
                _ <- enclosingResult(traitName, method, typeParameter.name)
                occupied = Set(
                  method.name,
                  parameters.head.name,
                  parameters(1).name
                )
              yield SourceShape(
                traitName = traitName,
                enclosingTypeParameterName = typeParameter.name,
                extensionTypeParameterName = freshTypeParameterName(
                  typeParameter.name,
                  Set(traitName)
                ),
                methodName = method.name,
                receiverParameterName = parameters.head.name,
                remainingParameterName = parameters(1).name,
                evidenceParameterName = freshEvidenceName(occupied)
              )
            case members =>
              unsupported(
                traitName,
                s"requires exactly one direct body member; found ${members.size}",
                bodyView.pos
              )
        case _ =>
          unsupported(
            traitName,
            "requires exactly one invariant unbounded enclosing type parameter",
            classView.classPos
          )

  private def restrictedTraitEnvelope(classView: AnnotatedClassView): Boolean =
    classView.definitionKind == DefinitionKind.Trait &&
      !classView.modifiers.isCase &&
      !classView.modifiers.isSealed &&
      !classView.constructorClauses.exists(_.parameters.nonEmpty)

  private def directMethod(
      traitName: String,
      member: AnnotatedClassBodyView.DirectMember
  ): Either[ExpansionDiagnostic, DirectMethod] =
    if member.kind != DirectMemberKind.Method then
      unsupported(
        traitName,
        "the direct body member must be a method",
        member.pos
      )
    else
      member.method match
        case Some(method) if normalizedNameAvailable(method.name) => Right(method)
        case Some(method) =>
          unsupported(
            traitName,
            "the direct method must have an available normalized name",
            method.pos
          )
        case None =>
          unsupported(
            traitName,
            "the direct body member must provide normalized method evidence",
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
        s"direct method `${method.name}` must not declare method type parameters in this first slice; historical Scala 2 @syntax support was broader",
        method.pos
      )
    else Right(())

  private def binaryTopology(
      traitName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, List[DirectMethodParameter]] =
    method.parameterClauses match
      case List(clause) =>
        if clause.isContextual || clause.isImplicit || clause.isGiven then
          unsupported(
            traitName,
            s"direct method `${method.name}` parameter clause must be ordinary and non-contextual",
            clause.pos
          )
        else if clause.parameters.size != 2 then
          unsupported(
            traitName,
            s"direct method `${method.name}` requires exactly two ordinary parameters; found ${clause.parameters.size}",
            clause.pos
          )
        else Right(clause.parameters)
      case clauses =>
        unsupported(
          traitName,
          s"direct method `${method.name}` requires exactly one ordinary parameter clause; found ${clauses.size}",
          method.pos
        )

  private def enclosingParameter(
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
        s"direct method `${method.name}` parameter `${String.valueOf(parameter.name)}` must be ordinary, non-defaulted, and unmodified",
        parameter.pos
      )
    else
      parameter.parameterType match
        case DirectTypeShape.EnclosingTypeParameter(name, _)
            if name == enclosingTypeParameterName => Right(())
        case _ =>
          unsupported(
            traitName,
            s"direct method `${method.name}` parameter `${parameter.name}` must use enclosing type parameter `$enclosingTypeParameterName`",
            parameter.typePos
          )

  private def enclosingResult(
      traitName: String,
      method: DirectMethod,
      enclosingTypeParameterName: String
  ): Either[ExpansionDiagnostic, Unit] =
    method.resultType match
      case DirectTypeShape.EnclosingTypeParameter(name, _)
          if name == enclosingTypeParameterName => Right(())
      case _ =>
        unsupported(
          traitName,
          s"direct method `${method.name}` result type must use enclosing type parameter `$enclosingTypeParameterName`",
          method.resultTypePos
        )

  private def freshEvidenceName(occupied: Set[String]): String =
    Iterator
      .from(0)
      .map(index => if index == 0 then "inst" else s"inst$index")
      .find(name => !occupied.contains(name))
      .getOrElse("inst")

  private def freshTypeParameterName(
      stem: String,
      occupied: Set[String]
  ): String =
    Iterator
      .from(0)
      .map(index => if index == 0 then stem else s"$stem$index")
      .find(name => !occupied.contains(name))
      .getOrElse(stem)

  private def normalizedNameAvailable(name: String): Boolean =
    name != null && name.nonEmpty && name != "<error>" && name != "<unknown>"

  private def unsupported[A](
      traitName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, A] =
    Left(
      ExpansionDiagnostic(
        s"unsupported @syntax source shape for `$traitName`: $reason",
        pos
      )
    )
