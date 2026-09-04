package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait LateInstanceRejection[A]:
  def empty: A
  def combine(a: A, a1: A): A
  val extra: A
