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
trait PolyEmpty[A]:
  def empty[B]: A
  def combine(a: A, a1: A): A

@instance
trait PolyCombine[A]:
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

@instance
trait ThirdAbstract[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A

@instance
trait TwoConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)
  def thrice(a: A): A = combine(twice(a), a)

@instance
trait ConcreteVal[A]:
  def empty: A
  def combine(a: A, a1: A): A
  val extra: A = ???

@instance
trait ConcreteVar[A]:
  def empty: A
  def combine(a: A, a1: A): A
  var extra: A = ???

@instance
trait ConcreteLazyVal[A]:
  def empty: A
  def combine(a: A, a1: A): A
  lazy val extra: A = ???

@instance
trait PolyConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice[B](a: A): A = combine(a, a)

@instance
trait ProtectedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  protected def twice(a: A): A = combine(a, a)

@instance
trait PrivateConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  private def twice(a: A): A = combine(a, a)

@instance
trait AnnotatedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  @deprecated("unsupported", "")
  def twice(a: A): A = combine(a, a)

@instance
trait InlineConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  inline def twice(a: A): A = combine(a, a)

@instance
trait WrongConcreteArity[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A, a1: A): A = combine(a, a1)

@instance
trait WrongConcreteParameter[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: Other): A = empty

@instance
trait WrongConcreteResult[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): Other = ???

@instance
trait DefaultedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A = empty): A = combine(a, a)

@instance
trait ContextualConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(using a: A): A = combine(a, a)

@instance
trait ConcretePlusUnsupported[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)
  trait Nested

@instance
trait NamedAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = Other

@instance
trait AppliedAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = List[A]

@instance
trait QualifiedAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = scala.Predef.String

@instance
trait PolymorphicAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item[B] = A

@instance
trait PrivateAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  private type Item = A

@instance
trait ProtectedAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  protected type Item = A

@instance
trait AnnotatedAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  @deprecated("unsupported", "")
  type Item = A

@instance
trait InfixAlias[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix type Item = A

@instance
trait AliasFirst[A]:
  type Item = A
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait AliasPlusUnsupported[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  val extra: A

@instance
trait TwoAliases[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  type Value = A
