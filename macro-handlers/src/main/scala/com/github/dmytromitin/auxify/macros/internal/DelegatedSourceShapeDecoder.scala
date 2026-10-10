package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.SrcPos

import paradise3.api.{
  ExpansionTargetBodyView,
  ExpansionTargetView,
  ExpansionDiagnostic,
  ExpansionTargetTypeStructureView
}
import paradise3.api.ExpansionTargetBodyView.{
  DirectMemberKind,
  DirectMethod,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}

private[internal] object DelegatedSourceShapeDecoder:
  enum Variant:
    case Unary(parameterName: String, resultTypeName: String)
    case Parameterless

  final case class SourceShape(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      variant: Variant,
      occupiedTermNames: Set[String] = Set.empty
  )

  def decode(
      traitName: String,
      classView: ExpansionTargetView,
      bodyView: ExpansionTargetBodyView,
      typeStructureView: Option[ExpansionTargetTypeStructureView] = None
  ): Either[ExpansionDiagnostic, SourceShape] =
    classView.typeParameters match
      case List(typeParameter)
          if typeParameter.variance == ExpansionTargetView.Variance.Invariant &&
            typeParameter.isOrdinaryUnbounded &&
            !typeParameter.hasContextBounds &&
            !typeParameter.isOrdinaryUpperBounded =>
        bodyView.members match
          case member :: tail =>
            if member.kind != DirectMemberKind.Method then
              unsupported(
                traitName,
                "the direct body member must be one method",
                member.pos
              )
            else
              member.method match
                case Some(method) if normalizedNameAvailable(method.name) =>
                  for
                    primary <- decodeMethod(traitName, typeParameter.name, method)
                    occupied <- InstanceInheritedConcreteTailValidator.validate(
                      traitName,
                      typeParameter.name,
                      tail,
                      firstBodyIndex = 1,
                      typeStructureView,
                      diagnosticAnnotation = "@delegated"
                    )
                  yield primary.copy(occupiedTermNames = occupied)
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
          case Nil =>
            unsupported(
              traitName,
              "requires exactly one direct body member; found 0",
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
        case Nil =>
          method.resultType match
            case DirectTypeShape.EnclosingTypeParameter(name, _)
                if name == typeParameterName =>
              Right(
                SourceShape(
                  traitName,
                  typeParameterName,
                  method.name,
                  Variant.Parameterless
                )
              )
            case _ =>
              unsupported(
                traitName,
                s"parameterless method `${method.name}` result type must use enclosing type parameter `$typeParameterName`",
                method.resultTypePos
              )
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
                              Variant.Unary(
                                parameter.name,
                                resultTypeName
                              )
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

  private def normalizedNameAvailable(name: String): Boolean =
    name != null && name.nonEmpty && name != "<error>" && name != "<unknown>"

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
