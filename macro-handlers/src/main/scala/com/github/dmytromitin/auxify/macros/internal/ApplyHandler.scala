package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  ParadiseAnnotationExpander
}
import paradise3.api.helpers.{
  CompanionMethodConflictPolicy,
  ExpansionHelpers
}

final class ApplyHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.apply"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.RestrictedGenericTraitApply

  override val consumesExistingCompanion: Boolean = true

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    ExpansionHelpers.withAnnotatedClassView(input): view =>
      val typeParameterName = view.typeParameters.head.name
      ApplyDefinitionBuilder.lower(input.className, typeParameterName) match
        case Left(failure) =>
          ExpansionHelpers.rejected(
            s"${failure.code}: ${failure.detail}",
            input.annotatedClass
          )
        case Right(lowered) =>
          ExpansionHelpers.addMethodToCompanion(
            input,
            lowered.tree,
            CompanionMethodConflictPolicy.PreserveExisting
          )
