package com.github.dmytromitin.auxify.integration.composition

class ApplySyntaxTailCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then syntax composes with a heterogeneous inherited tail") {
    import RichMonoid.syntax.*

    val instance = RichMonoid[Int]
    val item: instance.Item = 42
    val copy: instance.Copy = 42
    val value: instance.Value = 42
    val branch: instance.Branch = 42

    assertEquals(20.combine(22), 42)
    assertEquals(instance.twice(21), 42)
    assertEquals(instance.fold3(10, 12, 20), 42)
    assertEquals(summon[instance.Item =:= Int](item), 42)
    assertEquals(summon[instance.Copy =:= Int](copy), 42)
    assertEquals(summon[instance.Value =:= Int](value), 42)
    assertEquals(summon[instance.Branch =:= Int](branch), 42)
    assertEquals(RichMonoid.preservedBefore + RichMonoid.preservedAfter, 84)
  }

  test("syntax then apply composes with a renamed inherited tail") {
    import ReverseRichMonoid.syntax.*

    val instance = ReverseRichMonoid[Int]
    val item: instance.Item = 42
    val copy: instance.Copy = 42
    val more: instance.More = 42
    val branch: instance.Branch = 42

    assertEquals(20.merge(22), 42)
    assertEquals(instance.duplicate(21), 42)
    assertEquals(summon[instance.Item =:= Int](item), 42)
    assertEquals(summon[instance.Copy =:= Int](copy), 42)
    assertEquals(summon[instance.More =:= Int](more), 42)
    assertEquals(summon[instance.Branch =:= Int](branch), 42)
  }
