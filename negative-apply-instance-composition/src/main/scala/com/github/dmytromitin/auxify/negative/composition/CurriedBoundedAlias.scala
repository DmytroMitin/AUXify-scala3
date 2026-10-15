package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait CurriedBoundedAlias[A]:
  def combine(a: A)(b: A): A
  type Item <: A
