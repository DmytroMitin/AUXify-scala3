package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxContextualTail[A]:
  def combine(a: A, b: A): A
  def other(using a: A): A = a
