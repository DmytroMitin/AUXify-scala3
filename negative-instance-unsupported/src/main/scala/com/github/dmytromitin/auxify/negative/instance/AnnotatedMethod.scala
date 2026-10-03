package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait AnnotatedMethod[A]:
  def empty: A
  @deprecated("unsupported", "")
  def combine(a: A, a1: A): A
