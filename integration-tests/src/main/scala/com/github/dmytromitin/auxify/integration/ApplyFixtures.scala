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
