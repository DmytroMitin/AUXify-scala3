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

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

final class InstanceHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.instance"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    InstanceHandler.expandWithLowering(input): (shape, context) =>
      shape match
        case InstanceHandler.SourceShape.Methods(value) =>
          InstanceDefinitionBuilder.lower(value)(using context)
        case InstanceHandler.SourceShape.AbstractTypeMember(value) =>
          InstanceAbstractTypeMemberDefinitionBuilder.lower(value)(using context)
        case InstanceHandler.SourceShape.CurriedMethod(value) =>
          InstanceCurriedMethodDefinitionBuilder.lower(value)(using context)

private[internal] object InstanceHandler:
  enum SourceShape:
    case Methods(value: InstanceSourceShapeDecoder.SourceShape)
    case AbstractTypeMember(
        value: InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape
    )
    case CurriedMethod(
        value: InstanceCurriedMethodSourceShapeDecoder.SourceShape
    )

  type Lowering = (
      SourceShape,
      Context
  ) => Either[
    InstanceFactoryPeerBridge.Failure,
    InstanceFactoryPeerBridge.Lowered
  ]

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      for
        classView <- input.targetView
        _ <- AuxifyTargetAdmission.instanceOrDelegatedTarget(
          classView,
          "@instance"
        )
        bodyView <- input.targetBodyView
        typeStructure <-
          if bodyView.members.exists(
              _.kind == ExpansionTargetBodyView.DirectMemberKind.Type
            ) then input.targetTypeStructureView.map(Some(_))
          else Right(None)
        shape <- bodyView.members match
          case List(member)
              if member.kind == ExpansionTargetBodyView.DirectMemberKind.Type =>
            typeStructure match
              case Some(view) =>
                InstanceAbstractTypeMemberSourceShapeDecoder
                  .decode(classView, bodyView, view)
                  .map(SourceShape.AbstractTypeMember.apply)
              case None =>
                Left(
                  ExpansionDiagnostic(
                    s"unsupported @instance source shape for `${classView.className}`: direct body member at index 0 must provide normalized type-member evidence",
                    member.pos
                  )
                )
          case members
              if curriedFirstCandidate(members) =>
            InstanceCurriedMethodSourceShapeDecoder
              .decode(classView, bodyView, typeStructure)
              .map(SourceShape.CurriedMethod.apply)
          case _ =>
            InstanceSourceShapeDecoder
              .decode(classView, bodyView, typeStructure)
              .map(SourceShape.Methods.apply)
        lowered <- lower(shape, summon[Context]).left.map(failure =>
          ExpansionDiagnostic(
            s"${failure.code}: ${failure.detail}",
            input.currentAnnotation.sourcePos
          )
        )
        edit <- ExpansionEdit.start(input)
        placed <- ExpansionTransforms.placeMemberInCompanion(
          lowered.tree,
          MissingCompanionPolicy.Create(
            ExpansionTargetKind.Object,
            DefinitionPlacement.AfterPrimary
          ),
          MemberConflictPolicy.PreserveExisting
        )(edit)
      yield placed

  private def curriedFirstCandidate(
      members: List[ExpansionTargetBodyView.DirectMember]
  ): Boolean =
    members match
      case first :: tail
          if first.kind == ExpansionTargetBodyView.DirectMemberKind.Method =>
        first.method.exists: method =>
          val clauseCount = method.parameterClauses.size
          tail.isEmpty ||
          clauseCount >= 2 ||
          clauseCount == 1 && !tail.headOption.exists: next =>
            next.kind == ExpansionTargetBodyView.DirectMemberKind.Method &&
            next.method.exists(
              _.status == ExpansionTargetBodyView.DirectMethodStatus.Abstract
            )
      case _ => false
