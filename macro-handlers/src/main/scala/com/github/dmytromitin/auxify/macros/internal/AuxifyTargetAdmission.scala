package com.github.dmytromitin.auxify.macros.internal

import paradise3.api.{ExpansionDiagnostic, ExpansionTargetView}
import paradise3.api.ExpansionTargetView.{DefinitionKind, Variance}

/** AUXify-owned semantic applicability checks over the provider's normalized
  * syntactic target view.
  */
private[internal] object AuxifyTargetAdmission:
  def applyTarget(
      view: ExpansionTargetView
  ): Either[ExpansionDiagnostic, Unit] =
    val oneParameter = oneUnboundedTypeParameterTrait(view, "@apply")
    if oneParameter.isRight then oneParameter
    else
      val twoParameters = twoUpperBoundedTypeParameterTrait(view, "@apply")
      if twoParameters.isRight then twoParameters
      else
        val requirement =
          "requires either the one-unbounded-parameter restricted trait shape or the two-upper-bounded-parameter trait shape: both are top-level non-sealed ordinary traits with invariant ordinary type parameters and neither permits constructor/value parameters"
        val pos =
          oneParameter.left.toOption
            .map(_.pos)
            .filter(_.span.exists)
            .orElse(
              twoParameters.left.toOption.map(_.pos).filter(_.span.exists)
            )
            .getOrElse(view.classPos)
        Left(
          ExpansionDiagnostic(
            s"@apply $requirement; unsupported target `${view.className}` is outside the closed profile",
            pos
          )
        )

  def auxTarget(
      view: ExpansionTargetView
  ): Either[ExpansionDiagnostic, Unit] =
    twoUpperBoundedTypeParameterTrait(view, "@aux")

  def instanceOrDelegatedTarget(
      view: ExpansionTargetView,
      annotationLabel: String
  ): Either[ExpansionDiagnostic, Unit] =
    oneUnboundedTypeParameterTrait(view, annotationLabel)

  private def oneUnboundedTypeParameterTrait(
      view: ExpansionTargetView,
      annotationLabel: String
  ): Either[ExpansionDiagnostic, Unit] =
    val requirement =
      "requires one top-level non-sealed ordinary trait with exactly one invariant, ordinary unbounded type parameter and no constructor/value parameters"

    reject(view, annotationLabel, requirement)
      .orElse:
        Option.when(view.typeParameters.size != 1):
          ExpansionDiagnostic(
            s"$annotationLabel $requirement; found ${view.typeParameters.size} type parameters",
            view.typeParameters.headOption.map(_.pos).getOrElse(view.classPos)
          )
      .orElse:
        view.typeParameters.headOption.flatMap: parameter =>
          if parameter.variance != Variance.Invariant then
            Some(
              ExpansionDiagnostic(
                s"$annotationLabel $requirement; type parameter `${parameter.name}` is ${parameter.variance.toString.toLowerCase}",
                parameter.pos
              )
            )
          else if !parameter.isOrdinaryUnbounded || parameter.hasContextBounds then
            Some(
              ExpansionDiagnostic(
                s"$annotationLabel $requirement; type parameter `${parameter.name}` has an explicit or contextual bound",
                parameter.pos
              )
            )
          else None
      .orElse(constructorParameterRejection(view, annotationLabel, requirement))
      .toLeft(())

  private def twoUpperBoundedTypeParameterTrait(
      view: ExpansionTargetView,
      annotationLabel: String
  ): Either[ExpansionDiagnostic, Unit] =
    val requirement =
      "requires one top-level non-sealed ordinary trait with exactly two invariant, ordinary upper-bounded type parameters and no constructor/value parameters"

    reject(view, annotationLabel, requirement)
      .orElse:
        Option.when(view.typeParameters.size != 2):
          ExpansionDiagnostic(
            s"$annotationLabel $requirement; found ${view.typeParameters.size} type parameters",
            view.typeParameters.headOption.map(_.pos).getOrElse(view.classPos)
          )
      .orElse:
        view.typeParameters.find(_.variance != Variance.Invariant).map: parameter =>
          ExpansionDiagnostic(
            s"$annotationLabel $requirement; type parameter `${parameter.name}` is ${parameter.variance.toString.toLowerCase}",
            parameter.pos
          )
      .orElse:
        view.typeParameters
          .find(parameter =>
            !parameter.isOrdinaryUpperBounded || parameter.hasContextBounds
          )
          .map: parameter =>
            ExpansionDiagnostic(
              s"$annotationLabel $requirement; type parameter `${parameter.name}` is not an ordinary single upper-bounded parameter",
              parameter.pos
            )
      .orElse(constructorParameterRejection(view, annotationLabel, requirement))
      .toLeft(())

  private def reject(
      view: ExpansionTargetView,
      annotationLabel: String,
      requirement: String
  ): Option[ExpansionDiagnostic] =
    if view.definitionKind != DefinitionKind.Trait then
      Some(
        ExpansionDiagnostic(
          s"$annotationLabel $requirement; found class `${view.className}`",
          view.classPos
        )
      )
    else if view.modifiers.isCase then
      Some(
        ExpansionDiagnostic(
          s"$annotationLabel $requirement; case modifiers are unsupported",
          view.classPos
        )
      )
    else if view.modifiers.isSealed then
      Some(
        ExpansionDiagnostic(
          s"$annotationLabel $requirement; sealed trait `${view.className}` is unsupported",
          view.classPos
        )
      )
    else None

  private def constructorParameterRejection(
      view: ExpansionTargetView,
      annotationLabel: String,
      requirement: String
  ): Option[ExpansionDiagnostic] =
    view.constructorClauses.find(_.parameters.nonEmpty).map: clause =>
      ExpansionDiagnostic(
        s"$annotationLabel $requirement; trait constructor/value parameters are unsupported",
        clause.pos
      )
