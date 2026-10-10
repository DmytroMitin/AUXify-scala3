package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait AbstractTailDelegated[A]:
  def show(a: A): String
  def other(a: A): A
