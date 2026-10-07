package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ContextualParameterlessDelegated[A]:
  def empty(using A): A
