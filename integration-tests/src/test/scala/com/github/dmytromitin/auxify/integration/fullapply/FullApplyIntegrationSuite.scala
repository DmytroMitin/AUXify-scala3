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

  test("full apply keeps enclosing and result aliases inherited and refines only the abstract result") {
    val selected = summon[RichAdd[Zero, One]]
    val materialized: RichAdd[Zero, One] { type Out = selected.Out } =
      RichAdd[Zero, One]
    val left: materialized.Left =:= Zero = summon
    val right: materialized.Right =:= One = summon
    val out: materialized.Out =:= One = summon
    val copyOut: materialized.Copy =:= materialized.Out = summon
    val copyResult: materialized.Copy =:= One = summon
    val copyAgain: materialized.CopyAgain =:= One = summon
    val otherCopy: materialized.OtherCopy =:= One = summon
    val finalCopy: materialized.FinalCopy =:= One = summon
    val leftAgain: materialized.LeftAgain =:= Zero = summon
    val rightAgain: materialized.RightAgain =:= One = summon
    val result: One = materialized(new Zero, new One)

    assert(materialized eq selected)
    assert(left ne null)
    assert(right ne null)
    assert(out ne null)
    assert(copyOut ne null)
    assert(copyResult ne null)
    assert(copyAgain ne null)
    assert(otherCopy ne null)
    assert(finalCopy ne null)
    assert(leftAgain ne null)
    assert(rightAgain ne null)
    assert(result.isInstanceOf[One])
  }

  test("renamed Combine.Result materializer derives every semantic name") {
    val selected = summon[Combine[LeftNatural, RightNatural]]
    val materialized: Combine[LeftNatural, RightNatural] {
      type Result = selected.Result
    } = refinedCombine[LeftNatural, RightNatural](using selected)
    val result: selected.Result =
      materialized.combine(new LeftNatural, new RightNatural)
    val resultAgain: materialized.ResultAgain =:= RightNatural = summon
    val leftAgain: materialized.LeftAgain =:= LeftNatural = summon
    val rightAgain: materialized.RightAgain =:= RightNatural = summon

    assert(materialized eq selected)
    assert(result.isInstanceOf[RightNatural])
    assert(resultAgain ne null)
    assert(leftAgain ne null)
    assert(rightAgain ne null)
  }

  test("full branch preserves an existing direct apply method") {
    val instance = new ExistingFullApply[Zero, One]:
      type Out = One

    assert(ExistingFullApply[Zero, One](using instance) eq instance)
    assertEquals(ExistingFullApply.calls, 1)
  }
