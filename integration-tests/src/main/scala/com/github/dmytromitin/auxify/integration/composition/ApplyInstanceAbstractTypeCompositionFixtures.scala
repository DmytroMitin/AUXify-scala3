package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait AbstractTypeApplyThenInstance[A]:
  type Out

@instance
@apply
trait AbstractTypeInstanceThenApply[Element]:
  type Result

@apply
@instance
trait RichHasOut[A]:
  type Out
  type Item = A
  type Copy = Item
  def identity(value: Item): Copy = value
  type Value = Copy
  type Branch = Item
  def pick(first: A, second: Branch): Value = first

@instance
@apply
trait ReverseHasOut[Element]:
  type Result
  def keep(value: Element): Element = value
  type Item = Element
  type Copy = Item
  type More = Copy
  type Branch = Item
  def choose(first: More, second: Element): Branch = first

@apply
@instance
trait AliasOnlyHasOut[A]:
  type Out
  type Item = A

@instance
@apply
trait ReverseAliasOnlyHasOut[Element]:
  type Result
  type Item = Element

@apply
@instance
trait MethodOnlyHasOut[A]:
  type Out
  def keep(value: A): A = value

@instance
@apply
trait ReverseMethodOnlyHasOut[Element]:
  type Result
  def keep(value: Element): Element = value

@apply
@instance
trait CollisionHasOut[Element]:
  type Result
  type Result0 = Element
  type Result1 = Result0
  def Result2(value: Element): Element = value

@apply
@instance
trait AbstractTypeExistingApply[A]:
  type Out

object AbstractTypeExistingApply:
  var applyCalls = 0
  def apply[A](using value: AbstractTypeExistingApply[A]): AbstractTypeExistingApply[A] =
    applyCalls += 1
    value

@instance
@apply
trait ReverseAbstractTypeExistingApply[A]:
  type Out

object ReverseAbstractTypeExistingApply:
  var applyCalls = 0
  def apply[A](using value: ReverseAbstractTypeExistingApply[A]): ReverseAbstractTypeExistingApply[A] =
    applyCalls += 1
    value

@apply
@instance
trait AbstractTypeExistingInstance[A]:
  type Out

object AbstractTypeExistingInstance:
  var instanceCalls = 0
  def instance[A, Out0]: AbstractTypeExistingInstance[A] { type Out = Out0 } =
    instanceCalls += 1
    new AbstractTypeExistingInstance[A]:
      type Out = Out0

@instance
@apply
trait ReverseAbstractTypeExistingInstance[A]:
  type Out

object ReverseAbstractTypeExistingInstance:
  var instanceCalls = 0
  def instance[A, Out0]: ReverseAbstractTypeExistingInstance[A] { type Out = Out0 } =
    instanceCalls += 1
    new ReverseAbstractTypeExistingInstance[A]:
      type Out = Out0

@apply
@instance
trait AbstractTypeExistingBoth[A]:
  type Out

object AbstractTypeExistingBoth:
  var applyCalls = 0
  var instanceCalls = 0
  def apply[A](using value: AbstractTypeExistingBoth[A]): AbstractTypeExistingBoth[A] =
    applyCalls += 1
    value
  def instance[A, Out0]: AbstractTypeExistingBoth[A] { type Out = Out0 } =
    instanceCalls += 1
    new AbstractTypeExistingBoth[A]:
      type Out = Out0

@instance
@apply
trait ReverseAbstractTypeExistingBoth[A]:
  type Out

object ReverseAbstractTypeExistingBoth:
  var applyCalls = 0
  var instanceCalls = 0
  def apply[A](using value: ReverseAbstractTypeExistingBoth[A]): ReverseAbstractTypeExistingBoth[A] =
    applyCalls += 1
    value
  def instance[A, Out0]: ReverseAbstractTypeExistingBoth[A] { type Out = Out0 } =
    instanceCalls += 1
    new ReverseAbstractTypeExistingBoth[A]:
      type Out = Out0
