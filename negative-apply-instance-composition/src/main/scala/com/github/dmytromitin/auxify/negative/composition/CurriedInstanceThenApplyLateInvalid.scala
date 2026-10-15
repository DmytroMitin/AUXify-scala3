package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
@apply
trait CurriedInstanceThenApplyLateInvalid[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def invalid(left: A)(right: A): A = left
