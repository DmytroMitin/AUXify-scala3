package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{delegated, syntax}

@syntax
@delegated
trait SyntaxDelegatedRejection[A]:
  def combine(left: A, right: A): A
  type Item = A
