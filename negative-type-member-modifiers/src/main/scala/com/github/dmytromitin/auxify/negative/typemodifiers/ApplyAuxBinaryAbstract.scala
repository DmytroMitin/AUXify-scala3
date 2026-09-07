package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxBinaryAbstract[N <: Nat, M <: Nat]:
  infix type Out[X, Y] <: Nat

object ApplyAuxBinaryAbstract:
  val retained = 41
