import com.github.dmytromitin.auxify.macros.{apply, syntax}

@apply
@syntax
trait ExternalApplySyntax[A]:
  def combine(left: A, right: A): A
  type Item = A
  def twice(value: A): A = combine(value, value)
  type Copy = Item
  type Value = Copy
  def fold3(first: A, second: A, third: A): A =
    combine(combine(first, second), third)
  type Branch = Item

object ExternalApplySyntax:
  given ExternalApplySyntax[Int] with
    def combine(left: Int, right: Int): Int = left + right

@syntax
@apply
trait ExternalSyntaxApply[Element]:
  def merge(left: Element, right: Element): Element
  def duplicate(value: Element): Element = merge(value, value)
  type Item = Element
  type Copy = Item
  type Branch = Item

object ExternalSyntaxApply:
  given ExternalSyntaxApply[Int] with
    def merge(left: Int, right: Int): Int = left + right

object ApplySyntaxExternalProof:
  def verify(): Unit =
    import ExternalApplySyntax.syntax.*
    import ExternalSyntaxApply.syntax.*

    val forward = ExternalApplySyntax[Int]
    val forwardItem: forward.Item = 42
    val forwardCopy: forward.Copy = 42
    val forwardValue: forward.Value = 42
    val forwardBranch: forward.Branch = 42
    val reverse = ExternalSyntaxApply[Int]
    val reverseItem: reverse.Item = 42
    val reverseCopy: reverse.Copy = 42
    val reverseBranch: reverse.Branch = 42

    assert(20.combine(22) == 42)
    assert(forward.twice(21) == 42)
    assert(forward.fold3(10, 12, 20) == 42)
    assert(summon[forward.Item =:= Int](forwardItem) == 42)
    assert(summon[forward.Copy =:= Int](forwardCopy) == 42)
    assert(summon[forward.Value =:= Int](forwardValue) == 42)
    assert(summon[forward.Branch =:= Int](forwardBranch) == 42)
    assert(20.merge(22) == 42)
    assert(reverse.duplicate(21) == 42)
    assert(summon[reverse.Item =:= Int](reverseItem) == 42)
    assert(summon[reverse.Copy =:= Int](reverseCopy) == 42)
    assert(summon[reverse.Branch =:= Int](reverseBranch) == 42)
