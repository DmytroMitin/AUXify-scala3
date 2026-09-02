package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

@aux
trait LowerBoundedResult[N <: Nat, M <: Nat]:
  type Out >: Nothing <: Nat
