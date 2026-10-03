package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait AliasFirst[A]:
  type Item = A
  def empty: A
  def combine(a: A, a1: A): A
