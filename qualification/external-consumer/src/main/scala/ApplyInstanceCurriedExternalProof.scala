import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait ExternalComposedCurried[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def twice(a: A): A = combine(a)(a)
  type Copy = Item
  type Value = Copy
  def fold3(a: A, b: A, c: A): A = combine(combine(a)(b))(c)
  type Branch = Item

@instance
@apply
trait ExternalReverseComposedCurried[Element]:
  def merge(left: Element)(right: Element): Element
  def duplicate(value: Element): Element = merge(value)(value)
  type Item = Element
  type Copy = Item
  type Branch = Item

object ApplyInstanceCurriedExternalProof:
  def verify(): Unit =
    var carrierCalls = 0
    val constructed = ExternalComposedCurried.instance[Int](a => b =>
      carrierCalls += 1
      a + b
    )
    given ExternalComposedCurried[Int] = constructed

    val selected: ExternalComposedCurried[Int] = ExternalComposedCurried[Int]
    val item: selected.Item = 42
    val copy: selected.Copy = 42
    val value: selected.Value = 42
    val branch: selected.Branch = 42
    assert(selected.eq(constructed))
    assert(selected.combine(20)(22) == 42)
    assert(selected.twice(21) == 42)
    assert(selected.fold3(10, 12, 20) == 42)
    assert(carrierCalls == 4)
    assert(summon[selected.Item =:= Int](item) == 42)
    assert(summon[selected.Copy =:= Int](copy) == 42)
    assert(summon[selected.Value =:= Int](value) == 42)
    assert(summon[selected.Branch =:= Int](branch) == 42)

    val reverse = ExternalReverseComposedCurried.instance[Int](left => right => left + right)
    given ExternalReverseComposedCurried[Int] = reverse

    val reverseSelected: ExternalReverseComposedCurried[Int] =
      ExternalReverseComposedCurried[Int]
    val reverseItem: reverseSelected.Item = 42
    val reverseCopy: reverseSelected.Copy = 42
    val reverseBranch: reverseSelected.Branch = 42
    assert(reverseSelected.eq(reverse))
    assert(reverseSelected.merge(20)(22) == 42)
    assert(reverseSelected.duplicate(21) == 42)
    assert(summon[reverseSelected.Item =:= Int](reverseItem) == 42)
    assert(summon[reverseSelected.Copy =:= Int](reverseCopy) == 42)
    assert(summon[reverseSelected.Branch =:= Int](reverseBranch) == 42)
