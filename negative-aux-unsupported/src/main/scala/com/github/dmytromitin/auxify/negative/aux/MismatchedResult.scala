package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

@aux
trait MismatchedResult[N <: Nat, M <: Nat]:
  type Out <: Other
