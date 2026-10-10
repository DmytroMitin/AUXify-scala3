package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait CurriedTailDelegated[A]:
  def show(a: A): String
  def other(a: A)(b: A): A = a
