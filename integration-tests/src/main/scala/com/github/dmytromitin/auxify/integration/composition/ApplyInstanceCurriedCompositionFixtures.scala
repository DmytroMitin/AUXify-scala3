package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait CurriedApplyThenInstance[A]:
  def combine(a: A)(b: A): A

@instance
@apply
trait CurriedInstanceThenApply[Element]:
  def merge(left: Element)(right: Element): Element

@apply
@instance
trait RichCurriedApplyThenInstance[A]:
  def combine(a: A)(b: A): A
  type Item = A
  type Copy = Item
  def twice(a: Item): Copy = combine(a)(a)
  type Value = Copy
  type Branch = Item
  def pick(first: A, second: Branch): Value = first
  def fold3(a: Value, b: A, c: Branch): Copy = combine(combine(a)(b))(c)

@instance
@apply
trait RichCurriedInstanceThenApply[Element]:
  def merge(left: Element)(right: Element): Element
  def duplicate(value: Element): Element = merge(value)(value)
  type Item = Element
  type Copy = Item
  type More = Copy
  type Branch = Item
  def retain(value: More): Branch = value

@instance
@apply
trait CurriedArityTail[A]:
  def combine(a: A)(b: A): A
  def fortyTwo: A = 42.asInstanceOf[A]
  type Item = A
  def unary(value: A): A = value
  def binary(first: A, second: A): A = combine(first)(second)
  type Value = A
  def quaternary(a: A, b: A, c: A, d: A): A = combine(combine(a)(b))(combine(c)(d))

@apply
@instance
trait CurriedCarrierCollision[Element]:
  def merge(combineFunction: Element)(combineFunction1: Element): Element
  type combineFunction3 = Element
  def keep(combineFunction2: Element): Element = combineFunction2

@apply
@instance
trait CurriedExistingApply[A]:
  def combine(a: A)(b: A): A

object CurriedExistingApply:
  var applyCalls = 0
  def apply[A](using value: CurriedExistingApply[A]): CurriedExistingApply[A] =
    applyCalls += 1
    value

@instance
@apply
trait CurriedExistingInstance[A]:
  def combine(a: A)(b: A): A

object CurriedExistingInstance:
  var instanceCalls = 0
  def instance[A](f: A => A => A): CurriedExistingInstance[A] =
    instanceCalls += 1
    new CurriedExistingInstance[A]:
      def combine(a: A)(b: A): A = f(a)(b)

@apply
@instance
trait CurriedExistingBoth[A]:
  def combine(a: A)(b: A): A

object CurriedExistingBoth:
  var applyCalls = 0
  var instanceCalls = 0
  val retained = 17
  def apply[A](using value: CurriedExistingBoth[A]): CurriedExistingBoth[A] =
    applyCalls += 1
    value
  def instance[A](f: A => A => A): CurriedExistingBoth[A] =
    instanceCalls += 1
    new CurriedExistingBoth[A]:
      def combine(a: A)(b: A): A = f(a)(b)
