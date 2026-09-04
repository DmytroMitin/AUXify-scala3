import com.github.dmytromitin.auxify.macros.{apply, aux, delegated, instance, self}

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

object DelegatedThenApply:
  val preserved = 84
  given DelegatedThenApply[String] with
    def show(a: String): String = s"delegated-first:$a"

object ExternalApp:
  def main(args: Array[String]): Unit =
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

    assert(ApplyThenDelegated[Int].show(7) == "apply-first:7")
    assert(ApplyThenDelegated.show(7) == "apply-first:7")
    assert(ApplyThenDelegated.preserved == 41)
    assert(DelegatedThenApply[String].show("external") == "delegated-first:external")
    assert(DelegatedThenApply.show("external") == "delegated-first:external")
    assert(DelegatedThenApply.preserved == 84)
    println("AUXIFY_SCALA3_EXTERNAL_RUNTIME_PASS")
