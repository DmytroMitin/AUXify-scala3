package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait VariableTailDelegated[A]:
  def show(a: A): String
  var other: A = ???
