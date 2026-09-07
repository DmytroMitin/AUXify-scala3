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
trait ZeroMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero: A = empty

object ZeroMonoid:
  val preserved = 105

@instance
trait ZeroChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def defaultValue: Element = fallback

@instance
trait ExistingZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero: A = empty

object ExistingZero:
  var instanceCalls = 0
  def instance[A](value: A, combineFunction: (A, A) => A): ExistingZero[A] =
    instanceCalls += 1
    new ExistingZero[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)

@instance
trait DerivedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def duplicate(value: Element): Element = select(value, value)

@instance
trait WrappedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

object WrappedMonoid:
  val preserved = 126

@instance
trait WrappedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  type Value = Element

@instance
trait ExistingWrapped[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

object ExistingWrapped:
  var instanceCalls = 0
  def instance[A](value: A, combineFunction: (A, A) => A): ExistingWrapped[A] =
    instanceCalls += 1
    new ExistingWrapped[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
