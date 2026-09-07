package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
trait AuxBinaryAbstract[N <: Nat, M <: Nat]:
  infix type Out[X, Y] <: Nat

object AuxBinaryAbstract:
  val retained = 41
