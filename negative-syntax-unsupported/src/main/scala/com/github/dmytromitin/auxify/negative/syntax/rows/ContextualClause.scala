package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.{syntax as auxSyntax}

@com.github.dmytromitin.auxify.macros.syntax
trait SyntaxContextualClause[A]:
  def combine(using a: A, a1: A): A
