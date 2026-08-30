package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  AnnotatedClassBodyView,
  AnnotatedClassView,
  ExpansionDiagnostic
}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMethod,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}

private[internal] object DelegatedSourceShapeDecoder:
  final case class SourceShape(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      parameterName: String,
      resultTypeName: String
  )

  def decode(
      traitName: String,
      classView: AnnotatedClassView,
      bodyView: AnnotatedClassBodyView
  ): Either[ExpansionDiagnostic, SourceShape] =
    classView.typeParameters match
      case List(typeParameter)
          if typeParameter.variance == AnnotatedClassView.Variance.Invariant &&
            typeParameter.isOrdinaryUnbounded &&
            !typeParameter.hasContextBounds &&
            !typeParameter.isOrdinaryUpperBounded =>
        bodyView.members match
          case List(member) =>
            member.method match
              case Some(method) =>
                decodeMethod(traitName, typeParameter.name, method)
              case None =>
                unsupported(
                  traitName,
                  "the direct body member must be one method",
                  member.pos
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

  private def decodeMethod(
      traitName: String,
      typeParameterName: String,
      method: DirectMethod
  ): Either[ExpansionDiagnostic, SourceShape] =
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
    else
      method.parameterClauses match
        case List(clause) =>
          if clause.isContextual || clause.isImplicit || clause.isGiven then
            unsupported(
              traitName,
              s"direct method `${method.name}` parameter clause must be ordinary and non-contextual",
              clause.pos
            )
          else
            clause.parameters match
              case List(parameter) =>
                if
                  parameter.hasDefault || parameter.isContextual ||
                    parameter.isImplicit || parameter.isGiven ||
                    parameter.isVal || parameter.isVar
                then
                  unsupported(
                    traitName,
                    s"direct method `${method.name}` parameter `${parameter.name}` must be ordinary, non-defaulted, and unmodified",
                    parameter.pos
                  )
                else
                  parameter.parameterType match
                    case DirectTypeShape.EnclosingTypeParameter(name, _)
                        if name == typeParameterName =>
                      method.resultType match
                        case DirectTypeShape.NamedType(resultTypeName, _) =>
                          Right(
                            SourceShape(
                              traitName,
                              typeParameterName,
                              method.name,
                              parameter.name,
                              resultTypeName
                            )
                          )
                        case _ =>
                          unsupported(
                            traitName,
                            s"direct method `${method.name}` result type must be one unqualified named type",
                            method.resultTypePos
                          )
                    case _ =>
                      unsupported(
                        traitName,
                        s"direct method `${method.name}` parameter `${parameter.name}` must use enclosing type parameter `$typeParameterName`",
                        parameter.typePos
                      )
              case parameters =>
                unsupported(
                  traitName,
                  s"direct method `${method.name}` requires exactly one ordinary parameter; found ${parameters.size}",
                  clause.pos
                )
        case clauses =>
          unsupported(
            traitName,
            s"direct method `${method.name}` requires exactly one ordinary parameter clause; found ${clauses.size}",
            method.pos
          )

  private def unsupported[A](
      traitName: String,
      reason: String,
      pos: SrcPos
  ): Left[ExpansionDiagnostic, A] =
    Left(
      ExpansionDiagnostic(
        s"unsupported @delegated source shape for `$traitName`: $reason",
        pos
      )
    )
