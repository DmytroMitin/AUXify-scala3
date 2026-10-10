package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait WrongTailTypesDelegated[A]:
  def show(a: A): String
  def other(a: String): A = ???
