package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyAliasTail[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Left = N
  type Copy = Out

object AuxApplyAliasTail:
  val retained = 41
