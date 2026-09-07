package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyBinaryAlias[N <: Nat, M <: Nat]:
  infix type Out[X, Y] = Nat

object AuxApplyBinaryAlias:
  val retained = 41
