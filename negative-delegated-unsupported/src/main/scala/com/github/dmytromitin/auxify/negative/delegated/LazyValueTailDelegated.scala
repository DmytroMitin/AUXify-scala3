package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait LazyValueTailDelegated[A]:
  def show(a: A): String
  lazy val other: A = ???
