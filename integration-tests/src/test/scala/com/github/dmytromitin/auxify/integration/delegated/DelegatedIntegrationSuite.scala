package com.github.dmytromitin.auxify.integration.delegated

class DelegatedIntegrationSuite extends munit.FunSuite:
  test("canonical public marker forwards an ordinary companion invocation") {
    assertEquals(Show.show(42), "42")
    assertEquals(Show.preservedBefore, 41)
    assertEquals(Show.preservedAfter, 43)
  }

  test("renamed trait method parameter and result names are structurally derived") {
    assertEquals(Render.render(17L), Text("rendered:17"))
  }

  test("unary forwarding preserves heterogeneous inherited concrete tails") {
    val instance = summon[RichShow[Int]]
    val item: instance.Item = 42
    val value: instance.Value = 42
    assertEquals(RichShow.show(42), "42")
    assertEquals(instance.duplicate(42), 42)
    assertEquals(instance.pick(41, 42), 42)
    assertEquals(summon[instance.Item =:= Int](item), 42)
    assertEquals(summon[instance.Value =:= Int](value), 42)
  }

  test("parameterless forwarding preserves heterogeneous inherited concrete tails") {
    val instance = summon[RichEmpty[Int]]
    val alias: instance.inst = 42
    val value: instance.Value = 42
    assertEquals(RichEmpty.empty[Int], 42)
    assertEquals(instance.inst(42), 42)
    assertEquals(instance.pick(41, 42), 42)
    assertEquals(summon[instance.inst =:= Int](alias), 42)
    assertEquals(summon[instance.Value =:= Int](value), 42)
  }

  test("an existing direct same-name method is preserved without duplication") {
    assertEquals(Existing.describe(9), "preserved:9")
    assertEquals(Existing.calls, 1)
  }


  test("parameterless public marker forwards by stable selection") {
    assertEquals(Empty.empty[Int], 42)
  }

  test("renamed parameterless roles and companion members are preserved") {
    assertEquals(Default.fallback[String], "renamed")
    assertEquals(Default.preservedBefore, 41)
    assertEquals(Default.preservedAfter, 43)
  }

  test("parameterless evidence freshness handles a generated method named inst") {
    assertEquals(EmptyLike.inst[Int], 42)
  }

  test("parameterless direct same-name method is preserved without duplication") {
    assertEquals(ExistingEmpty.empty[Int], 42)
    assertEquals(ExistingEmpty.calls, 1)
  }
