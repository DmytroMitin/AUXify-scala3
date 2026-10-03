package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TwoAliases[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  type Value = A
