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
trait ParameterlessNoTail[A]:
  def empty: A

object ParameterlessNoTail:
  given ParameterlessNoTail[Int] with
    def empty: Int = 42

@delegated
@apply
trait ReverseParameterlessNoTail[Element]:
  def fallback: Element

object ReverseParameterlessNoTail:
  given ReverseParameterlessNoTail[String] with
    def fallback: String = "fallback"

@apply
@delegated
trait RichEmpty[A]:
  def empty: A
  type Item = A
  type Copy = Item
  def duplicate(a: A): A = a
  type Value = Copy
  type Branch = Item
  def choose(a: A, b: A): A = a
  def current: A = empty

object RichEmpty:
  given RichEmpty[Int] with
    def empty: Int = 42

@delegated
@apply
trait ReverseRichEmpty[Element]:
  def fallback: Element
  def repeat(value: Element): Element = value
  type Item = Element
  type Copy = Item
  type More = Copy
  type Branch = Item

object ReverseRichEmpty:
  given ReverseRichEmpty[String] with
    def fallback: String = "reverse"

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

@apply
@delegated
trait RichShow[A]:
  def show(a: A): String
  type Item = A
  type Copy = Item
  def duplicate(a: A): A = a
  type Value = Copy
  type Branch = Item

object RichShow:
  val preserved = 97
  given RichShow[Int] with
    def show(a: Int): String = s"rich:$a"

@delegated
@apply
trait ReverseRichRender[Element]:
  def render(value: Element): Text
  def retain(value: Element): Element = value
  type Item = Element
  type Copy = Item
  type More = Copy
  type Branch = Item

object ReverseRichRender:
  val preserved = 98
  given ReverseRichRender[Long] with
    def render(value: Long): Text = Text(s"reverse-rich:$value")

@delegated
@apply
trait GeneratedTail[A]:
  def show(a: A): String
  def duplicate(a: A): A = a

@apply
@delegated
trait ApplyConflictForward[A]:
  def show(a: A): String
  type Item = A

object ApplyConflictForward:
  var applyCalls = 0
  def apply[A](using instance: ApplyConflictForward[A]): ApplyConflictForward[A] =
    applyCalls += 1
    instance
  given ApplyConflictForward[Int] with
    def show(a: Int): String = s"apply-conflict-forward:$a"

@delegated
@apply
trait ApplyConflictReverse[A]:
  def show(a: A): String
  def duplicate(a: A): A = a

object ApplyConflictReverse:
  var applyCalls = 0
  def apply[A](using instance: ApplyConflictReverse[A]): ApplyConflictReverse[A] =
    applyCalls += 1
    instance
  given ApplyConflictReverse[Int] with
    def show(a: Int): String = s"apply-conflict-reverse:$a"

@apply
@delegated
trait ShowConflictForward[A]:
  def show(a: A): String
  def unavailable: A = throw new NoSuchElementException("not evaluated")

object ShowConflictForward:
  var showCalls = 0
  def show[A](a: A)(using ShowConflictForward[A]): String =
    showCalls += 1
    s"show-conflict-forward:$a"
  given ShowConflictForward[Int] with
    def show(a: Int): String = s"instance-forward:$a"

@delegated
@apply
trait ShowConflictReverse[A]:
  def show(a: A): String
  def choose(first: A, second: A, third: A): A = second

object ShowConflictReverse:
  var showCalls = 0
  def show[A](a: A)(using ShowConflictReverse[A]): String =
    showCalls += 1
    s"show-conflict-reverse:$a"
  given ShowConflictReverse[Int] with
    def show(a: Int): String = s"instance-reverse:$a"

@apply
@delegated
trait BothConflictForward[A]:
  def show(a: A): String
  type Item = A
  def duplicate(a: A): A = a

object BothConflictForward:
  var applyCalls = 0
  var showCalls = 0
  def apply[A](using instance: BothConflictForward[A]): BothConflictForward[A] =
    applyCalls += 1
    instance
  def show[A](a: A)(using BothConflictForward[A]): String =
    showCalls += 1
    s"both-conflict-forward:$a"
  given BothConflictForward[Int] with
    def show(a: Int): String = s"instance-forward:$a"

@delegated
@apply
trait BothConflictReverse[A]:
  def show(a: A): String
  def duplicate(a: A): A = a
  type Item = A

object BothConflictReverse:
  var applyCalls = 0
  var showCalls = 0
  def apply[A](using instance: BothConflictReverse[A]): BothConflictReverse[A] =
    applyCalls += 1
    instance
  def show[A](a: A)(using BothConflictReverse[A]): String =
    showCalls += 1
    s"both-conflict-reverse:$a"
  given BothConflictReverse[Int] with
    def show(a: Int): String = s"instance-reverse:$a"

@delegated
@apply
trait GeneratedParameterlessTail[A]:
  def empty: A
  def zero: A = empty
  type Item = A

@apply
@delegated
trait ParameterlessApplyConflict[A]:
  def empty: A
  type Item = A

object ParameterlessApplyConflict:
  var applyCalls = 0
  def apply[A](using instance: ParameterlessApplyConflict[A]): ParameterlessApplyConflict[A] =
    applyCalls += 1
    instance
  given ParameterlessApplyConflict[Int] with
    def empty: Int = 42

@delegated
@apply
trait ParameterlessEmptyConflict[A]:
  def empty: A
  def duplicate(value: A): A = value

object ParameterlessEmptyConflict:
  var emptyCalls = 0
  def empty[A](using instance: ParameterlessEmptyConflict[A]): A =
    emptyCalls += 1
    instance.empty
  given ParameterlessEmptyConflict[Int] with
    def empty: Int = 43

@apply
@delegated
trait ParameterlessBothConflict[A]:
  def empty: A
  type Value = A

object ParameterlessBothConflict:
  var applyCalls = 0
  var emptyCalls = 0
  def apply[A](using instance: ParameterlessBothConflict[A]): ParameterlessBothConflict[A] =
    applyCalls += 1
    instance
  def empty[A](using instance: ParameterlessBothConflict[A]): A =
    emptyCalls += 1
    instance.empty
  given ParameterlessBothConflict[Int] with
    def empty: Int = 44
