package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait WrongTopologyDelegated[A]:
  def show(a: A, other: A): String
