package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

sealed trait Other

@instance
class ClassTarget[A]:
  def empty: A = ???
  def combine(a: A, a1: A): A = ???

@instance
trait TwoParameters[A, B]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait Variant[+A]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait Bounded[A <: AnyRef]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait Concrete[A]:
  def empty: A = ???
  def combine(a: A, a1: A): A

@instance
trait Polymorphic[A]:
  def empty: A
  def combine[B](a: A, a1: A): A

@instance
trait Reversed[A]:
  def combine(a: A, a1: A): A
  def empty: A

@instance
trait EmptyClause[A]:
  def empty(): A
  def combine(a: A, a1: A): A

@instance
trait WrongArity[A]:
  def empty: A
  def combine(a: A): A

@instance
trait ContextualClause[A]:
  def empty: A
  def combine(using a: A, a1: A): A

@instance
trait Defaulted[A]:
  def empty: A
  def combine(a: A = ???, a1: A): A

@instance
trait WrongParameter[A]:
  def empty: A
  def combine(a: Other, a1: A): A

@instance
trait WrongEmptyResult[A]:
  def empty: Other
  def combine(a: A, a1: A): A

@instance
trait WrongBinaryResult[A]:
  def empty: A
  def combine(a: A, a1: A): Other

@instance
trait ExtraVal[A]:
  def empty: A
  def combine(a: A, a1: A): A
  val extra: A

@instance
trait ExtraVar[A]:
  def empty: A
  def combine(a: A, a1: A): A
  var extra: A

@instance
trait ExtraType[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Extra

@instance
trait ExtraNested[A]:
  def empty: A
  def combine(a: A, a1: A): A
  trait Nested

@instance
trait ProtectedMethod[A]:
  protected def empty: A
  def combine(a: A, a1: A): A

@instance
trait AnnotatedMethod[A]:
  def empty: A
  @deprecated("unsupported", "")
  def combine(a: A, a1: A): A
