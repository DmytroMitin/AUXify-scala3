package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait ProtectedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  protected def twice(a: A): A = combine(a, a)
