import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait ExternalComposedHasOut[A]:
  type Out
  type Item = A
  def identity(value: A): A = value
  type Value = A
  def pick(first: A, second: A): A = first

@instance
@apply
trait ExternalReverseComposedHasOut[Element]:
  type Result
  def keep(value: Element): Element = value
  type Item = Element

object ApplyInstanceAbstractTypeExternalProof:
  def verify(): Unit =
    val constructed: ExternalComposedHasOut[Int] { type Out = String } =
      ExternalComposedHasOut.instance[Int, String]
    val proof: constructed.Out =:= String = summon
    given ExternalComposedHasOut[Int] = constructed

    val selected: ExternalComposedHasOut[Int] = ExternalComposedHasOut[Int]
    val item: selected.Item = 42
    val value: selected.Value = 43
    assert(selected.eq(constructed))
    assert(proof("external-composed") == "external-composed")
    assert(selected.identity(42) == 42)
    assert(selected.pick(42, 99) == 42)
    assert(summon[selected.Item =:= Int](item) == 42)
    assert(summon[selected.Value =:= Int](value) == 43)

    val reverse: ExternalReverseComposedHasOut[Int] { type Result = String } =
      ExternalReverseComposedHasOut.instance[Int, String]
    val reverseProof: reverse.Result =:= String = summon
    given ExternalReverseComposedHasOut[Int] = reverse

    val reverseSelected: ExternalReverseComposedHasOut[Int] =
      ExternalReverseComposedHasOut[Int]
    val reverseItem: reverseSelected.Item = 42
    assert(reverseSelected.eq(reverse))
    assert(reverseProof("external-reverse") == "external-reverse")
    assert(reverseSelected.keep(42) == 42)
    assert(summon[reverseSelected.Item =:= Int](reverseItem) == 42)
