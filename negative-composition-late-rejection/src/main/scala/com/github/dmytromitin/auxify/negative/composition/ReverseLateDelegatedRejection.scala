package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, delegated}

@delegated
@apply
trait ReverseLateDelegatedRejection[A]:
  def show(a: A): String
  type Item = A
  def duplicate(a: A): A = a
  val invalid: A
