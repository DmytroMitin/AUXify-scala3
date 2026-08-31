import com.github.dmytromitin.auxify.macros.{apply, delegated, self}

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
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out

object Add:
  given Add[Zero, One] with
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

object ExternalApp:
  def main(args: Array[String]): Unit =
    assert(Show[String].show("external") == "external")

    val selected = summon[Add[Zero, One]]
    val refined: Add[Zero, One] { type Out = selected.Out } = Add[Zero, One]
    val result: selected.Out = refined(new Zero, new One)
    assert(result.isInstanceOf[One])

    val selfQualified = new SelfQualified {}
    val generatedSelf: selfQualified.Self = selfQualified
    assert(generatedSelf eq selfQualified)

    assert(Render.render(42) == "42")
    println("AUXIFY_SCALA3_EXTERNAL_RUNTIME_PASS")
