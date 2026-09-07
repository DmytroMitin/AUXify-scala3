package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
trait ApplyBinaryAbstract[N <: Nat, M <: Nat]:
  infix type Out[X, Y] <: Nat

object ApplyBinaryAbstract:
  val retained = 41
