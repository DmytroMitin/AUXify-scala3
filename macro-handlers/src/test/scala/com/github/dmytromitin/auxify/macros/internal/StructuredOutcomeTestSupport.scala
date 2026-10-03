package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.Context
import paradise3.api.*

private[internal] final case class StructuredExpansionOutput(
    primary: untpd.TypeDef,
    companion: Option[untpd.ModuleDef]
)

private[internal] object StructuredOutcomeTestSupport:
  def materialize(
      input: ExpansionInput,
      changes: ExpansionChanges
  )(using Context): StructuredExpansionOutput =
    val primary = applyPrimary(input.primary, changes.primary) match
      case ExpansionTarget.Class(tree) => tree
      case ExpansionTarget.Trait(tree) => tree
      case other =>
        throw new IllegalArgumentException(
          s"test fixture requires a class or trait primary, found ${other.kind}"
        )

    val companion = changes.companion match
      case CompanionChange.Preserve =>
        input.companion.map(asModule)
      case CompanionChange.Merge(patches) =>
        input.companion.map(value => asModule(applyPatches(value, patches)))
      case CompanionChange.Replace(value) =>
        Some(asModule(value))
      case CompanionChange.Create(value, _) =>
        Some(asModule(value))
      case CompanionChange.Delete =>
        None

    StructuredExpansionOutput(primary, companion)

  private def applyPrimary(
      primary: ExpansionTarget,
      change: PrimaryChange
  )(using Context): ExpansionTarget =
    change match
      case PrimaryChange.Preserve       => primary
      case PrimaryChange.Merge(patches) => applyPatches(primary, patches)
      case PrimaryChange.Replace(value) => value
      case PrimaryChange.Delete =>
        throw new IllegalArgumentException("test fixture cannot materialize a deleted primary")

  private def applyPatches(
      target: ExpansionTarget,
      patches: List[TargetPatch]
  )(using Context): ExpansionTarget =
    patches.foldLeft(target):
      case (current, TargetPatch.AppendMembers(members)) =>
        rewriteTemplate(current, template =>
          cpy.Template(template)(
            template.constr,
            template.parentsOrDerived,
            template.derived,
            template.self,
            template.body ++ members
          )
        )
      case (current, TargetPatch.ReplaceAnnotations(annotations)) =>
        current match
          case ExpansionTarget.Class(tree) =>
            ExpansionTarget.Class(
              tree.withMods(Trees.mods(tree).withAnnotations(annotations)).asInstanceOf[TypeDef]
            )
          case ExpansionTarget.Trait(tree) =>
            ExpansionTarget.Trait(
              tree.withMods(Trees.mods(tree).withAnnotations(annotations)).asInstanceOf[TypeDef]
            )
          case ExpansionTarget.Object(tree) =>
            ExpansionTarget.Object(
              tree.withMods(Trees.mods(tree).withAnnotations(annotations)).asInstanceOf[ModuleDef]
            )
      case (current, TargetPatch.SetSelf(self)) =>
        rewriteTemplate(current, template =>
          cpy.Template(template)(
            template.constr,
            template.parentsOrDerived,
            template.derived,
            self.getOrElse(EmptyValDef),
            template.body
          )
        )

  private def rewriteTemplate(
      target: ExpansionTarget,
      update: Template => Template
  )(using Context): ExpansionTarget =
    target match
      case ExpansionTarget.Class(tree) =>
        ExpansionTarget.Class(cpy.TypeDef(tree)(tree.name, update(tree.rhs.asInstanceOf[Template])))
      case ExpansionTarget.Trait(tree) =>
        ExpansionTarget.Trait(cpy.TypeDef(tree)(tree.name, update(tree.rhs.asInstanceOf[Template])))
      case ExpansionTarget.Object(tree) =>
        ExpansionTarget.Object(cpy.ModuleDef(tree)(tree.name, update(tree.impl)))

  private def asModule(target: ExpansionTarget): untpd.ModuleDef =
    target match
      case ExpansionTarget.Object(tree) => tree
      case other =>
        throw new IllegalArgumentException(
          s"test fixture requires an object companion, found ${other.kind}"
        )
