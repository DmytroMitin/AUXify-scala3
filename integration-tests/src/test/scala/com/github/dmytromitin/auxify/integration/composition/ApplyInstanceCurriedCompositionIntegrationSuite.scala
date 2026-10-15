package com.github.dmytromitin.auxify.integration.composition

class ApplyInstanceCurriedCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then instance already composes for the no-tail curried family") {
    val constructed = CurriedApplyThenInstance.instance[Int](a => b => a + b)
    given CurriedApplyThenInstance[Int] = constructed

    val selected: CurriedApplyThenInstance[Int] = CurriedApplyThenInstance[Int]
    assert(selected.eq(constructed))
    assertEquals(selected.combine(20)(22), 42)
  }

  test("instance then apply already composes for the renamed no-tail curried family") {
    val constructed = CurriedInstanceThenApply.instance[Int](left => right => left + right)
    given CurriedInstanceThenApply[Int] = constructed

    val selected: CurriedInstanceThenApply[Int] = CurriedInstanceThenApply[Int]
    assert(selected.eq(constructed))
    assertEquals(selected.merge(20)(22), 42)
  }

  test("apply then instance already composes for an interleaved inherited tail") {
    var carrierCalls = 0
    val constructed = RichCurriedApplyThenInstance.instance[Int](a => b =>
      carrierCalls += 1
      a + b
    )
    given RichCurriedApplyThenInstance[Int] = constructed

    val selected: RichCurriedApplyThenInstance[Int] = RichCurriedApplyThenInstance[Int]
    val item: selected.Item = 42
    val value: selected.Value = 42
    assert(selected.eq(constructed))
    assertEquals(selected.combine(20)(22), 42)
    assertEquals(selected.twice(21), 42)
    assertEquals(selected.pick(42, 99), 42)
    assertEquals(selected.fold3(10, 12, 20), 42)
    assertEquals(carrierCalls, 4)
    assertEquals(summon[selected.Item =:= Int](item), 42)
    assertEquals(summon[selected.Value =:= Int](value), 42)
  }

  test("instance then apply already composes for a renamed inherited tail") {
    val constructed = RichCurriedInstanceThenApply.instance[Int](left => right => left + right)
    given RichCurriedInstanceThenApply[Int] = constructed

    val selected: RichCurriedInstanceThenApply[Int] = RichCurriedInstanceThenApply[Int]
    val item: selected.Item = 42
    assert(selected.eq(constructed))
    assertEquals(selected.merge(20)(22), 42)
    assertEquals(selected.duplicate(21), 42)
    assertEquals(summon[selected.Item =:= Int](item), 42)
  }

  test("mixed inherited tails retain zero through four-argument concrete methods") {
    val constructed = CurriedArityTail.instance[Int](a => b => a + b)
    given CurriedArityTail[Int] = constructed

    val selected: CurriedArityTail[Int] = CurriedArityTail[Int]
    val item: selected.Item = 42
    val value: selected.Value = 42
    assert(selected.eq(constructed))
    assertEquals(selected.fortyTwo, 42)
    assertEquals(selected.unary(42), 42)
    assertEquals(selected.binary(20, 22), 42)
    assertEquals(selected.quaternary(10, 12, 8, 12), 42)
    assertEquals(summon[selected.Item =:= Int](item), 42)
    assertEquals(summon[selected.Value =:= Int](value), 42)
  }

  test("carrier freshness includes term roles but excludes type-only aliases") {
    val constructed = CurriedCarrierCollision.instance[Int](left => right => left + right)
    given CurriedCarrierCollision[Int] = constructed

    val selected: CurriedCarrierCollision[Int] = CurriedCarrierCollision[Int]
    val alias: selected.combineFunction3 = 42
    assert(selected.eq(constructed))
    assertEquals(selected.merge(20)(22), 42)
    assertEquals(selected.keep(42), 42)
    assertEquals(summon[selected.combineFunction3 =:= Int](alias), 42)
  }

  test("an existing apply suppresses only apply") {
    val constructed = CurriedExistingApply.instance[Int](a => b => a + b)
    given CurriedExistingApply[Int] = constructed
    assert(CurriedExistingApply[Int].eq(constructed))
    assertEquals(CurriedExistingApply.applyCalls, 1)
    assertEquals(constructed.combine(20)(22), 42)
  }

  test("an existing instance suppresses only instance") {
    val constructed = CurriedExistingInstance.instance[Int](a => b => a + b)
    given CurriedExistingInstance[Int] = constructed
    assert(CurriedExistingInstance[Int].eq(constructed))
    assertEquals(CurriedExistingInstance.instanceCalls, 1)
    assertEquals(constructed.combine(20)(22), 42)
  }

  test("both existing methods and unrelated companion content are preserved") {
    val constructed = CurriedExistingBoth.instance[Int](a => b => a + b)
    given CurriedExistingBoth[Int] = constructed
    assert(CurriedExistingBoth[Int].eq(constructed))
    assertEquals(CurriedExistingBoth.applyCalls, 1)
    assertEquals(CurriedExistingBoth.instanceCalls, 1)
    assertEquals(CurriedExistingBoth.retained, 17)
    assertEquals(constructed.combine(20)(22), 42)
  }
