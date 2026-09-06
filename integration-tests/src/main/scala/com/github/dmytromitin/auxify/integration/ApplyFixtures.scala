package com.github.dmytromitin.auxify.integration

import com.github.dmytromitin.auxify.macros.apply

@apply
trait Created[A]

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  val preservedBefore = 41
  object Nested:
    def apply(value: Int): Int = value + 1
  def applyLike(value: Int): Int = value + 2
  given Show[String] with
    def show(a: String): String = a
  val preservedAfter = 43

@apply
trait Evidence[X]

@apply
trait ExistingApply[T]

object ExistingApply:
  var calls = 0
  def apply[T](using inst: ExistingApply[T]): ExistingApply[T] =
    calls += 1
    inst

@apply
trait AppliedDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

object AppliedDerivedMonoid:
  val preserved = 84
  given AppliedDerivedMonoid[Int] with
    def empty: Int = 0
    def combine(a: Int, a1: Int): Int = a + a1
