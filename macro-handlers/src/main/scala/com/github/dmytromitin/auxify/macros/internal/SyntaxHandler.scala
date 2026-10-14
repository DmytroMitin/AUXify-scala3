package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  DefinitionPlacement,
  ExpansionDiagnostic,
  ExpansionEdit,
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetBodyView,
  ExpansionTargetKind
}
import paradise3.api.helpers.{
  ExpansionTransforms,
  MemberConflictPolicy,
  MissingCompanionPolicy
}

import quasiquotes.definitions.dotty.ExtensionModulePeerBridge

import scala.meta.Defn

final class SyntaxHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.syntax"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    SyntaxHandler.expandWithLowering(input):
      (definition, virtualSourceName, context) =>
        ExtensionModulePeerBridge.lower(
          definition,
          virtualSourceName
        )(using context)

private[internal] object SyntaxHandler:
  type Lowering = (
      Defn.Object,
      String,
      Context
  ) => Either[
    ExtensionModulePeerBridge.Failure,
    ExtensionModulePeerBridge.Lowered
  ]

  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val syntaxName = "com.github.dmytromitin.auxify.macros.syntax"
  private val admittedTailOrders = List(
    List(applyName, syntaxName),
    List(syntaxName, applyName)
  )

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      for
        targetView <- input.targetView
        bodyView <- input.targetBodyView
        typeStructure <-
          if bodyView.members.exists(
              _.kind == ExpansionTargetBodyView.DirectMemberKind.Type
            ) then input.targetTypeStructureView.map(Some(_))
          else Right(None)
        shape <- SyntaxSourceShapeDecoder.decode(
          targetView,
          bodyView,
          typeStructure
        )
        _ <-
          if
            bodyView.members.sizeCompare(1) > 0 &&
              input.sourceOrderedHandledAnnotationNames.exists(_ != syntaxName)
          then
            if admittedTailOrders.contains(input.sourceOrderedHandledAnnotationNames)
            then AuxifyTargetAdmission.applyTarget(targetView)
            else
              Left(
                ExpansionDiagnostic(
                  s"unsupported @syntax composition shape for `${input.primary.name}`: inherited concrete tails require exactly the @apply and @syntax participant cohort",
                  input.currentAnnotation.sourcePos
                )
              )
          else Right(())
        lowered <- lower(
          SyntaxDefinitionBuilder.module(shape),
          s"AuxifyGenerated${shape.traitName}Syntax.scala",
          summon[Context]
        ).left.map(failure =>
          ExpansionDiagnostic(
            s"${failure.code}: ${failure.detail}",
            input.currentAnnotation.sourcePos
          )
        )
        edit <- ExpansionEdit.start(input)
        updated <- ExpansionTransforms.placeMemberInCompanion(
          lowered.tree,
          MissingCompanionPolicy.Create(
            ExpansionTargetKind.Object,
            DefinitionPlacement.AfterPrimary
          ),
          MemberConflictPolicy.PreserveExisting
        )(edit)
      yield updated
