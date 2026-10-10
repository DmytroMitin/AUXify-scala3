package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ContextualTailDelegated[A]:
  def show(a: A): String
  def other(using a: A): A = a
