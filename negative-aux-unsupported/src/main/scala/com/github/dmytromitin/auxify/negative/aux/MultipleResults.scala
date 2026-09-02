package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

@aux
trait MultipleResults[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Extra <: Nat
