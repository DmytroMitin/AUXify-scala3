package com.github.dmytromitin.auxify.integration.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait Show[A]:
  def show(a: A): String

object Show:
  val preservedBefore = 41
  given Show[Int] with
    def show(a: Int): String = a.toString
  val preservedAfter = 43

final case class Text(value: String)

@delegated
trait Render[Element]:
  def render(value: Element): Text

object Render:
  given Render[Long] with
    def render(value: Long): Text = Text(s"rendered:$value")

@delegated
trait Existing[A]:
  def describe(a: A): String

object Existing:
  var calls = 0
  def describe[A](a: A)(using Existing[A]): String =
    calls += 1
    s"preserved:$a"
  given Existing[Int] with
    def describe(a: Int): String = s"generated:$a"



@delegated
trait Empty[A]:
  def empty: A

given Empty[Int] with
  def empty: Int = 42


@delegated
trait Default[Value]:
  def fallback: Value

object Default:
  val preservedBefore = 41
  given Default[String] with
    def fallback: String = "renamed"
  val preservedAfter = 43


@delegated
trait EmptyLike[Value]:
  def inst: Value

object EmptyLike:
  given EmptyLike[Int] with
    def inst: Int = 42


@delegated
trait ExistingEmpty[A]:
  def empty: A

object ExistingEmpty:
  var calls = 0
  def empty[A](using ExistingEmpty[A]): A =
    calls += 1
    summon[ExistingEmpty[A]].empty
  given ExistingEmpty[Int] with
    def empty: Int = 42
