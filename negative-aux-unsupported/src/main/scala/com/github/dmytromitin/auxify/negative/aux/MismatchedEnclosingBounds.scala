package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

trait Nat
trait Other

@aux
trait MismatchedEnclosingBounds[N <: Nat, M <: Other]:
  type Out <: Nat
