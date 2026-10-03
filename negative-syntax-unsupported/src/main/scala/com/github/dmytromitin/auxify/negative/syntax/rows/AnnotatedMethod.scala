package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.{syntax as auxSyntax}

@com.github.dmytromitin.auxify.macros.syntax
trait SyntaxAnnotatedMethod[A]:
  @deprecated("unsupported", "0")
  def combine(a: A, a1: A): A
