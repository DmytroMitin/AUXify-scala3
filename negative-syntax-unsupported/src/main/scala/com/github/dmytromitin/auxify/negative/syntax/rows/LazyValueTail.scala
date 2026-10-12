package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxLazyValueTail[A]:
  def combine(a: A, b: A): A
  lazy val other: A = ???
