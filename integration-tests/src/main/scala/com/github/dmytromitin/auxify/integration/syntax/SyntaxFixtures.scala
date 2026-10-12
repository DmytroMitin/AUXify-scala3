package com.github.dmytromitin.auxify.integration.syntax

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait Monoid[A]:
  def combine(a: A, a1: A): A

@syntax
trait Merge[Element]:
  def merge(inst: Element, inst1: Element): Element

@syntax
trait RichMonoid[A]:
  def combine(left: A, right: A): A
  type inst = A
  def zeroLike: A = throw new UnsupportedOperationException("compile-only zero-arity witness")
  def inst(inst1: A): A = inst1
  type Value = A
  def twice(value: A): A = combine(value, value)
  type Result = A
  def fold3(first: A, second: A, third: A): A =
    combine(combine(first, second), third)
@syntax
trait ExistingCompanion[A]:
  def join(left: A, right: A): A

object ExistingCompanion:
  val before = 20
  object Nested
  val after = 22

@syntax
trait ExistingSyntax[A]:
  def join(left: A, right: A): A

object ExistingSyntax:
  object syntax:
    val retained = 42

@syntax
trait TypeSyntax[A]:
  def join(left: A, right: A): A

object TypeSyntax:
  type syntax = String

@syntax
trait NestedSyntax[A]:
  def join(left: A, right: A): A

object NestedSyntax:
  object Nested:
    object syntax:
      val retained = 42

object SyntaxRuntime:
  def canonical: Int =
    import Monoid.syntax.*
    given Monoid[Int] with
      def combine(a: Int, a1: Int): Int = a + a1
    20.combine(22)

  def renamed: Int =
    import Merge.syntax.*
    given Merge[Int] with
      def merge(inst: Int, inst1: Int): Int = inst + inst1
    20.merge(22)

  def existingCompanion: Int =
    import ExistingCompanion.syntax.*
    given ExistingCompanion[Int] with
      def join(left: Int, right: Int): Int = left + right
    ExistingCompanion.before.join(ExistingCompanion.after)


  private val richInstance: RichMonoid[Int] = new RichMonoid[Int]:
    def combine(left: Int, right: Int): Int = left + right

  private given RichMonoid[Int] = richInstance

  def richExtension: Int =
    import RichMonoid.syntax.*
    20.combine(22)

  def richTwice: Int = richInstance.twice(21)

  def richFold3: Int = richInstance.fold3(10, 12, 20)

  def richAlias: Int =
    val value: richInstance.Value = 42
    summon[richInstance.Value =:= Int](value)
