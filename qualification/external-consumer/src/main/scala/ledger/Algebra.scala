package ledger

import com.github.dmytromitin.auxify.macros.{apply, delegated, instance, syntax}

@apply
@instance
trait Monoid[A]:
  def empty: A
  def combine(left: A, right: A): A
  type Item = A
  type Copy = Item
  def twice(value: Copy): Item = combine(value, value)

// A separate binary-only type class adapts the same calculation to syntax.
@apply
@syntax
trait Addition[A]:
  def combine(left: A, right: A): A
  type Item = A
  type Copy = Item
  def twice(value: Copy): Item = combine(value, value)

@instance
@apply
trait CurriedAddition[A]:
  def combine(left: A)(right: A): A

@apply
@delegated
trait Show[A]:
  def show(value: A): String
  type Item = A
  type Copy = Item
  def retain(value: Copy): Item = value

@delegated
trait Zero[A]:
  def empty: A

@apply
@instance
trait Format[A]:
  type Out
  type Text = Out
  type Copy = Text
  type Item = A
  def retain(value: Copy): Out = value
  def input(value: Item): A = value

// The opposite supported annotation order has the same precision boundary.
@instance
@apply
trait Receipt[A]:
  type Out
  type Text = Out
  type Copy = Text
  def retain(value: Copy): Out = value
