package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait NestedClassTailDelegated[A]:
  def show(a: A): String
  class Nested
