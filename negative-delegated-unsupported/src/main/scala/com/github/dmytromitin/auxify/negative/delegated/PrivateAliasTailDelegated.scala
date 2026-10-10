package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait PrivateAliasTailDelegated[A]:
  def show(a: A): String
  private type Item = A
