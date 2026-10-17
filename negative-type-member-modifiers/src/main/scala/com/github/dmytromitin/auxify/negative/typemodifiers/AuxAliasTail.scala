package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.aux

@aux
trait AuxAliasTail[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Left = N
  type Copy = Nat

object AuxAliasTail:
  val retained = 41
