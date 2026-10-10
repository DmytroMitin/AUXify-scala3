package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ValueTailDelegated[A]:
  def show(a: A): String
  val other: A = ???
