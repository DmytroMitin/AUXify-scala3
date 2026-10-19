package com.github.dmytromitin.auxify.integration.composition

class ApplySyntaxCompositionMatrixIntegrationSuite extends munit.FunSuite:
  test("both source orders create one companion from a missing companion") {
    import FreshApplySyntax.syntax.*
    import FreshSyntaxApply.syntax.*

    given FreshApplySyntax[Int] with
      def combine(left: Int, right: Int): Int = left + right
    given FreshSyntaxApply[Int] with
      def merge(left: Int, right: Int): Int = left + right

    assertEquals(20.combine(22), 42)
    assertEquals(20.merge(22), 42)
    assertEquals(FreshApplySyntax[Int].twice(21), 42)
    assertEquals(FreshSyntaxApply[Int].duplicate(21), 42)
    assertEquals(FreshSyntaxApply[Int].retain(42), 42)
  }

  test("a fresh companion contains one apply and one primary extension only") {
    val companionMethods = FreshApplySyntax.getClass.getDeclaredMethods.toList.map(_.getName)
    val syntaxMethods = FreshApplySyntax.syntax.getClass.getDeclaredMethods.toList.map(_.getName)

    assertEquals(companionMethods.count(_ == "apply"), 1)
    assertEquals(syntaxMethods.count(_ == "combine"), 1)
    assert(!companionMethods.contains("twice"), clue(companionMethods))
    assert(!syntaxMethods.contains("twice"), clue(syntaxMethods))
  }

  test("an existing apply suppresses only apply generation") {
    import ApplyConflictSyntax.syntax.*

    assertEquals(20.combine(22), 42)
    assertEquals(ApplyConflictSyntax[Int].twice(21), 42)
    assertEquals(ApplyConflictSyntax.applyCalls, 1)
  }

  test("an existing direct syntax object suppresses only syntax generation") {
    val instance = SyntaxObjectConflict[Int]
    val item: instance.Item = 42

    assertEquals(instance.combine(20, 22), 42)
    assertEquals(summon[instance.Item =:= Int](item), 42)
    assertEquals(SyntaxObjectConflict.syntax.preserved, 42)
  }

  test("existing apply and syntax conflicts preserve both independently") {
    val instance = BothApplySyntaxConflicts[Int]

    assertEquals(instance.twice(21), 42)
    assertEquals(BothApplySyntaxConflicts.applyCalls, 1)
    assertEquals(BothApplySyntaxConflicts.syntax.preserved, 42)
  }

  test("a direct type syntax and nested syntax object do not conflict") {
    import TypeSyntaxNamespace.syntax.*

    val typeWitness: TypeSyntaxNamespace.syntax = "type-namespace"
    assertEquals(20.merge(22), 42)
    assertEquals(TypeSyntaxNamespace[Int].duplicate(21), 42)
    assertEquals(typeWitness, "type-namespace")
    assertEquals(TypeSyntaxNamespace.Nested.syntax.preserved, 21)
  }

  test("term names inst and inst1 freshen the evidence while aliases stay type only") {
    import FreshEvidenceNames.syntax.*
    import AliasOnlyEvidence.syntax.*

    assertEquals(20.merge(22), 42)
    assertEquals(FreshEvidenceNames[Int].inst(42), 42)
    assertEquals(FreshEvidenceNames[Int].inst1(42), 42)
    assertEquals(20.combine(22), 42)
    val aliasValue: AliasOnlyEvidence[Int]#inst = 42
    assertEquals(aliasValue, 42)
  }

  test("interleaved method arities and alias indices remain inherited") {
    import InterleavedApplySyntax.syntax.*

    val instance = InterleavedApplySyntax[Int]
    val first: instance.First = 40
    val second: instance.Second = 41
    val third: instance.Third = 42

    assertEquals(20.combine(22), 42)
    val zeroArity = intercept[NoSuchElementException](instance.current)
    assertEquals(zeroArity.getMessage, "not evaluated")
    assertEquals(instance.keep(42), 42)
    assertEquals(instance.choose(10, 42, 20), 42)
    assertEquals(summon[instance.First =:= Int](first), 40)
    assertEquals(summon[instance.Second =:= Int](second), 41)
    assertEquals(summon[instance.Third =:= Int](third), 42)
    assertEquals(InterleavedApplySyntax.before + InterleavedApplySyntax.after, 84)
  }
