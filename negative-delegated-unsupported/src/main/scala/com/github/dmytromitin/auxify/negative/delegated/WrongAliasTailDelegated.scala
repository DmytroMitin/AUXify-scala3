package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait WrongAliasTailDelegated[A]:
  def show(a: A): String
  type Item = String
