package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.{CompilationUnit, Main}
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.interfaces.{Diagnostic, SimpleReporter}
import dotty.tools.dotc.parsing.Parsers

import java.io.File
import java.nio.file.Files
import scala.jdk.CollectionConverters.*

import paradise3.api.{AnnotatedClassTypeStructureView, AnnotatedClassView, ExpansionInput, ExpansionOutcome}
import paradise3.api.AnnotatedClassBodyView.{DirectTypeShape, DirectVisibility}
import paradise3.api.AnnotatedClassTypeStructureView.{Bound, DirectTypeMemberKind}

class CurrentPublicTypeMemberModifierAdmissionSuite extends munit.FunSuite:
  private case class Row(label: String, declaration: String, kind: DirectTypeMemberKind,
      parameters: List[String], upperBound: Boolean, alias: Boolean, reason: Option[String])

  private val modifierReason =
    "result type member `Out` must be public, unannotated, and free of unsupported modifiers"
  private val rows = List(
    Row("Bounded", "type Out <: Nat", DirectTypeMemberKind.AbstractBounds,
      Nil, true, false, None),
    Row("Unbounded", "type Out", DirectTypeMemberKind.AbstractBounds,
      Nil, false, false, Some("result type member `Out` upper bound must be an unqualified named type")),
    Row("BinaryAbstract", "type Out[X, Y] <: Nat", DirectTypeMemberKind.AbstractBounds,
      List("X", "Y"), true, false, Some("result type member `Out` must not declare type parameters")),
    Row("Alias", "type Out = Nat", DirectTypeMemberKind.Alias,
      Nil, false, true, Some("result type member `Out` must be abstract bounds, found alias")),
    Row("BinaryAlias", "type Out[X, Y] = Nat", DirectTypeMemberKind.Alias,
      List("X", "Y"), false, true, Some("result type member `Out` must be abstract bounds, found alias"))
  )

  test("release compiler accepts every plain and infix abstract or alias source row") {
    val directory = Files.createTempDirectory("auxify-type-member-native-")
    try
      val source = directory.resolve("Native.scala")
      val output = Files.createDirectory(directory.resolve("out"))
      val definitions = for row <- rows; infix <- List(false, true) yield
        val name = s"${if infix then "Infix" else "Plain"}${row.label}"
        s"trait $name[N <: Nat, M <: Nat] { ${if infix then "infix " else ""}${row.declaration} }"
      Files.writeString(source, "trait Nat\n" + definitions.mkString("\n"))
      val messages = scala.collection.mutable.ListBuffer.empty[String]
      val reporter = new SimpleReporter:
        override def report(diagnostic: Diagnostic): Unit = messages += diagnostic.message()
      def location(clazz: Class[?]): String =
        new File(clazz.getProtectionDomain.getCodeSource.getLocation.toURI).getAbsolutePath
      val classpath = List(location(classOf[scala.Option[?]]), location(classOf[scala.deriving.Mirror]))
        .distinct.mkString(File.pathSeparator)
      val result = Main.process(Array("-classpath", classpath, "-d", output.toString,
        source.toString), reporter, null)
      assert(!result.hasErrors(), clue(messages.toList))
      for row <- rows; prefix <- List("Plain", "Infix") do
        assert(Files.exists(output.resolve(s"$prefix${row.label}.class")))
        assert(Files.exists(output.resolve(s"$prefix${row.label}.tasty")))
    finally
      val stream = Files.walk(directory)
      try stream.iterator().asScala.toList.reverse.foreach(Files.delete)
      finally stream.close()
  }

  for row <- rows; infix <- List(false) do
    val name = s"${if infix then "Infix" else "Plain"}${row.label}"
    test(s"$name retains normalized facts and both handlers enforce source admission") {
      val source = s"""@current
                      |trait $name[N <: Nat, M <: Nat]:
                      |  ${if infix then "infix " else ""}${row.declaration}
                      |object $name:
                      |  val retained = 41
                      |""".stripMargin
      val unit = CompilationUnit(s"$name.scala", source)
      given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
      val stats = new Parsers.Parser(unit.source).parse() match
        case PackageDef(_, values) => values
        case other => fail(s"missing package stats in $other")
      assert(!summon[Context].reporter.hasErrors)
      val primary = stats.collectFirst { case value: TypeDef => value }.get
      val companion = stats.collectFirst { case value: ModuleDef => value }.get
      val view = AnnotatedClassTypeStructureView.decode(primary)
        .fold(diagnostic => fail(diagnostic.message), identity)
      assertEquals(view.typeParameters.map(_.name), List("N", "M"))
      view.typeParameters.foreach: parameter =>
        assertEquals(parameter.variance, AnnotatedClassView.Variance.Invariant)
        assertEquals(parameter.lowerBound, Bound.Absent)
        assertNamedBound(parameter.upperBound)
        assert(!parameter.hasContextBounds)
        assert(parameter.pos.span.exists)
      assert(view.pos.span.exists)
      assertEquals(view.directTypeMembers.size, 1)
      val member = view.directTypeMembers.head
      assertEquals(member.name, "Out")
      assertEquals(member.bodyIndex, 0)
      assertEquals(member.kind, row.kind)
      assertEquals(member.typeParameters.map(_.name), row.parameters)
      assert(member.typeParameters.forall(_.pos.span.exists))
      assertEquals(member.lowerBound, Bound.Absent)
      if row.upperBound then assertNamedBound(member.upperBound)
      else assertEquals(member.upperBound, Bound.Absent)
      if row.alias then
        member.aliasTarget match
          case Some(DirectTypeShape.NamedType("Nat", pos)) => assert(pos.span.exists)
          case other => fail(s"missing named alias target: $other")
      else assertEquals(member.aliasTarget, None)
      assertEquals(member.modifiers.visibility, DirectVisibility.Public)
      assert(!member.modifiers.hasAnnotations)
      assertEquals(member.modifiers.annotationCount, 0)
      assertEquals(member.modifiers.unsupportedFlags, if infix then List("infix") else Nil)
      assert(member.pos.span.exists)
      assertEquals(member.pos.sourcePos.line, 2)

      // Kind and member-parameter checks intentionally precede modifier checks.
      val reason =
        if infix && !row.alias && row.parameters.isEmpty then Some(modifierReason)
        else row.reason
      val applyResult = ApplyFullShapeDecoder.decode(name, view)
      val auxResult = AuxSourceShapeDecoder.decode(name, view)
      val template = primary.rhs
      val companionBody = companion.impl.body
      val current = Trees.mods(primary).annotations.head
      for annotation <- List("apply", "aux") do
        val input = ExpansionInput(s"com.github.dmytromitin.auxify.macros.$annotation",
          primary, Some(companion), Set(name), Some(current))
        val diagnosticPrefix = if annotation == "apply" then "full @apply" else "@aux"
        reason match
          case Some(detail) =>
            val expected = s"unsupported $diagnosticPrefix source shape for `$name`: $detail"
            val diagnostic = (if annotation == "apply" then applyResult.left.toOption
              else auxResult.left.toOption).getOrElse(fail("decoder admitted unsupported row"))
            assertEquals(diagnostic.message, expected)
            assertEquals(diagnostic.pos, member.pos)
            val outcome = if annotation == "apply" then new ApplyHandler().expand(input)
              else AuxHandler.expandWithLowering(input)((_, _) => fail("lowering reached after rejection"))
            outcome match
              case ExpansionOutcome.Rejected(diagnostics, fallback) =>
                assertEquals(diagnostics.map(_.message), List(expected))
                assertEquals(diagnostics.map(_.pos), List(member.pos))
                assert(fallback.eq(primary))
              case other => fail(s"expected controlled rejection, found $other")
          case None =>
            assert(applyResult.isRight)
            assert(auxResult.isRight)
            val outcome = if annotation == "apply" then new ApplyHandler().expand(input)
              else new AuxHandler().expand(input)
            assert(outcome.isInstanceOf[ExpansionOutcome.Structured], clue(outcome))
        assert(primary.rhs.eq(template))
        assert(companion.impl.body.eq(companionBody))
    }

  private def assertNamedBound(bound: Bound): Unit = bound match
    case Bound.Present(DirectTypeShape.NamedType("Nat", pos)) => assert(pos.span.exists)
    case other => fail(s"expected named Nat bound, found $other")
