package com.github.dmytromitin.auxify.integration.instance

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
trait RootedStandalone[A]:
  type Out
  type Out0 = Out
  type Item = A
  def directOut(value: Out): Out = value
  type Out1 = Item
  type Copy = Out0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Out
  def mixed(value: Out1, result: Again): Branch = result
  def identityA(value: Item): Out1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Out2(value: A): A = value

@apply
@instance
trait RootedForward[A]:
  type Out
  type Out0 = Out
  type Item = A
  def directOut(value: Out): Out = value
  type Out1 = Item
  type Copy = Out0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Out
  def mixed(value: Out1, result: Again): Branch = result
  def identityA(value: Item): Out1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Out2(value: A): A = value

@instance
@apply
trait RootedReverse[Element]:
  type Result
  type Result0 = Result
  type Item = Element
  def directOut(value: Result): Result = value
  type Result1 = Item
  type Copy = Result0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Result
  def mixed(value: Result1, result: Again): Branch = result
  def identityA(value: Item): Result1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Result2(value: Element): Element = value

@instance
trait OutputOnly[Element]:
  type Output
  type Copy = Output
  type Again = Copy
  def identity(value: Again): Output = value

// Former controlled negatives, preserved as plugin-backed positives.
@instance
trait TypeMemberOutRootAlias[A]:
  type Out
  type Item = Out

@instance
trait TypeMemberOutMethod[A]:
  type Out
  def identity(value: Out): Out = value
