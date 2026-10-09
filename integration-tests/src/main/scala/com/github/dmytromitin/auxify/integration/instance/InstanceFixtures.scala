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
trait HasOut[A]:
  type Out

@instance
trait Container[Element0]:
  type Element

object Container:
  val preserved = 142

@instance
trait Curried[A]:
  def combine(a: A)(b: A): A

object Curried:
  val preserved = 242

@instance
trait CurriedCollision[Element]:
  def combineFunction(left: Element)(right: Element): Element

@instance
trait RichCurried[A]:
  def combine(a: A)(b: A): A
  type combineFunction = A
  def twice(a: A): A = combine(a)(a)
  type Value = A
  def fold3(a: A, b: A, c: A): A = combine(combine(a)(b))(c)

object RichCurried:
  val preserved = 243

@instance
trait ExistingRichCurried[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def twice(a: A): A = combine(a)(a)

object ExistingRichCurried:
  var instanceCalls = 0
  def instance[A](f: A => A => A): ExistingRichCurried[A] =
    instanceCalls += 1
    new ExistingRichCurried[A]:
      def combine(a: A)(b: A): A = f(a)(b)

@instance
trait ExistingOut[A]:
  type Out

object ExistingOut:
  var instanceCalls = 0
  def instance[A, Out0]: ExistingOut[A] { type Out = Out0 } =
    instanceCalls += 1
    new ExistingOut[A]:
      type Out = Out0

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
trait BinaryDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(a: A, a1: A): A = combine(a, a1)

object BinaryDerivedMonoid:
  val preserved = 91

@instance
trait TernaryDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

object TernaryDerivedMonoid:
  val preserved = 93

@instance
trait LargerDerivedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def fold5(a: Element, b: Element, c: Element, d: Element, e: Element): Element =
    select(select(select(select(a, b), c), d), e)

object LargerDerivedChoice:
  val preserved = 95

@instance
trait RichDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zeroLike: A = empty
  def twice(a: A): A = combine(a, a)
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

object RichDerivedMonoid:
  val preserved = 97

@instance
trait HeterogeneousDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  def twice(a: A): A = combine(a, a)
  type Value = A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

object HeterogeneousDerivedMonoid:
  val preserved = 98

@instance
trait BinaryDerivedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def selectAgain(first: Element, second: Element): Element = select(first, second)

@instance
trait ExistingBinaryDerived[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(a: A, a1: A): A = combine(a, a1)

object ExistingBinaryDerived:
  var instanceCalls = 0
  def instance[A](value: A, combineFunction: (A, A) => A): ExistingBinaryDerived[A] =
    instanceCalls += 1
    new ExistingBinaryDerived[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)

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
