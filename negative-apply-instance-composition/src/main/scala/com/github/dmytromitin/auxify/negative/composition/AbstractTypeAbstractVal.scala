package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
@apply
trait AbstractTypeAbstractVal[A]:
  type Out
  val missing: A
