package com.github.dmytromitin.auxify.integration.fullapply

class FullApplyIntegrationSuite extends munit.FunSuite:
  test("canonical Add.Out materializer preserves refined typing, identity, and invocation") {
    val selected = summon[Add[Zero, One]]
    val materialized: Add[Zero, One] { type Out = selected.Out } =
      refinedAdd[Zero, One](using selected)
    val result: selected.Out = materialized(new Zero, new One)

    assert(materialized eq selected)
    assert(result.isInstanceOf[One])
  }

  test("full branch merges with an unrelated existing companion") {
    assertEquals(Add.preservedBefore, 71)
    assertEquals(Add.Nested.apply(1), 2)
    assertEquals(Add.applyLike(1), 3)
    assertEquals(Add.preservedAfter, 73)
  }

  test("renamed Combine.Result materializer derives every semantic name") {
    val selected = summon[Combine[LeftNatural, RightNatural]]
    val materialized: Combine[LeftNatural, RightNatural] {
      type Result = selected.Result
    } = refinedCombine[LeftNatural, RightNatural](using selected)
    val result: selected.Result =
      materialized.combine(new LeftNatural, new RightNatural)

    assert(materialized eq selected)
    assert(result.isInstanceOf[RightNatural])
  }

  test("full branch preserves an existing direct apply method") {
    val instance = new ExistingFullApply[Zero, One]:
      type Out = One

    assert(ExistingFullApply[Zero, One](using instance) eq instance)
    assertEquals(ExistingFullApply.calls, 1)
  }
