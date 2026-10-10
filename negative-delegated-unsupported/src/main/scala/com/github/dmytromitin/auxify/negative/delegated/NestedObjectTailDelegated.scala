package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait NestedObjectTailDelegated[A]:
  def show(a: A): String
  object Nested
