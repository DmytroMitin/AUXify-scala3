package com.github.dmytromitin.auxify.integration.composition

class ApplyInstanceAbstractTypeCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then instance already composes for the no-tail abstract-type family") {
    val constructed: AbstractTypeApplyThenInstance[Int] { type Out = String } =
      AbstractTypeApplyThenInstance.instance[Int, String]
    val proof: constructed.Out =:= String = summon
    given AbstractTypeApplyThenInstance[Int] = constructed

    val selected: AbstractTypeApplyThenInstance[Int] =
      AbstractTypeApplyThenInstance[Int]
    assert(selected.eq(constructed))
    assert(proof("typed") == "typed")
  }

  test("instance then apply already composes for the renamed no-tail abstract-type family") {
    val constructed: AbstractTypeInstanceThenApply[Int] { type Result = String } =
      AbstractTypeInstanceThenApply.instance[Int, String]
    val proof: constructed.Result =:= String = summon
    given AbstractTypeInstanceThenApply[Int] = constructed

    val selected: AbstractTypeInstanceThenApply[Int] =
      AbstractTypeInstanceThenApply[Int]
    assert(selected.eq(constructed))
    assert(proof("typed") == "typed")
  }

  test("apply then instance already composes for an interleaved inherited tail") {
    val constructed: RichHasOut[Int] { type Out = String } =
      RichHasOut.instance[Int, String]
    val proof: constructed.Out =:= String = summon
    given RichHasOut[Int] = constructed

    val selected: RichHasOut[Int] = RichHasOut[Int]
    val alias: selected.Item = 42
    val copy: selected.Copy = 42
    val value: selected.Value = 42
    val branch: selected.Branch = 42
    assert(selected.eq(constructed))
    assert(proof("typed") == "typed")
    assert(selected.identity(42) == 42)
    assert(selected.pick(42, 99) == 42)
    assert(summon[selected.Item =:= Int](alias) == 42)
    assert(summon[selected.Copy =:= Int](copy) == 42)
    assert(summon[selected.Value =:= Int](value) == 42)
    assert(summon[selected.Branch =:= Int](branch) == 42)
  }

  test("instance then apply already composes for a renamed inherited tail") {
    val constructed: ReverseHasOut[Int] { type Result = String } =
      ReverseHasOut.instance[Int, String]
    val proof: constructed.Result =:= String = summon
    given ReverseHasOut[Int] = constructed

    val selected: ReverseHasOut[Int] = ReverseHasOut[Int]
    val alias: selected.Item = 42
    val copy: selected.Copy = 42
    val more: selected.More = 42
    val branch: selected.Branch = 42
    assert(selected.eq(constructed))
    assert(proof("typed") == "typed")
    assert(selected.keep(42) == 42)
    assert(selected.choose(42, 99) == 42)
    assert(summon[selected.Item =:= Int](alias) == 42)
    assert(summon[selected.Copy =:= Int](copy) == 42)
    assert(summon[selected.More =:= Int](more) == 42)
    assert(summon[selected.Branch =:= Int](branch) == 42)
  }

  test("both orders inherit alias-only tails") {
    val forward = AliasOnlyHasOut.instance[Int, String]
    given AliasOnlyHasOut[Int] = forward
    val forwardSelected: AliasOnlyHasOut[Int] = AliasOnlyHasOut[Int]
    val forwardItem: forwardSelected.Item = 42
    assert(forwardSelected.eq(forward))
    assert(summon[forwardSelected.Item =:= Int](forwardItem) == 42)

    val reverse = ReverseAliasOnlyHasOut.instance[Int, String]
    given ReverseAliasOnlyHasOut[Int] = reverse
    val reverseSelected: ReverseAliasOnlyHasOut[Int] = ReverseAliasOnlyHasOut[Int]
    val reverseItem: reverseSelected.Item = 42
    assert(reverseSelected.eq(reverse))
    assert(summon[reverseSelected.Item =:= Int](reverseItem) == 42)
  }

  test("both orders inherit method-only tails") {
    val forward = MethodOnlyHasOut.instance[Int, String]
    given MethodOnlyHasOut[Int] = forward
    assert(MethodOnlyHasOut[Int].eq(forward))
    assert(MethodOnlyHasOut[Int].keep(42) == 42)

    val reverse = ReverseMethodOnlyHasOut.instance[Int, String]
    given ReverseMethodOnlyHasOut[Int] = reverse
    assert(ReverseMethodOnlyHasOut[Int].eq(reverse))
    assert(ReverseMethodOnlyHasOut[Int].keep(42) == 42)
  }

  test("type binder freshness ignores a same-spelling term member") {
    val constructed: CollisionHasOut[Int] { type Result = String } =
      CollisionHasOut.instance[Int, String]
    val result0: constructed.Result0 = 42
    val result1: constructed.Result1 = 42
    given CollisionHasOut[Int] = constructed

    val selected: CollisionHasOut[Int] = CollisionHasOut[Int]
    assert(selected.eq(constructed))
    assert(summon[constructed.Result =:= String]("typed") == "typed")
    assert(summon[constructed.Result0 =:= Int](result0) == 42)
    assert(summon[constructed.Result1 =:= Int](result1) == 42)
    assert(selected.Result2(42) == 42)
  }

  test("apply-only conflicts preserve apply and still generate instance in both orders") {
    val forward = AbstractTypeExistingApply.instance[Int, String]
    given AbstractTypeExistingApply[Int] = forward
    assert(AbstractTypeExistingApply[Int].eq(forward))
    assertEquals(AbstractTypeExistingApply.applyCalls, 1)

    val reverse = ReverseAbstractTypeExistingApply.instance[Int, String]
    given ReverseAbstractTypeExistingApply[Int] = reverse
    assert(ReverseAbstractTypeExistingApply[Int].eq(reverse))
    assertEquals(ReverseAbstractTypeExistingApply.applyCalls, 1)
  }

  test("instance-only conflicts preserve instance and still generate apply in both orders") {
    val forward = AbstractTypeExistingInstance.instance[Int, String]
    given AbstractTypeExistingInstance[Int] = forward
    assert(AbstractTypeExistingInstance[Int].eq(forward))
    assertEquals(AbstractTypeExistingInstance.instanceCalls, 1)

    val reverse = ReverseAbstractTypeExistingInstance.instance[Int, String]
    given ReverseAbstractTypeExistingInstance[Int] = reverse
    assert(ReverseAbstractTypeExistingInstance[Int].eq(reverse))
    assertEquals(ReverseAbstractTypeExistingInstance.instanceCalls, 1)
  }

  test("both conflicts preserve both exact methods in both orders") {
    val forward = AbstractTypeExistingBoth.instance[Int, String]
    given AbstractTypeExistingBoth[Int] = forward
    assert(AbstractTypeExistingBoth[Int].eq(forward))
    assertEquals(AbstractTypeExistingBoth.applyCalls, 1)
    assertEquals(AbstractTypeExistingBoth.instanceCalls, 1)

    val reverse = ReverseAbstractTypeExistingBoth.instance[Int, String]
    given ReverseAbstractTypeExistingBoth[Int] = reverse
    assert(ReverseAbstractTypeExistingBoth[Int].eq(reverse))
    assertEquals(ReverseAbstractTypeExistingBoth.applyCalls, 1)
    assertEquals(ReverseAbstractTypeExistingBoth.instanceCalls, 1)
  }
