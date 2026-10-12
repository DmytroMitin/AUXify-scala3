package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxInfixTail[A]:
  def combine(a: A, b: A): A
  infix def other(a: A): A = a
