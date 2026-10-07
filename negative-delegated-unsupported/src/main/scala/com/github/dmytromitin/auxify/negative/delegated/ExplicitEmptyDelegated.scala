package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ExplicitEmptyDelegated[A]:
  def empty(): A
