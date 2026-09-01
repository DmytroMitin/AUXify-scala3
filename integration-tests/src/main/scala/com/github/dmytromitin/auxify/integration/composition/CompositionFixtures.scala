package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, delegated}

@apply
@delegated
trait ApplyThenDelegated[A]:
  def show(a: A): String

object ApplyThenDelegated:
  val preservedBefore = 41
  given ApplyThenDelegated[Int] with
    def show(a: Int): String = s"apply-first:$a"
  val preservedAfter = 43

@delegated
@apply
trait DelegatedThenApply[A]:
  def show(a: A): String

object DelegatedThenApply:
  val preservedBefore = 81
  given DelegatedThenApply[String] with
    def show(a: String): String = s"delegated-first:$a"
  val preservedAfter = 85

@apply
@delegated
trait ExistingApplyThenDelegated[A]:
  def show(a: A): String

object ExistingApplyThenDelegated:
  var applyCalls = 0
  def apply[A](using instance: ExistingApplyThenDelegated[A]): ExistingApplyThenDelegated[A] =
    applyCalls += 1
    instance
  given ExistingApplyThenDelegated[Int] with
    def show(a: Int): String = s"generated-show:$a"

@delegated
@apply
trait ExistingForwardThenApply[A]:
  def show(a: A): String

object ExistingForwardThenApply:
  var showCalls = 0
  def show[A](a: A)(using ExistingForwardThenApply[A]): String =
    showCalls += 1
    s"preserved-show:$a"
  given ExistingForwardThenApply[Int] with
    def show(a: Int): String = s"generated-show:$a"

final case class Text(value: String)

@apply
@delegated
trait RenamedComposition[Element]:
  def render(value: Element): Text

object RenamedComposition:
  given RenamedComposition[Long] with
    def render(value: Long): Text = Text(s"rendered:$value")
