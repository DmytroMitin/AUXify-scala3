package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait PolymorphicAliasTailDelegated[A]:
  def show(a: A): String
  type Item[B] = A
