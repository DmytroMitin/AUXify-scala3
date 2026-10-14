package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance, syntax}

@apply
@syntax
@instance
trait ApplySyntaxThirdParticipantRejection[A]:
  def combine(left: A, right: A): A
  type Item = A
  def twice(value: A): A = combine(value, value)
