import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait ExternalComposedHasOut[A]:
  type Out
  type Item = A
  def identity(value: Item): Item = value
  type Copy = Item
  type Value = Copy
  def pick(first: Value, second: A): Copy = first
  type Branch = Item

@instance
@apply
trait ExternalReverseComposedHasOut[Element]:
  type Result
  def keep(value: Element): Element = value
  type Item = Element
  type Copy = Item
  type Branch = Item
  def choose(value: Copy): Branch = value

object ApplyInstanceAbstractTypeExternalProof:
  def verify(): Unit =
    val constructed: ExternalComposedHasOut[Int] { type Out = String } =
      ExternalComposedHasOut.instance[Int, String]
    val proof: constructed.Out =:= String = summon
    given ExternalComposedHasOut[Int] = constructed

    val selected: ExternalComposedHasOut[Int] = ExternalComposedHasOut[Int]
    val item: selected.Item = 42
    val copy: selected.Copy = 42
    val value: selected.Value = 43
    val branch: selected.Branch = 44
    assert(selected.eq(constructed))
    assert(proof("external-composed") == "external-composed")
    assert(selected.identity(42) == 42)
    assert(selected.pick(42, 99) == 42)
    assert(summon[selected.Item =:= Int](item) == 42)
    assert(summon[selected.Copy =:= Int](copy) == 42)
    assert(summon[selected.Value =:= Int](value) == 43)
    assert(summon[selected.Branch =:= Int](branch) == 44)

    val reverse: ExternalReverseComposedHasOut[Int] { type Result = String } =
      ExternalReverseComposedHasOut.instance[Int, String]
    val reverseProof: reverse.Result =:= String = summon
    given ExternalReverseComposedHasOut[Int] = reverse

    val reverseSelected: ExternalReverseComposedHasOut[Int] =
      ExternalReverseComposedHasOut[Int]
    val reverseItem: reverseSelected.Item = 42
    val reverseCopy: reverseSelected.Copy = 42
    val reverseBranch: reverseSelected.Branch = 42
    assert(reverseSelected.eq(reverse))
    assert(reverseProof("external-reverse") == "external-reverse")
    assert(reverseSelected.keep(42) == 42)
    assert(reverseSelected.choose(42) == 42)
    assert(summon[reverseSelected.Item =:= Int](reverseItem) == 42)
    assert(summon[reverseSelected.Copy =:= Int](reverseCopy) == 42)
    assert(summon[reverseSelected.Branch =:= Int](reverseBranch) == 42)
