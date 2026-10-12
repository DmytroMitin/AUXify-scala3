package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxNestedTraitTail[A]:
  def combine(a: A, b: A): A
  trait Nested
