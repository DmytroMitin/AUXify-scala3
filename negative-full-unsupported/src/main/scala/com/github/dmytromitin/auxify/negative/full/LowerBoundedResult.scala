package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait LowerBoundedResult[N <: Nat, M <: Nat]:
  type Out >: Nothing <: Nat
