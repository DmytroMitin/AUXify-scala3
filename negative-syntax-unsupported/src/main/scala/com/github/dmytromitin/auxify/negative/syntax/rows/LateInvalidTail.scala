package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxLateInvalidTail[A]:
  def combine(a: A, b: A): A
  type First = A
  def twice(a: A): A = combine(a, a)
  type Second = A
  val invalid: A = ???
