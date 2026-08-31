package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object SyntaxDefinitionBuilder:
  def module(shape: SyntaxSourceShapeDecoder.SourceShape): Defn.Object =
    val traitName = Type.Name(shape.traitName)
    val typeParameterName = Type.Name(shape.extensionTypeParameterName)
    val methodName = Term.Name(shape.methodName)
    val receiverName = Term.Name(shape.receiverParameterName)
    val remainingParameterName = Term.Name(shape.remainingParameterName)
    val evidenceName = Term.Name(shape.evidenceParameterName)

    val typeParameter: Type.Param = tparam"$typeParameterName"
    val typeParameters: List[Type.Param] = typeParameter :: Nil
    val typeArguments: List[Type] = typeParameterName :: Nil
    val target: Type = t"$traitName[..$typeArguments]"
    val receiverParameter: Term.Param =
      param"$receiverName: $typeParameterName"
    val remainingParameter: Term.Param =
      param"$remainingParameterName: $typeParameterName"
    val invocation: Term =
      q"$evidenceName.$methodName($receiverName, $remainingParameterName)"
    val forwardingMethod: Defn.Def =
      q"def $methodName($remainingParameter)(using $evidenceName: $target): $typeParameterName = $invocation"
    val extensionGroup: Defn.ExtensionGroup =
      q"extension [..$typeParameters]($receiverParameter) { $forwardingMethod }"
    val syntaxStats: List[Stat] = extensionGroup :: Nil

    q"object syntax { ..$syntaxStats }"
