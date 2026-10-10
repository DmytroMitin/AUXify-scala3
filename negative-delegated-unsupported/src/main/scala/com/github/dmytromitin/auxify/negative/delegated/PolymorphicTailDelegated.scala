package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait PolymorphicTailDelegated[A]:
  def show(a: A): String
  def other[B](a: A): A = a
