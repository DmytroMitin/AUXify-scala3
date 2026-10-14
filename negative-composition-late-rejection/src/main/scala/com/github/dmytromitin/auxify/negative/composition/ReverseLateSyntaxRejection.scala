package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, syntax}

@syntax
@apply
trait ReverseLateSyntaxRejection[Element]:
  def merge(left: Element, right: Element): Element
  def duplicate(value: Element): Element = merge(value, value)
  type Item = Element
  def choose(first: Element, second: Element, third: Element): Element = second
  val invalid: Element = ???
