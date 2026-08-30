package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait PolymorphicResult[N <: Nat, M <: Nat]:
  type Out[X] <: Nat
