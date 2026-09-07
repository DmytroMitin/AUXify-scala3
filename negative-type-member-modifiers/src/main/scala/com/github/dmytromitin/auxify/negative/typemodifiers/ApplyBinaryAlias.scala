package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
trait ApplyBinaryAlias[N <: Nat, M <: Nat]:
  infix type Out[X, Y] = Nat

object ApplyBinaryAlias:
  val retained = 41
