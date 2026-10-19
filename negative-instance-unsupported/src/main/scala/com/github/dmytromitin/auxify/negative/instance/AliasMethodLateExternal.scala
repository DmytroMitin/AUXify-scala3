package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait AliasMethodLateExternal[A]:
  def empty: A
  def combine(left: A, right: A): A
  type Item = A
  def identity(value: Item): Item = value
  type Copy = Item
  def invalid(value: String): Copy = empty
