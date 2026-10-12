package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxDefaultedTail[A]:
  def combine(a: A, b: A): A
  def other(a: A = ???): A = a
