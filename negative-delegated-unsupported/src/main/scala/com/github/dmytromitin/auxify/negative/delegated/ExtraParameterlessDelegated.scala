package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ExtraParameterlessDelegated[A]:
  def empty: A
  val extra: Int
