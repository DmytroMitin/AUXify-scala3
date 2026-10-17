package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.apply

@apply
trait ApplyAliasTail[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Left = N
  type Copy = Out

object ApplyAliasTail:
  val retained = 41
