package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxAbstractTypeTail[A]:
  def combine(a: A, b: A): A
  type Item
