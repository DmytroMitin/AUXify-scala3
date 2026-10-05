import com.github.dmytromitin.auxify.macros.{apply, aux, delegated, instance, self, syntax}

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[String] with
    def show(a: String): String = a

trait Nat
final class Zero extends Nat
final class One extends Nat

@apply
@aux
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out

object Add:
  given Add[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m

@aux
trait Combine[Left <: Nat, Right <: Nat]:
  type Result <: Nat

object Combine:
  given Combine[Zero, One] with
    type Result = One

@aux
@apply
trait AddReverse[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out

object AddReverse:
  given AddReverse[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m

@self
trait SelfQualified

@delegated
trait Render[A]:
  def render(a: A): String

object Render:
  given Render[Int] with
    def render(a: Int): String = a.toString

@instance
trait Monoid[A]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait Choice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

@instance
trait DerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

@instance
trait BinaryDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(a: A, a1: A): A = combine(a, a1)

@instance
trait TernaryDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

@instance
trait LargerDerivedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def fold5(a: Element, b: Element, c: Element, d: Element, e: Element): Element =
    select(select(select(select(a, b), c), d), e)

@instance
trait ZeroMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero: A = empty

@instance
trait WrappedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

@instance
trait WrappedChoice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  type Value = Element

@apply
@instance
trait ApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
@apply
trait InstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

@apply
@instance
trait DerivedApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A): A = combine(a, a)

@instance
@apply
trait DerivedInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def duplicate(value: Element): Element = select(value, value)

@apply
@instance
trait BinaryApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(a: A, a1: A): A = combine(a, a1)

@instance
@apply
trait BinaryInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def selectAgain(first: Element, second: Element): Element = select(first, second)

@apply
@instance
trait TernaryApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

@instance
@apply
trait LargerInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def fold5(a: Element, b: Element, c: Element, d: Element, e: Element): Element =
    select(select(select(select(a, b), c), d), e)

@apply
@instance
trait ZeroApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero: A = empty

@instance
@apply
trait ZeroInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def defaultValue: Element = fallback

@apply
@instance
trait AliasApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A

@instance
@apply
trait AliasInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  type Value = Element

@apply
@delegated
trait ApplyThenDelegated[A]:
  def show(a: A): String

object ApplyThenDelegated:
  val preserved = 41
  given ApplyThenDelegated[Int] with
    def show(a: Int): String = s"apply-first:$a"

@delegated
@apply
trait DelegatedThenApply[A]:
  def show(a: A): String

@syntax
trait ExternalSyntaxMonoid[A]:
  def combine(left: A, right: A): A

object DelegatedThenApply:
  val preserved = 84
  given DelegatedThenApply[String] with
    def show(a: String): String = s"delegated-first:$a"

object ExternalApp:
  def main(args: Array[String]): Unit =
    import ExternalSyntaxMonoid.syntax.*
    given ExternalSyntaxMonoid[Int] with
      def combine(left: Int, right: Int): Int = left + right

    assert(20.combine(22) == 42)

    assert(Show[String].show("external") == "external")

    val selected: Add.Aux[Zero, One, One] = summon[Add[Zero, One]]
    val refined: Add.Aux[Zero, One, One] = Add[Zero, One]
    val result: One = refined(new Zero, new One)
    assert(result.isInstanceOf[One])

    val combined: Combine.Aux[Zero, One, One] = summon[Combine[Zero, One]]
    assert(combined.isInstanceOf[Combine[?, ?]])

    val reversed: AddReverse.Aux[Zero, One, One] = AddReverse[Zero, One]
    val reverseResult: One = reversed(new Zero, new One)
    assert(reverseResult.isInstanceOf[One])

    val selfQualified = new SelfQualified {}
    val generatedSelf: selfQualified.Self = selfQualified
    assert(generatedSelf eq selfQualified)

    assert(Render.render(42) == "42")

    var emptyEvaluations = 0
    val intAddition: Monoid[Int] = Monoid.instance(
      {
        emptyEvaluations += 1
        0
      },
      _ + _
    )
    assert(emptyEvaluations == 0)
    assert(intAddition.empty == 0)
    assert(emptyEvaluations == 1)
    assert(intAddition.combine(20, 22) == 42)

    val renamed: Choice[String] =
      Choice.instance("external", (left, right) => s"$left/$right")
    assert(renamed.fallback == "external")
    assert(renamed.select("left", "right") == "left/right")

    val derived: DerivedMonoid[Int] = DerivedMonoid.instance(0, _ + _)
    assert(derived.twice(21) == 42)

    val binaryDerived: BinaryDerivedMonoid[Int] =
      BinaryDerivedMonoid.instance(0, _ + _)
    assert(binaryDerived.combineAgain(20, 22) == 42)

    val ternaryDerived = TernaryDerivedMonoid.instance[Int](0, _ + _)
    assert(ternaryDerived.fold3(10, 12, 20) == 42)

    val largerDerived =
      LargerDerivedChoice.instance[String]("fallback", (left, right) => s"$left/$right")
    assert(largerDerived.fold5("a", "b", "c", "d", "e") == "a/b/c/d/e")

    var zeroEvaluations = 0
    val zero: ZeroMonoid[Int] = ZeroMonoid.instance(
      {
        zeroEvaluations += 1
        zeroEvaluations
      },
      _ + _
    )
    assert(zeroEvaluations == 0)
    assert(zero.zero == 1)
    assert(zero.empty == 2)
    assert(zeroEvaluations == 2)

    val wrapped: WrappedMonoid[Int] = WrappedMonoid.instance(0, _ + _)
    val wrappedItem: wrapped.Item = 42
    assert(wrappedItem == 42)
    assert(wrapped.combine(20, 22) == 42)

    val wrappedChoice: WrappedChoice[String] =
      WrappedChoice.instance("external", (left, right) => s"$left/$right")
    val wrappedValue: wrappedChoice.Value = "typed"
    assert(wrappedValue == "typed")
    assert(wrappedChoice.select("left", "right") == "left/right")

    val composed: ApplyThenInstance[Int] =
      ApplyThenInstance.instance(0, _ + _)
    given ApplyThenInstance[Int] = composed
    assert(ApplyThenInstance[Int].eq(composed))
    assert(composed.combine(20, 22) == 42)

    val reverseComposed: InstanceThenApply[String] =
      InstanceThenApply.instance("external", (left, right) => s"$left/$right")
    given InstanceThenApply[String] = reverseComposed
    assert(InstanceThenApply[String].eq(reverseComposed))
    assert(reverseComposed.select("left", "right") == "left/right")

    val derivedComposed: DerivedApplyThenInstance[Int] =
      DerivedApplyThenInstance.instance(0, _ + _)
    given DerivedApplyThenInstance[Int] = derivedComposed
    assert(DerivedApplyThenInstance[Int].eq(derivedComposed))
    assert(derivedComposed.twice(21) == 42)

    val derivedReverse: DerivedInstanceThenApply[String] =
      DerivedInstanceThenApply.instance("external", (left, right) => s"$left/$right")
    given DerivedInstanceThenApply[String] = derivedReverse
    assert(DerivedInstanceThenApply[String].eq(derivedReverse))
    assert(derivedReverse.duplicate("same") == "same/same")

    val binaryComposed = BinaryApplyThenInstance.instance[Int](0, _ + _)
    given BinaryApplyThenInstance[Int] = binaryComposed
    assert(BinaryApplyThenInstance[Int].eq(binaryComposed))
    assert(binaryComposed.combineAgain(20, 22) == 42)

    val binaryReverse = BinaryInstanceThenApply.instance[String](
      "external",
      (left, right) => s"$left/$right"
    )
    given BinaryInstanceThenApply[String] = binaryReverse
    assert(BinaryInstanceThenApply[String].eq(binaryReverse))
    assert(binaryReverse.selectAgain("left", "right") == "left/right")

    val ternaryComposed = TernaryApplyThenInstance.instance[Int](0, _ + _)
    given TernaryApplyThenInstance[Int] = ternaryComposed
    assert(TernaryApplyThenInstance[Int].eq(ternaryComposed))
    assert(ternaryComposed.fold3(10, 12, 20) == 42)

    val largerReverse =
      LargerInstanceThenApply.instance[String](
        "external",
        (left, right) => s"$left/$right"
      )
    given LargerInstanceThenApply[String] = largerReverse
    assert(LargerInstanceThenApply[String].eq(largerReverse))
    assert(largerReverse.fold5("a", "b", "c", "d", "e") == "a/b/c/d/e")

    val zeroComposed = ZeroApplyThenInstance.instance[Int](0, _ + _)
    given ZeroApplyThenInstance[Int] = zeroComposed
    assert(ZeroApplyThenInstance[Int].eq(zeroComposed))
    assert(zeroComposed.zero == 0)

    val zeroReverse = ZeroInstanceThenApply.instance[String](
      "external",
      (left, right) => s"$left/$right"
    )
    given ZeroInstanceThenApply[String] = zeroReverse
    assert(ZeroInstanceThenApply[String].eq(zeroReverse))
    assert(zeroReverse.defaultValue == "external")

    val aliasComposed = AliasApplyThenInstance.instance[Int](0, _ + _)
    given AliasApplyThenInstance[Int] = aliasComposed
    val aliasComposedItem: aliasComposed.Item = 42
    assert(aliasComposedItem == 42)
    assert(AliasApplyThenInstance[Int].eq(aliasComposed))

    val aliasReverse = AliasInstanceThenApply.instance[String](
      "external",
      (left, right) => s"$left/$right"
    )
    given AliasInstanceThenApply[String] = aliasReverse
    val aliasReverseValue: aliasReverse.Value = "typed"
    assert(aliasReverseValue == "typed")
    assert(AliasInstanceThenApply[String].eq(aliasReverse))

    assert(ApplyThenDelegated[Int].show(7) == "apply-first:7")
    assert(ApplyThenDelegated.show(7) == "apply-first:7")
    assert(ApplyThenDelegated.preserved == 41)
    assert(DelegatedThenApply[String].show("external") == "delegated-first:external")
    assert(DelegatedThenApply.show("external") == "delegated-first:external")
    assert(DelegatedThenApply.preserved == 84)
    println("AUXIFY_SCALA3_EXTERNAL_RUNTIME_PASS")
