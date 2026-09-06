package com.github.dmytromitin.auxify.integration.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait Monoid[A]:
  def empty: A
  def combine(a: A, a1: A): A

object Monoid:
  val preserved = 41

@instance
trait Choice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

@instance
trait Collision[Element]:
  def emptyValue: Element
  def merge(combineFunction: Element, right: Element): Element

@instance
trait DerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object DerivedMonoid:
  val preserved = 84

@instance
trait DerivedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def duplicate(value: Element): Element = select(value, value)
