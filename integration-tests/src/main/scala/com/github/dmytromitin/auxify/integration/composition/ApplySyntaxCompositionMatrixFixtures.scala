package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, syntax}

@apply
@syntax
trait FreshApplySyntax[A]:
  def combine(left: A, right: A): A
  type Item = A
  def twice(value: A): A = combine(value, value)

@syntax
@apply
trait FreshSyntaxApply[Element]:
  def merge(left: Element, right: Element): Element
  def duplicate(value: Element): Element = merge(value, value)
  type Item = Element

@apply
@syntax
trait ApplyConflictSyntax[A]:
  def combine(left: A, right: A): A
  def twice(value: A): A = combine(value, value)

object ApplyConflictSyntax:
  var applyCalls = 0
  def apply[A](using instance: ApplyConflictSyntax[A]): ApplyConflictSyntax[A] =
    applyCalls += 1
    instance
  given ApplyConflictSyntax[Int] with
    def combine(left: Int, right: Int): Int = left + right

@syntax
@apply
trait SyntaxObjectConflict[A]:
  def combine(left: A, right: A): A
  type Item = A

object SyntaxObjectConflict:
  object syntax:
    val preserved = 42
  given SyntaxObjectConflict[Int] with
    def combine(left: Int, right: Int): Int = left + right

@apply
@syntax
trait BothApplySyntaxConflicts[A]:
  def combine(left: A, right: A): A
  def twice(value: A): A = combine(value, value)

object BothApplySyntaxConflicts:
  var applyCalls = 0
  def apply[A](using instance: BothApplySyntaxConflicts[A]): BothApplySyntaxConflicts[A] =
    applyCalls += 1
    instance
  object syntax:
    val preserved = 42
  given BothApplySyntaxConflicts[Int] with
    def combine(left: Int, right: Int): Int = left + right

@syntax
@apply
trait TypeSyntaxNamespace[Element]:
  def merge(left: Element, right: Element): Element
  type Item = Element
  def duplicate(value: Element): Element = merge(value, value)

object TypeSyntaxNamespace:
  type syntax = String
  object Nested:
    object syntax:
      val preserved = 21
  given TypeSyntaxNamespace[Int] with
    def merge(left: Int, right: Int): Int = left + right

@apply
@syntax
trait FreshEvidenceNames[Element]:
  def merge(left: Element, right: Element): Element
  def inst(value: Element): Element = value
  type Alias = Element
  def inst1(value: Element): Element = value

object FreshEvidenceNames:
  given FreshEvidenceNames[Int] with
    def merge(left: Int, right: Int): Int = left + right

@syntax
@apply
trait AliasOnlyEvidence[A]:
  def combine(left: A, right: A): A
  type inst = A

object AliasOnlyEvidence:
  given AliasOnlyEvidence[Int] with
    def combine(left: Int, right: Int): Int = left + right

@apply
@syntax
trait InterleavedApplySyntax[A]:
  def combine(left: A, right: A): A
  type First = A
  def current: A = throw new NoSuchElementException("not evaluated")
  type Second = A
  def keep(value: A): A = value
  type Third = A
  def choose(first: A, second: A, third: A): A = second

object InterleavedApplySyntax:
  val before = 40
  given InterleavedApplySyntax[Int] with
    def combine(left: Int, right: Int): Int = left + right
  val after = 44
