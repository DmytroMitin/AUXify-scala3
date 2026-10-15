package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait AbstractTypeBoundedAlias[A]:
  type Out
  type Item <: A
