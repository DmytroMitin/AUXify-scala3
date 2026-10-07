package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait WrongParameterlessResultDelegated[A]:
  def empty: String
