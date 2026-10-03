package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ConcreteDelegated[A]:
  def show(a: A): String = a.toString
