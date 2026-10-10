package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait EmptyClauseTailDelegated[A]:
  def show(a: A): String
  def other(): A = ???
