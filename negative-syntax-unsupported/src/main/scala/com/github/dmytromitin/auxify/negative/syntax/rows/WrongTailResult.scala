package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxWrongTailResult[A]:
  def combine(a: A, b: A): A
  def other(a: A): String = a.toString
