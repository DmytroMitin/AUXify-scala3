package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, syntax}

@apply
@syntax
trait RichMonoid[A]:
  def combine(left: A, right: A): A
  type Item = A
  def twice(value: A): A = combine(value, value)
  type Value = A
  def fold3(first: A, second: A, third: A): A =
    combine(combine(first, second), third)

object RichMonoid:
  val preservedBefore = 40
  given RichMonoid[Int] with
    def combine(left: Int, right: Int): Int = left + right
  val preservedAfter = 44

@syntax
@apply
trait ReverseRichMonoid[Element]:
  def merge(left: Element, right: Element): Element
  def duplicate(value: Element): Element = merge(value, value)
  type Item = Element

object ReverseRichMonoid:
  given ReverseRichMonoid[Int] with
    def merge(left: Int, right: Int): Int = left + right
