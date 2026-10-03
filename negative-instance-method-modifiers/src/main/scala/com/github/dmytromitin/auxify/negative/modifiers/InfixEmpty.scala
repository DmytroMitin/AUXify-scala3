package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.instance

@instance
trait InfixEmpty[A]:
  infix def empty: A
  def combine(a: A, a1: A): A
