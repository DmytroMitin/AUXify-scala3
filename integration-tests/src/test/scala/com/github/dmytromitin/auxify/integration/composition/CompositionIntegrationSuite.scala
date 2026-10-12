package com.github.dmytromitin.auxify.integration.composition

class CompositionIntegrationSuite extends munit.FunSuite:
  test("apply then delegated generates both methods and preserves the companion") {
    val instance = ApplyThenDelegated[Int]

    assertEquals(instance.show(7), "apply-first:7")
    assertEquals(ApplyThenDelegated.show(7), "apply-first:7")
    assertEquals(ApplyThenDelegated.preservedBefore, 41)
    assertEquals(ApplyThenDelegated.preservedAfter, 43)
  }

  test("delegated then apply generates both methods and preserves the companion") {
    val instance = DelegatedThenApply[String]

    assertEquals(instance.show("value"), "delegated-first:value")
    assertEquals(DelegatedThenApply.show("value"), "delegated-first:value")
    assertEquals(DelegatedThenApply.preservedBefore, 81)
    assertEquals(DelegatedThenApply.preservedAfter, 85)
  }

  test("an existing apply is preserved while delegated forwarding is generated") {
    val instance = ExistingApplyThenDelegated[Int]

    assertEquals(instance.show(9), "generated-show:9")
    assertEquals(ExistingApplyThenDelegated.show(9), "generated-show:9")
    assertEquals(ExistingApplyThenDelegated.applyCalls, 1)
  }

  test("an existing forwarding method is preserved while apply is generated") {
    val instance = ExistingForwardThenApply[Int]

    assertEquals(instance.show(11), "generated-show:11")
    assertEquals(ExistingForwardThenApply.show(11), "preserved-show:11")
    assertEquals(ExistingForwardThenApply.showCalls, 1)
  }

  test("a renamed delegated method remains structurally derived in composition") {
    assertEquals(RenamedComposition.render(17L), Text("rendered:17"))
  }

  test("apply then delegated composes a unary forwarder with a heterogeneous inherited tail") {
    val instance = RichShow[Int]
    val item: instance.Item = 42
    val value: instance.Value = 43

    assertEquals(instance.show(42), "rich:42")
    assertEquals(RichShow.show(42), "rich:42")
    assertEquals(instance.duplicate(42), 42)
    assertEquals(summon[instance.Item =:= Int](item), 42)
    assertEquals(summon[instance.Value =:= Int](value), 43)
    assertEquals(RichShow.preserved, 97)
  }

  test("delegated then apply composes a renamed unary forwarder with an inherited tail") {
    val instance = ReverseRichRender[Long]
    val item: instance.Item = 19L

    assertEquals(instance.render(19L), Text("reverse-rich:19"))
    assertEquals(ReverseRichRender.render(19L), Text("reverse-rich:19"))
    assertEquals(instance.retain(19L), 19L)
    assertEquals(summon[instance.Item =:= Long](item), 19L)
    assertEquals(ReverseRichRender.preserved, 98)
  }

  test("a fresh companion contains exactly apply and one unary forwarder, never tail copies") {
    given GeneratedTail[Int] with
      def show(a: Int): String = s"generated:$a"

    val instance = GeneratedTail[Int]
    val generatedNames = GeneratedTail.getClass.getDeclaredMethods.toList.map(_.getName)

    assertEquals(instance.show(5), "generated:5")
    assertEquals(GeneratedTail.show(5), "generated:5")
    assertEquals(instance.duplicate(5), 5)
    assertEquals(generatedNames.count(_ == "apply"), 1)
    assertEquals(generatedNames.count(_ == "show"), 1)
    assert(!generatedNames.contains("duplicate"), clue(generatedNames))
  }

  test("a populated companion also keeps inherited tail members out of generated topology") {
    val generatedNames = RichShow.getClass.getDeclaredMethods.toList.map(_.getName)

    assertEquals(generatedNames.count(_ == "apply"), 1)
    assertEquals(generatedNames.count(_ == "show"), 1)
    assert(!generatedNames.contains("duplicate"), clue(generatedNames))
  }

  test("an existing apply suppresses only apply in both source orders") {
    val forward = ApplyConflictForward[Int]
    assertEquals(forward.show(21), "apply-conflict-forward:21")
    assertEquals(ApplyConflictForward.show(21), "apply-conflict-forward:21")
    assertEquals(ApplyConflictForward.applyCalls, 1)

    val reverse = ApplyConflictReverse[Int]
    assertEquals(reverse.show(22), "apply-conflict-reverse:22")
    assertEquals(ApplyConflictReverse.show(22), "apply-conflict-reverse:22")
    assertEquals(reverse.duplicate(22), 22)
    assertEquals(ApplyConflictReverse.applyCalls, 1)
  }

  test("an existing unary forwarder suppresses only the forwarder in both source orders") {
    val forward = ShowConflictForward[Int]
    assertEquals(forward.show(23), "instance-forward:23")
    assertEquals(ShowConflictForward.show(23), "show-conflict-forward:23")
    assertEquals(ShowConflictForward.showCalls, 1)

    val reverse = ShowConflictReverse[Int]
    assertEquals(reverse.show(24), "instance-reverse:24")
    assertEquals(ShowConflictReverse.show(24), "show-conflict-reverse:24")
    assertEquals(reverse.choose(1, 24, 3), 24)
    assertEquals(ShowConflictReverse.showCalls, 1)
  }

  test("existing apply and unary forwarder remain independent in both source orders") {
    val forward = BothConflictForward[Int]
    val forwardItem: forward.Item = 25
    assertEquals(forward.show(25), "instance-forward:25")
    assertEquals(BothConflictForward.show(25), "both-conflict-forward:25")
    assertEquals(forward.duplicate(25), 25)
    assertEquals(summon[forward.Item =:= Int](forwardItem), 25)
    assertEquals(BothConflictForward.applyCalls, 1)
    assertEquals(BothConflictForward.showCalls, 1)

    val reverse = BothConflictReverse[Int]
    val reverseItem: reverse.Item = 26
    assertEquals(reverse.show(26), "instance-reverse:26")
    assertEquals(BothConflictReverse.show(26), "both-conflict-reverse:26")
    assertEquals(reverse.duplicate(26), 26)
    assertEquals(summon[reverse.Item =:= Int](reverseItem), 26)
    assertEquals(BothConflictReverse.applyCalls, 1)
    assertEquals(BothConflictReverse.showCalls, 1)
  }
