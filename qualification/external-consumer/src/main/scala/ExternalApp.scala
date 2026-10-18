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

@apply
trait FullRichAdd[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type CopyAgain = Copy
  type Left = N
  def apply(n: N, m: M): Out
  type OtherCopy = Copy
  type FinalCopy = CopyAgain
  type LeftAgain = Left
  type Right = M
  type RightAgain = Right

object FullRichAdd:
  given FullRichAdd[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m

@aux
trait AuxRichAdd[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type CopyAgain = Copy
  type Left = N
  def apply(n: N, m: M): Out
  type OtherCopy = Copy
  type FinalCopy = CopyAgain
  type LeftAgain = Left
  type Right = M
  type RightAgain = Right

object AuxRichAdd:
  given AuxRichAdd[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m

@apply
@aux
trait RichAdd[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type CopyAgain = Copy
  type Left = N
  def apply(n: N, m: M): Out
  type OtherCopy = Copy
  type FinalCopy = CopyAgain
  type LeftAgain = Left
  type Right = M
  type RightAgain = Right

object RichAdd:
  given RichAdd[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m

@aux
@apply
trait RichAddReverse[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type CopyAgain = Copy
  type Left = N
  def apply(n: N, m: M): Out
  type OtherCopy = Copy
  type FinalCopy = CopyAgain
  type LeftAgain = Left
  type Right = M
  type RightAgain = Right

object RichAddReverse:
  given RichAddReverse[Zero, One] with
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

@delegated
trait EmptyDelegated[A]:
  def empty: A

object EmptyDelegated:
  given EmptyDelegated[Int] with
    def empty: Int = 42

@delegated
trait RichRender[A]:
  def render(a: A): String
  type Item = A
  def duplicate(a: A): A = a
  type Value = A
  def pick(a: A, b: A): A = b

object RichRender:
  given RichRender[Int] with
    def render(a: Int): String = a.toString

@delegated
trait RichEmptyDelegated[A]:
  def empty: A
  def inst(inst1: A): A = inst1
  type inst = A
  type Value = A

object RichEmptyDelegated:
  given RichEmptyDelegated[Int] with
    def empty: Int = 42

@instance
trait Monoid[A]:
  def empty: A
  def combine(a: A, a1: A): A

@instance
trait Choice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

@instance
trait HasOut[A]:
  type Out

@instance
trait RichHasOut[A]:
  type Out
  type Out0 = A
  def identity(a: A): A = a
  type Out1 = A
  def fortyTwo: A = 42.asInstanceOf[A]
  def pick(a: A, b: A): A = a

@instance
trait Curried[A]:
  def combine(a: A)(b: A): A

@instance
trait RichCurried[A]:
  def combine(a: A)(b: A): A
  type combineFunction = A
  def twice(a: A): A = combine(a)(a)
  type Value = A
  def fold3(a: A, b: A, c: A): A = combine(combine(a)(b))(c)

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
trait RichDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zeroLike: A = empty
  def twice(a: A): A = combine(a, a)
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

@instance
trait HeterogeneousDerivedMonoid[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  def twice(a: A): A = combine(a, a)
  type Value = A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

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
trait RichApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zeroLike: A = empty
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

@instance
@apply
trait RichInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  def duplicate(value: Element): Element = select(value, value)
  def fold3(a: Element, b: Element, c: Element): Element = select(select(a, b), c)

@apply
@instance
trait HeterogeneousApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  type Item = A
  def twice(a: A): A = combine(a, a)
  type Value = A
  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)

@instance
@apply
trait HeterogeneousInstanceThenApply[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element
  type Value = Element
  def duplicate(value: Element): Element = select(value, value)
  type Output = Element
  def fold3(first: Element, second: Element, third: Element): Element =
    select(select(first, second), third)

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
  type Item = A
  def twice(value: A): A = combine(value, value)
  type Value = A
  def fold3(first: A, second: A, third: A): A =
    combine(combine(first, second), third)

object DelegatedThenApply:
  val preserved = 84
  given DelegatedThenApply[String] with
    def show(a: String): String = s"delegated-first:$a"

@apply
@delegated
trait ExternalRichShow[A]:
  def show(a: A): String
  type Item = A
  def duplicate(a: A): A = a
  type Value = A

object ExternalRichShow:
  given ExternalRichShow[Int] with
    def show(a: Int): String = s"external-rich:$a"

@delegated
@apply
trait ExternalReverseRichShow[Element]:
  def render(value: Element): String
  def retain(value: Element): Element = value
  type Item = Element

object ExternalReverseRichShow:
  given ExternalReverseRichShow[String] with
    def render(value: String): String = s"external-reverse:$value"

@apply
@delegated
trait ExternalRichEmpty[A]:
  def empty: A
  type Item = A
  def duplicate(value: A): A = value
  type Value = A
  def choose(first: A, second: A): A = first

object ExternalRichEmpty:
  given ExternalRichEmpty[Int] with
    def empty: Int = 42

@delegated
@apply
trait ExternalReverseRichEmpty[Element]:
  def fallback: Element
  def repeat(value: Element): Element = value
  type Item = Element

object ExternalReverseRichEmpty:
  given ExternalReverseRichEmpty[String] with
    def fallback: String = "external-empty"

object ExternalApp:
  def main(args: Array[String]): Unit =
    ApplySyntaxExternalProof.verify()
    ApplyInstanceAbstractTypeExternalProof.verify()
    ApplyInstanceCurriedExternalProof.verify()

    val output: HasOut[Int] { type Out = String } =
      HasOut.instance[Int, String]
    val outputWitness: output.Out =:= String = summon[output.Out =:= String]
    assert(outputWitness("external-type-member") == "external-type-member")

    val richOutput: RichHasOut[Int] { type Out = String } =
      RichHasOut.instance[Int, String]
    val richOut0: richOutput.Out0 = 42
    val richOut1: richOutput.Out1 = 42
    assert(summon[richOutput.Out =:= String]("external-rich-out") == "external-rich-out")
    assert(summon[richOutput.Out0 =:= Int](richOut0) == 42)
    assert(summon[richOutput.Out1 =:= Int](richOut1) == 42)
    assert(richOutput.identity(42) == 42)
    assert(richOutput.pick(42, 99) == 42)
    assert(richOutput.fortyTwo == 42)

    import ExternalSyntaxMonoid.syntax.*
    given ExternalSyntaxMonoid[Int] with
      def combine(left: Int, right: Int): Int = left + right

    assert(20.combine(22) == 42)
    val syntaxInstance = summon[ExternalSyntaxMonoid[Int]]
    val syntaxItem: syntaxInstance.Item = 42
    val syntaxValue: syntaxInstance.Value = 42
    assert(syntaxInstance.twice(21) == 42)
    assert(syntaxInstance.fold3(10, 12, 20) == 42)
    assert(summon[syntaxInstance.Item =:= Int](syntaxItem) == 42)
    assert(summon[syntaxInstance.Value =:= Int](syntaxValue) == 42)

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

    val fullRich = FullRichAdd[Zero, One]
    val fullLeft: fullRich.Left =:= Zero = summon
    val fullRight: fullRich.Right =:= One = summon
    val fullOut: fullRich.Out =:= One = summon
    val fullCopyOut: fullRich.Copy =:= fullRich.Out = summon
    val fullCopyResult: fullRich.Copy =:= One = summon
    val fullCopyAgain: fullRich.CopyAgain =:= One = summon
    val fullOtherCopy: fullRich.OtherCopy =:= One = summon
    val fullFinalCopy: fullRich.FinalCopy =:= One = summon
    val fullLeftAgain: fullRich.LeftAgain =:= Zero = summon
    val fullRightAgain: fullRich.RightAgain =:= One = summon
    assert(fullRich(new Zero, new One).isInstanceOf[One])
    assert(fullLeft ne null)
    assert(fullRight ne null)
    assert(fullOut ne null)
    assert(fullCopyOut ne null)
    assert(fullCopyResult ne null)
    assert(fullCopyAgain ne null)
    assert(fullOtherCopy ne null)
    assert(fullFinalCopy ne null)
    assert(fullLeftAgain ne null)
    assert(fullRightAgain ne null)

    val auxRich: AuxRichAdd.Aux[Zero, One, One] = summon[AuxRichAdd[Zero, One]]
    val auxLeft: auxRich.Left =:= Zero = summon
    val auxRight: auxRich.Right =:= One = summon
    val auxOut: auxRich.Out =:= One = summon
    val auxCopyOut: auxRich.Copy =:= auxRich.Out = summon
    val auxCopyResult: auxRich.Copy =:= One = summon
    val auxCopyAgain: auxRich.CopyAgain =:= One = summon
    val auxOtherCopy: auxRich.OtherCopy =:= One = summon
    val auxFinalCopy: auxRich.FinalCopy =:= One = summon
    val auxLeftAgain: auxRich.LeftAgain =:= Zero = summon
    val auxRightAgain: auxRich.RightAgain =:= One = summon
    assert(auxRich(new Zero, new One).isInstanceOf[One])
    assert(auxLeft ne null)
    assert(auxRight ne null)
    assert(auxOut ne null)
    assert(auxCopyOut ne null)
    assert(auxCopyResult ne null)
    assert(auxCopyAgain ne null)
    assert(auxOtherCopy ne null)
    assert(auxFinalCopy ne null)
    assert(auxLeftAgain ne null)
    assert(auxRightAgain ne null)

    val rich: RichAdd.Aux[Zero, One, One] = RichAdd[Zero, One]
    val richLeft: rich.Left =:= Zero = summon
    val richRight: rich.Right =:= One = summon
    val richOut: rich.Out =:= One = summon
    val richCopyOut: rich.Copy =:= rich.Out = summon
    val richCopyResult: rich.Copy =:= One = summon
    val richCopyAgain: rich.CopyAgain =:= One = summon
    val richOtherCopy: rich.OtherCopy =:= One = summon
    val richFinalCopy: rich.FinalCopy =:= One = summon
    val richLeftAgain: rich.LeftAgain =:= Zero = summon
    val richRightAgain: rich.RightAgain =:= One = summon
    assert(rich(new Zero, new One).isInstanceOf[One])
    assert(richLeft ne null)
    assert(richRight ne null)
    assert(richOut ne null)
    assert(richCopyOut ne null)
    assert(richCopyResult ne null)
    assert(richCopyAgain ne null)
    assert(richOtherCopy ne null)
    assert(richFinalCopy ne null)
    assert(richLeftAgain ne null)
    assert(richRightAgain ne null)

    val richAddReverse: RichAddReverse.Aux[Zero, One, One] =
      RichAddReverse[Zero, One]
    val richAddReverseLeft: richAddReverse.Left =:= Zero = summon
    val richAddReverseRight: richAddReverse.Right =:= One = summon
    val richAddReverseOut: richAddReverse.Out =:= One = summon
    val richAddReverseCopyOut: richAddReverse.Copy =:= richAddReverse.Out = summon
    val richAddReverseCopyResult: richAddReverse.Copy =:= One = summon
    val richAddReverseCopyAgain: richAddReverse.CopyAgain =:= One = summon
    val richAddReverseOtherCopy: richAddReverse.OtherCopy =:= One = summon
    val richAddReverseFinalCopy: richAddReverse.FinalCopy =:= One = summon
    val richAddReverseLeftAgain: richAddReverse.LeftAgain =:= Zero = summon
    val richAddReverseRightAgain: richAddReverse.RightAgain =:= One = summon
    assert(richAddReverse(new Zero, new One).isInstanceOf[One])
    assert(richAddReverseLeft ne null)
    assert(richAddReverseRight ne null)
    assert(richAddReverseOut ne null)
    assert(richAddReverseCopyOut ne null)
    assert(richAddReverseCopyResult ne null)
    assert(richAddReverseCopyAgain ne null)
    assert(richAddReverseOtherCopy ne null)
    assert(richAddReverseFinalCopy ne null)
    assert(richAddReverseLeftAgain ne null)
    assert(richAddReverseRightAgain ne null)

    val selfQualified = new SelfQualified {}
    val generatedSelf: selfQualified.Self = selfQualified
    assert(generatedSelf eq selfQualified)

    assert(Render.render(42) == "42")

    val richRender = summon[RichRender[Int]]
    val richRenderItem: richRender.Item = 42
    val richRenderValue: richRender.Value = 42
    assert(RichRender.render(42) == "42")
    assert(richRender.duplicate(42) == 42)
    assert(richRender.pick(41, 42) == 42)
    assert(summon[richRender.Item =:= Int](richRenderItem) == 42)
    assert(summon[richRender.Value =:= Int](richRenderValue) == 42)

    val richEmptyDelegated = summon[RichEmptyDelegated[Int]]
    val richEmptyInst: richEmptyDelegated.inst = 42
    val richEmptyValue: richEmptyDelegated.Value = 42
    assert(RichEmptyDelegated.empty[Int] == 42)
    assert(richEmptyDelegated.inst(42) == 42)
    assert(summon[richEmptyDelegated.inst =:= Int](richEmptyInst) == 42)
    assert(summon[richEmptyDelegated.Value =:= Int](richEmptyValue) == 42)

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

    val curried: Curried[Int] = Curried.instance[Int](a => b => a + b)
    assert(curried.combine(20)(22) == 42)

    var richCurriedCalls = 0
    val richCurried: RichCurried[Int] =
      RichCurried.instance[Int](a =>
        b =>
          richCurriedCalls += 1
          a + b
      )
    val richCurriedItem: richCurried.combineFunction = 42
    val richCurriedValue: richCurried.Value = 42
    assert(summon[richCurried.combineFunction =:= Int](richCurriedItem) == 42)
    assert(summon[richCurried.Value =:= Int](richCurriedValue) == 42)
    assert(richCurried.combine(20)(22) == 42)
    assert(richCurried.twice(21) == 42)
    assert(richCurried.fold3(10, 12, 20) == 42)
    assert(richCurriedCalls == 4)

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

    var richEmptyEvaluations = 0
    var richCombineCalls = 0
    val richDerived = RichDerivedMonoid.instance[Int](
      {
        richEmptyEvaluations += 1
        richEmptyEvaluations
      },
      (left, right) =>
        richCombineCalls += 1
        left + right
    )
    assert(richEmptyEvaluations == 0)
    assert(richDerived.zeroLike == 1)
    assert(richDerived.twice(21) == 42)
    assert(richDerived.fold3(10, 12, 20) == 42)
    assert(richEmptyEvaluations == 1)
    assert(richCombineCalls == 3)

    val heterogeneous = HeterogeneousDerivedMonoid.instance[Int](0, _ + _)
    val heterogeneousItem: heterogeneous.Item = 42
    val heterogeneousValue: heterogeneous.Value = 42
    assert(summon[heterogeneous.Item =:= Int](heterogeneousItem) == 42)
    assert(summon[heterogeneous.Value =:= Int](heterogeneousValue) == 42)
    assert(heterogeneous.twice(21) == 42)
    assert(heterogeneous.fold3(10, 12, 20) == 42)

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

    val richComposed = RichApplyThenInstance.instance[Int](0, _ + _)
    given RichApplyThenInstance[Int] = richComposed
    assert(RichApplyThenInstance[Int].eq(richComposed))
    assert(richComposed.zeroLike == 0)
    assert(richComposed.fold3(10, 12, 20) == 42)

    val richReverse = RichInstanceThenApply.instance[String](
      "external",
      (left, right) => s"$left/$right"
    )
    given RichInstanceThenApply[String] = richReverse
    assert(RichInstanceThenApply[String].eq(richReverse))
    assert(richReverse.duplicate("same") == "same/same")
    assert(richReverse.fold3("a", "b", "c") == "a/b/c")

    val heterogeneousComposed = HeterogeneousApplyThenInstance.instance[Int](0, _ + _)
    given HeterogeneousApplyThenInstance[Int] = heterogeneousComposed
    val composedItem: heterogeneousComposed.Item = 42
    val composedValue: heterogeneousComposed.Value = 42
    assert(HeterogeneousApplyThenInstance[Int].eq(heterogeneousComposed))
    assert(summon[heterogeneousComposed.Item =:= Int](composedItem) == 42)
    assert(summon[heterogeneousComposed.Value =:= Int](composedValue) == 42)
    assert(heterogeneousComposed.twice(21) == 42)
    assert(heterogeneousComposed.fold3(10, 12, 20) == 42)

    val heterogeneousReverse = HeterogeneousInstanceThenApply.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )
    given HeterogeneousInstanceThenApply[String] = heterogeneousReverse
    val reverseValue: heterogeneousReverse.Value = "typed"
    val reverseOutput: heterogeneousReverse.Output = "output"
    assert(HeterogeneousInstanceThenApply[String].eq(heterogeneousReverse))
    assert(summon[heterogeneousReverse.Value =:= String](reverseValue) == "typed")
    assert(summon[heterogeneousReverse.Output =:= String](reverseOutput) == "output")
    assert(heterogeneousReverse.duplicate("same") == "same/same")
    assert(heterogeneousReverse.fold3("a", "b", "c") == "a/b/c")

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
    assert(EmptyDelegated.empty[Int] == 42)
    val externalRich = ExternalRichShow[Int]
    val externalItem: externalRich.Item = 42
    val externalValue: externalRich.Value = 43
    assert(ExternalRichShow[Int].eq(externalRich))
    assert(ExternalRichShow.show(42) == "external-rich:42")
    assert(externalRich.duplicate(42) == 42)
    assert(summon[externalRich.Item =:= Int](externalItem) == 42)
    assert(summon[externalRich.Value =:= Int](externalValue) == 43)

    val externalReverse = ExternalReverseRichShow[String]
    val externalReverseItem: externalReverse.Item = "typed"
    assert(ExternalReverseRichShow[String].eq(externalReverse))
    assert(ExternalReverseRichShow.render("value") == "external-reverse:value")
    assert(externalReverse.retain("retained") == "retained")
    assert(summon[externalReverse.Item =:= String](externalReverseItem) == "typed")
    val externalEmpty = ExternalRichEmpty[Int]
    val externalEmptyItem: externalEmpty.Item = 42
    val externalEmptyValue: externalEmpty.Value = 43
    assert(ExternalRichEmpty[Int].eq(externalEmpty))
    assert(ExternalRichEmpty.empty[Int] == 42)
    assert(externalEmpty.duplicate(42) == 42)
    assert(externalEmpty.choose(42, 43) == 42)
    assert(summon[externalEmpty.Item =:= Int](externalEmptyItem) == 42)
    assert(summon[externalEmpty.Value =:= Int](externalEmptyValue) == 43)

    val externalReverseEmpty = ExternalReverseRichEmpty[String]
    val externalReverseEmptyItem: externalReverseEmpty.Item = "typed"
    assert(ExternalReverseRichEmpty[String].eq(externalReverseEmpty))
    assert(ExternalReverseRichEmpty.fallback[String] == "external-empty")
    assert(externalReverseEmpty.repeat("retained") == "retained")
    assert(
      summon[externalReverseEmpty.Item =:= String](externalReverseEmptyItem) == "typed"
    )
    println("AUXIFY_SCALA3_EXTERNAL_RUNTIME_PASS")
