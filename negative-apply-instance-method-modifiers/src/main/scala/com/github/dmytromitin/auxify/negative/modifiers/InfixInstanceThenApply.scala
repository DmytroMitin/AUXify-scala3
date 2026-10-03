package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
@apply
trait InfixInstanceThenApply[A]:
  infix def empty: A
  def combine(a: A, a1: A): A
