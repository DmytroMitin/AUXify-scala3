
import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
trait ExternalRootedStandalone[A]:
  type Out
  type Out0 = Out
  type Item = A
  def directOut(value: Out): Out = value
  type Out1 = Item
  type Copy = Out0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Out
  def mixed(value: Out1, result: Again): Branch = result
  def identityA(value: Item): Out1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Out2(value: A): A = value

@apply
@instance
trait ExternalRootedForward[A]:
  type Out
  type Out0 = Out
  type Item = A
  def directOut(value: Out): Out = value
  type Out1 = Item
  type Copy = Out0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Out
  def mixed(value: Out1, result: Again): Branch = result
  def identityA(value: Item): Out1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Out2(value: A): A = value

@instance
@apply
trait ExternalRootedReverse[Element]:
  type Result
  type Result0 = Result
  type Item = Element
  def directOut(value: Result): Result = value
  type Result1 = Item
  type Copy = Result0
  def identityOut(value: Copy): Copy = value
  type Again = Copy
  type Branch = Result
  def mixed(value: Result1, result: Again): Branch = result
  def identityA(value: Item): Result1 = value
  def zero: Again = throw new NoSuchElementException("inherited")
  def Result2(value: Element): Element = value

@instance
trait ExternalOutputOnly[Element]:
  type Output
  type Copy = Output
  type Again = Copy
  def identity(value: Again): Output = value

// Former controlled negatives, preserved as plugin-backed positives.
@instance
trait ExternalTypeMemberOutRootAlias[A]:
  type Out
  type Item = Out

@instance
trait ExternalTypeMemberOutMethod[A]:
  type Out
  def identity(value: Out): Out = value

object AbstractRootExternalProof:
  def verify(): Unit =
    val standalone = ExternalRootedStandalone.instance[Int, String]
    val outProof: standalone.Out =:= String = summon
    val copyProof: standalone.Copy =:= String = summon
    val againProof: standalone.Again =:= String = summon
    val itemProof: standalone.Item =:= Int = summon
    assert(standalone.identityOut("typed") == "typed")
    assert(standalone.directOut("value") == "value")
    assert(standalone.identityA(42) == 42)
    assert(standalone.mixed(42, "mixed") == "mixed")
    val forward = ExternalRootedForward.instance[Int, String]
    given ExternalRootedForward[Int] = forward
    val selected: ExternalRootedForward[Int] = ExternalRootedForward[Int]
    val pathProof: selected.Copy =:= selected.Out = summon
    val itemSelected: selected.Item =:= Int = summon
    assert(selected.eq(forward))
    assert(itemSelected(selected.identityA(42)) == 42)
    val reverse = ExternalRootedReverse.instance[Int, String]
    val reverseProof: reverse.Again =:= String = summon
    given ExternalRootedReverse[Int] = reverse
    val reverseSelected: ExternalRootedReverse[Int] = ExternalRootedReverse[Int]
    val reversePath: reverseSelected.Copy =:= reverseSelected.Result = summon
    assert(reverseSelected.eq(reverse))
    assert(reverse.identityOut("reverse") == "reverse")
    println("AUXIFY_SCALA3_INSTANCE_ABSTRACT_OUT_EXTERNAL_PASS")
