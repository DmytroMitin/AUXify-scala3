package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait AliasMethodForward[A]:
  def empty: A
  def combine(left: A, right: A): A
  def identity(value: Item): Item = value
  type Item = A
