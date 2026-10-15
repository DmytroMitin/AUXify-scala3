package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
@apply
trait CurriedAbstractVal[A]:
  def combine(a: A)(b: A): A
  val missing: A
