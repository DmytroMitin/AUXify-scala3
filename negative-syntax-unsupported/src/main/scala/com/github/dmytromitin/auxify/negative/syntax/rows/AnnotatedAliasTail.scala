package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxAnnotatedAliasTail[A]:
  def combine(a: A, b: A): A
  @deprecated("unsupported", "0")
  type Item = A
