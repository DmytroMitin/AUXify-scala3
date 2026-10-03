package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait ZeroFirst[A]:
  def zero: A = empty
  def empty: A
  def combine(a: A, a1: A): A
