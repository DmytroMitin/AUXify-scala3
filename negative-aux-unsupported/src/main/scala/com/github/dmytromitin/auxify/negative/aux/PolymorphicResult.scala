package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

@aux
trait PolymorphicResult[N <: Nat, M <: Nat]:
  type Out[X] <: Nat
