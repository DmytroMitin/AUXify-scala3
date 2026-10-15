package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance, syntax}

@apply
@instance
@syntax
trait AbstractTypeThirdParticipant[A]:
  type Out
  type Item = A
  def identity(value: A): A = value
