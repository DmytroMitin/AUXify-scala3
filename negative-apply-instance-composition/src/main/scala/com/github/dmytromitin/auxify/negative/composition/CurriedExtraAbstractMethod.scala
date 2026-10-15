package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait CurriedExtraAbstractMethod[A]:
  def combine(a: A)(b: A): A
  def missing(value: A): A
