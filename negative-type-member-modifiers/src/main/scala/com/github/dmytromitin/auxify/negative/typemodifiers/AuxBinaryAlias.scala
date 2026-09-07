package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
trait AuxBinaryAlias[N <: Nat, M <: Nat]:
  infix type Out[X, Y] = Nat

object AuxBinaryAlias:
  val retained = 41
