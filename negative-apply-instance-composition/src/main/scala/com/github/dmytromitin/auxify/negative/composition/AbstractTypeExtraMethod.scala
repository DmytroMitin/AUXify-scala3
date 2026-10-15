package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait AbstractTypeExtraMethod[A]:
  type Out
  def missing(value: A): A
