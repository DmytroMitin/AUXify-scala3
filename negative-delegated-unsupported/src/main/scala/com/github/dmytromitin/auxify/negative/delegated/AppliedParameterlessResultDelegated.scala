package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait AppliedParameterlessResultDelegated[A]:
  def empty: List[A]
