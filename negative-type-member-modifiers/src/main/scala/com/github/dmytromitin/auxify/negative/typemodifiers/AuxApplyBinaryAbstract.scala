package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyBinaryAbstract[N <: Nat, M <: Nat]:
  infix type Out[X, Y] <: Nat

object AuxApplyBinaryAbstract:
  val retained = 41
