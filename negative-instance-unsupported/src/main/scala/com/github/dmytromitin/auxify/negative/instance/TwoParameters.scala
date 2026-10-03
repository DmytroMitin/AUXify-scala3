package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TwoParameters[A, B]:
  def empty: A
  def combine(a: A, a1: A): A
