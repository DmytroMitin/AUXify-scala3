package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongConcreteArity[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A, a1: A, a2: A): A = combine(combine(a, a1), a2)
