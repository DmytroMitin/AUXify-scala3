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
