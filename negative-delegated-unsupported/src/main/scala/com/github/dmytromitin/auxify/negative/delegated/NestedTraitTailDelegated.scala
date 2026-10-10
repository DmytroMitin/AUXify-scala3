package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait NestedTraitTailDelegated[A]:
  def show(a: A): String
  trait Nested
