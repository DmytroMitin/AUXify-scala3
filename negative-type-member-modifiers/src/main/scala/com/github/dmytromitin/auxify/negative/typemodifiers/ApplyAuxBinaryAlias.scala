package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxBinaryAlias[N <: Nat, M <: Nat]:
  infix type Out[X, Y] = Nat

object ApplyAuxBinaryAlias:
  val retained = 41
