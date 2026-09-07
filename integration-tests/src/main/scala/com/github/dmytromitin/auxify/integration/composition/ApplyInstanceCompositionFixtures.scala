package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait ApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A

object ApplyThenInstance:
  val preservedBefore = 41
  val preservedAfter = 43

@instance
@apply
trait InstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

object InstanceThenApply:
  val preserved = 84

@apply
@instance
trait ExistingApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A

object ExistingApplyThenInstance:
  var applyCalls = 0
  def apply[A](using value: ExistingApplyThenInstance[A]): ExistingApplyThenInstance[A] =
    applyCalls += 1
    value
  val retained = 7

@instance
@apply
trait ExistingInstanceThenApply[A]:
  def empty: A
  def combine(a: A, a1: A): A

object ExistingInstanceThenApply:
  var instanceCalls = 0
  def instance[A](
      value: A,
      combineFunction: (A, A) => A
  ): ExistingInstanceThenApply[A] =
    instanceCalls += 1
    new ExistingInstanceThenApply[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
  val retained = 11

@apply
@instance
trait ExistingBoth[A]:
  def empty: A
  def combine(a: A, a1: A): A

object ExistingBoth:
  var applyCalls = 0
  var instanceCalls = 0
  def apply[A](using value: ExistingBoth[A]): ExistingBoth[A] =
    applyCalls += 1
    value
  def instance[A](value: A, combineFunction: (A, A) => A): ExistingBoth[A] =
    instanceCalls += 1
    new ExistingBoth[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
  val retained = 13

@apply
@instance
trait DerivedApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object DerivedApplyThenInstance:
  val preservedBefore = 141
  val preservedAfter = 143

@instance
@apply
trait DerivedInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def duplicate(value: Element): Element = select(value, value)

object DerivedInstanceThenApply:
  val preserved = 184

@apply
@instance
trait DerivedExistingApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object DerivedExistingApplyThenInstance:
  var applyCalls = 0
  def apply[A](using value: DerivedExistingApplyThenInstance[A]): DerivedExistingApplyThenInstance[A] =
    applyCalls += 1
    value
  val retained = 107

@instance
@apply
trait DerivedExistingInstanceThenApply[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object DerivedExistingInstanceThenApply:
  var instanceCalls = 0
  def instance[A](
      value: A,
      combineFunction: (A, A) => A
  ): DerivedExistingInstanceThenApply[A] =
    instanceCalls += 1
    new DerivedExistingInstanceThenApply[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
  val retained = 111

@apply
@instance
trait DerivedExistingBoth[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object DerivedExistingBoth:
  var applyCalls = 0
  var instanceCalls = 0
  def apply[A](using value: DerivedExistingBoth[A]): DerivedExistingBoth[A] =
    applyCalls += 1
    value
  def instance[A](value: A, combineFunction: (A, A) => A): DerivedExistingBoth[A] =
    instanceCalls += 1
    new DerivedExistingBoth[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
  val retained = 113

@apply
@instance
trait AliasApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

object AliasApplyThenInstance:
  val preserved = 241

@instance
@apply
trait AliasInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  type Value = Element

object AliasInstanceThenApply:
  val preserved = 284

@apply
@instance
trait AliasExistingApply[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

object AliasExistingApply:
  var applyCalls = 0
  def apply[A](using value: AliasExistingApply[A]): AliasExistingApply[A] =
    applyCalls += 1
    value

@instance
@apply
trait AliasExistingInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

object AliasExistingInstance:
  var instanceCalls = 0
  def instance[A](value: A, combineFunction: (A, A) => A): AliasExistingInstance[A] =
    instanceCalls += 1
    new AliasExistingInstance[A]:
      def empty: A = value
      def combine(a: A, a1: A): A = combineFunction(a, a1)
