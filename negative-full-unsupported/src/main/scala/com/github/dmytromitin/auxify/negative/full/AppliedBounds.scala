package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait AppliedBounds[N <: Box[N], M <: Box[M]]:
  type Out <: Nat
