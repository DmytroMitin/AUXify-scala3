package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxAliasTail[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Left = N
  type Copy = Out

object ApplyAuxAliasTail:
  val retained = 41
