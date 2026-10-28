package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetTypeStructureView, ExpansionTargetView}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape

class InstanceAbstractRootAuthoritySuite extends munit.FunSuite:
  private val rich =
    """trait Rooted[A]:
      |  type Out
      |  type Item = A
      |  type Copy = Out
      |  def identityOut(value: Copy): Out = value
      |  type Again = Copy
      |  type Branch = Out
      |  type ItemCopy = Item
      |  def mixed(item: ItemCopy, result: Again): Branch = result
      |  def zero: Again = throw new NoSuchElementException
      |""".stripMargin

  test("authorizes the validated abstract root through interleaved aliases and methods") {
    val (owner, body, types) = views(rich, "Rooted")
    val shape = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, types)
      .fold(d => fail(d.message), identity)
    assertEquals(shape.memberName, "Out")
    assertEquals(shape.generatedTypeParameterName, "Out0")
    assertEquals(types.directTypeMembers.map(_.bodyIndex), List(0, 1, 2, 4, 5, 6))
    types.directTypeMembers(2).aliasTarget match
      case Some(DirectTypeShape.NamedType("Out", _)) => ()
      case other => fail(s"expected direct named abstract root, found $other")
  }

  List("Result", "Output").foreach: root =>
    test(s"derives $root authority from the normalized primary record") {
      val source = rich.replace("Rooted", "Renamed").replace("Out", root).replace("[A]", "[Element]").replace("= A", "= Element")
      val (owner, body, types) = views(source, "Renamed")
      val shape = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, types)
        .fold(d => fail(d.message), identity)
      assertEquals(shape.memberName, root)
      assertEquals(shape.enclosingTypeParameterName, "Element")
    }

  test("freshens type binders across A and abstract-root aliases while ignoring term names") {
    val source = "trait Fresh[A]:\n  type Out\n  type Out0 = Out\n  type Out1 = A\n  def Out2(value: Out0): Out = value\n"
    val (owner, body, types) = views(source, "Fresh")
    assertEquals(InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, types)
      .fold(d => fail(d.message), _.generatedTypeParameterName), "Out2")
  }

  test("an otherwise valid normalized primary name cannot authorize a spurious root") {
    val (owner, body, types) = views("trait Integrity[A]:\n  type Out\n", "Integrity")
    val malformed = types.copy(directTypeMembers = types.directTypeMembers.map(_.copy(name = "Result")))
    val diagnostic = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, malformed)
      .left.toOption.getOrElse(fail("tampered primary name was admitted"))
    assertEquals(diagnostic.message,
      "unsupported @instance source shape for `Integrity`: normalized direct type member `Result` at body index 0 must match actual direct type member `Out`")
    assertEquals(diagnostic.pos.span, types.directTypeMembers.head.pos.span)
  }

  test("the required abstract name cannot shadow the enclosing binder") {
    val (owner, body, types) = views("trait Collision[A]:\n  type A\n", "Collision")
    val result = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, types)
    assert(result.isLeft, clues(result))
    assert(result.left.toOption.get.message.contains("must not duplicate the enclosing type parameter"))
  }

  test("default shared-tail authority rejects Out for every other primary route") {
    val (_, body, types) = views(rich, "Rooted")
    List("@instance", "@delegated", "@syntax").foreach: annotation =>
      val result = InstanceInheritedConcreteTailValidator.validate(
        "Rooted", "A", body.members.tail, 1, Some(types), annotation)
      val problem = result.left.toOption.getOrElse(fail(s"$annotation leaked abstract root authority"))
      assert(problem.message.contains("inherited concrete type alias `Copy` must target enclosing type parameter `A` or an earlier validated concrete alias"))
  }

  private val otherRoutes = List(
    ("Historical", "def empty: A\n  def combine(left: A, right: A): A"),
    ("Curried", "def combine(left: A)(right: A): A"),
    ("Unary", "def show(value: A): String"),
    ("Parameterless", "def value: A"),
    ("Syntax", "def combine(left: A, right: A): A")
  )
  otherRoutes.foreach: (route, primary) =>
    List("type Copy = Out", "def identity(value: Out): Out = value").foreach: tail =>
      test(s"$route decoder keeps named Out unauthorized in $tail") {
        val (owner, body, types) = views(s"trait Isolated[A]:\n  $primary\n  $tail\n", "Isolated")
        val result = route match
          case "Historical" => InstanceSourceShapeDecoder.decode(owner, body, Some(types))
          case "Curried" => InstanceCurriedMethodSourceShapeDecoder.decode(owner, body, Some(types))
          case "Syntax" => SyntaxSourceShapeDecoder.decode(owner, body, Some(types))
          case _ => DelegatedSourceShapeDecoder.decode("Isolated", owner, body, Some(types))
        assert(result.isLeft, clues(route, result))
        assert(result.left.toOption.get.message.contains("earlier validated concrete alias"))
      }

  private val illegalTails = List(
    "forward alias" -> "type Copy = Later\n  type Later = Out",
    "self alias" -> "type Copy = Copy",
    "mutual alias" -> "type Copy = Other\n  type Other = Copy",
    "missing alias" -> "type Copy = Missing",
    "unrelated alias" -> "type Copy = String",
    "qualified alias" -> "type Copy = scala.Int",
    "applied alias" -> "type Copy = List[Out]",
    "function alias" -> "type Copy = Out => Out",
    "union alias" -> "type Copy = Out | A",
    "intersection alias" -> "type Copy = Out & A",
    "duplicate abstract name" -> "type Out = A",
    "duplicate enclosing name" -> "type A = Out",
    "duplicate tail name" -> "type Copy = Out\n  type Copy = Out",
    "private alias" -> "private type Copy = Out",
    "protected alias" -> "protected type Copy = Out",
    "opaque alias" -> "opaque type Copy = Out",
    "annotated alias" -> "@deprecated type Copy = Out",
    "bounded alias" -> "type Copy <: Out",
    "polymorphic alias" -> "type Copy[B] = Out",
    "abstract method" -> "def identity(value: Out): Out",
    "private method" -> "private def identity(value: Out): Out = value",
    "polymorphic method" -> "def identity[B](value: Out): Out = value",
    "default argument" -> "def identity(value: Out = ???): Out = value",
    "contextual clause" -> "def identity(using value: Out): Out = value",
    "empty clause" -> "def identity(): Out = ???",
    "curried clauses" -> "def identity(value: Out)(other: Out): Out = value",
    "unrelated parameter" -> "def identity(value: String): Out = ???",
    "unrelated result" -> "def identity(value: Out): String = ???",
    "forward method" -> "def identity(value: Later): Out = value\n  type Later = Out",
    "late illegal member" -> "type Copy = Out\n  type Again = Copy\n  def identity(value: Again): Out = value\n  val illegal: Out = ???"
  )
  illegalTails.foreach: (label, tail) =>
    test(s"rejects $label after the validated abstract root") {
      val (owner, body, types) = views(s"trait Rejected[A]:\n  type Out\n  $tail\n", "Rejected")
      val result = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, types)
      assert(result.isLeft, clues(label, result))
      assert(result.left.toOption.get.pos.span.exists)
    }

  test("tail name and cardinality tampering remain rejected after an Out chain") {
    val (owner, body, types) = views(rich, "Rooted")
    val target = types.directTypeMembers.find(_.name == "Again").get
    List(
      types.copy(directTypeMembers = types.directTypeMembers :+ target),
      types.copy(directTypeMembers = types.directTypeMembers.map(t => if t == target then t.copy(name = "Spurious") else t)),
      types.copy(directTypeMembers = types.directTypeMembers.map(t => if t == target then t.copy(bodyIndex = 40) else t)),
      types.copy(directTypeMembers = types.directTypeMembers.map(t => if t == target then t.copy(aliasTarget = Some(DirectTypeShape.EnclosingTypeParameter("Wrong", t.pos))) else t))
    ).foreach: malformed =>
      val result = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body, malformed)
      assert(result.isLeft, clues(result))
  }

  List(-1, 3, 40).foreach: index =>
    test(s"rejects an extra normalized type record at non-type body index $index") {
      val (owner, body, types) = views(rich, "Rooted")
      val orphan = types.directTypeMembers.head.copy(bodyIndex = index, name = "Spurious")
      val result = InstanceAbstractTypeMemberSourceShapeDecoder.decode(owner, body,
        types.copy(directTypeMembers = types.directTypeMembers :+ orphan))
      val problem = result.left.toOption.getOrElse(fail("orphan normalized type record was admitted"))
      assertEquals(problem.message,
        s"unsupported @instance source shape for `Rooted`: normalized direct type member `Spurious` at body index $index must correspond to an actual direct type member")
      assertEquals(problem.pos.span, orphan.pos.span)
    }

  private def views(source: String, name: String) =
    val unit = CompilationUnit(s"${name}RootAuthority.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(t: TypeDef)) => t
      case t: TypeDef => t
      case other => fail(s"expected trait, found $other")
    (
      ExpansionTargetView.decode(primary).fold(d => fail(d.message), identity),
      ExpansionTargetBodyView.decode(primary).fold(d => fail(d.message), identity),
      ExpansionTargetTypeStructureView.decode(primary).fold(d => fail(d.message), identity)
    )
