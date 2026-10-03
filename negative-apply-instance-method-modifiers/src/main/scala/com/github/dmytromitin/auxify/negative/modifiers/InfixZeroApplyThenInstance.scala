package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait InfixZeroApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def zero: A = empty
