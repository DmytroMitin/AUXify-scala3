package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.util.{NoSourcePosition, SrcPos}
import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.*

class AnnotatedClassBodyViewConsumerSuite extends munit.FunSuite:
  private val pos: SrcPos = NoSourcePosition

  test("input 042 is sufficient for the exact first instance inspection slice") {
    val inspected = InspectionPolicy.instance(
      canonicalTrait("Monoid"),
      body(
        method("empty", Nil, enclosingA),
        method("combine", List(clause(parameter("a"), parameter("a1"))), enclosingA)
      )
    )

    assertEquals(inspected, true)
  }

  test("instance inspection rejects extra members and unsupported method evidence") {
    val defaulted = parameter("a", hasDefault = true)
    val unsupportedModifiers = publicModifiers.copy(unsupportedFlags = List("inline"))

    assertEquals(
      InspectionPolicy.instance(
        canonicalTrait("Monoid"),
        body(
          method("empty", Nil, enclosingA),
          method("combine", List(clause(defaulted, parameter("a1"))), enclosingA)
        )
      ),
      false
    )
    assertEquals(
      InspectionPolicy.instance(
        canonicalTrait("Monoid"),
        body(
          method("empty", Nil, enclosingA),
          method("combine", List(clause(parameter("a"), parameter("a1"))), enclosingA, unsupportedModifiers),
          DirectMember("extra", DirectMemberKind.Val, None, "deferred-value", pos)
        )
      ),
      false
    )
  }

  test("input 042 is sufficient to identify the first syntax receiver and remaining argument") {
    val result = InspectionPolicy.syntax(
      canonicalTrait("Monoid"),
      body(method("combine", List(clause(parameter("a"), parameter("a1"))), enclosingA))
    )

    assertEquals(result, Some(InspectionPolicy.SyntaxSelection("combine", "a", "a1", "A")))
  }

  test("delegated inspection remains partial when String is an unsupported named result") {
    val result = InspectionPolicy.delegated(
      canonicalTrait("Show"),
      body(
        method(
          "show",
          List(clause(parameter("a"))),
          DirectTypeShape.Unsupported("unqualified-reference", "String", pos)
        )
      )
    )

    assertEquals(result, InspectionPolicy.DelegatedResult.NamedResultUnsupported("unqualified-reference"))
  }

  test("delegated policy never treats Unsupported.summary as semantic type evidence") {
    val summaries = List("String", "not String", "arbitrary decoder text")
    val results = summaries.map: summary =>
      InspectionPolicy.delegated(
        canonicalTrait("Show"),
        body(
          method(
            "show",
            List(clause(parameter("a"))),
            DirectTypeShape.Unsupported("unqualified-reference", summary, pos)
          )
        )
      )

    assertEquals(
      results,
      List.fill(summaries.size)(InspectionPolicy.DelegatedResult.NamedResultUnsupported("unqualified-reference"))
    )
  }

  private val publicModifiers =
    DirectMethodModifiers(
      visibility = DirectVisibility.Public,
      hasAnnotations = false,
      annotationCount = 0,
      unsupportedFlags = Nil
    )

  private val enclosingA = DirectTypeShape.EnclosingTypeParameter("A", pos)

  private def canonicalTrait(name: String): AnnotatedClassView =
    AnnotatedClassView(
      className = name,
      typeParameters = List(
        AnnotatedClassView.TypeParameter(
          name = "A",
          pos = pos,
          variance = AnnotatedClassView.Variance.Invariant,
          isOrdinaryUnbounded = true,
          hasContextBounds = false
        )
      ),
      constructorClauses = Nil,
      modifiers = AnnotatedClassView.Modifiers(
        isCase = false,
        isAbstract = false,
        isFinal = false,
        isSealed = false,
        constructorIsPrivate = false
      ),
      classPos = pos,
      constructorPos = pos,
      definitionKind = AnnotatedClassView.DefinitionKind.Trait
    )

  private def body(members: DirectMember*): AnnotatedClassBodyView =
    AnnotatedClassBodyView(members.toList, pos)

  private def method(
      name: String,
      parameterClauses: List[DirectMethodParameterClause],
      resultType: DirectTypeShape,
      modifiers: DirectMethodModifiers = publicModifiers
  ): DirectMember =
    val value = DirectMethod(
      name = name,
      typeParameters = Nil,
      parameterClauses = parameterClauses,
      resultType = resultType,
      status = DirectMethodStatus.Abstract,
      modifiers = modifiers,
      pos = pos,
      resultTypePos = pos
    )
    DirectMember(name, DirectMemberKind.Method, Some(value), "method", pos)

  private def clause(parameters: DirectMethodParameter*): DirectMethodParameterClause =
    DirectMethodParameterClause(
      parameters = parameters.toList,
      isContextual = false,
      isImplicit = false,
      isGiven = false,
      pos = pos
    )

  private def parameter(name: String, hasDefault: Boolean = false): DirectMethodParameter =
    DirectMethodParameter(
      name = name,
      parameterType = enclosingA,
      isContextual = false,
      isImplicit = false,
      isGiven = false,
      isVal = false,
      isVar = false,
      hasDefault = hasDefault,
      pos = pos,
      typePos = pos
    )

private object InspectionPolicy:
  final case class SyntaxSelection(method: String, receiver: String, remaining: String, enclosingType: String)

  enum DelegatedResult:
    case Sufficient(method: String, parameter: String, enclosingType: String, resultType: String)
    case NamedResultUnsupported(kind: String)
    case ShapeRejected

  def instance(classView: AnnotatedClassView, bodyView: AnnotatedClassBodyView): Boolean =
    canonicalTypeParameter(classView).exists: enclosingName =>
      bodyView.members match
        case emptyMember :: combineMember :: Nil =>
          method(emptyMember).exists: empty =>
            eligible(empty) &&
            empty.name == "empty" &&
            empty.parameterClauses.isEmpty &&
            enclosing(empty.resultType).contains(enclosingName) &&
            method(combineMember).exists: combine =>
              eligible(combine) &&
              combine.name == "combine" &&
              ordinaryParameters(combine).exists: parameters =>
                parameters.map(_.name) == List("a", "a1") &&
                parameters.forall(parameter => enclosing(parameter.parameterType).contains(enclosingName)) &&
                enclosing(combine.resultType).contains(enclosingName)
        case _ => false

  def syntax(classView: AnnotatedClassView, bodyView: AnnotatedClassBodyView): Option[SyntaxSelection] =
    for
      enclosingName <- canonicalTypeParameter(classView)
      direct <- bodyView.members match
        case member :: Nil => method(member)
        case _ => None
      if eligible(direct) && direct.name == "combine"
      parameters <- ordinaryParameters(direct)
      if parameters.size == 2
      if parameters.forall(parameter => enclosing(parameter.parameterType).contains(enclosingName))
      if enclosing(direct.resultType).contains(enclosingName)
    yield SyntaxSelection(direct.name, parameters.head.name, parameters(1).name, enclosingName)

  def delegated(classView: AnnotatedClassView, bodyView: AnnotatedClassBodyView): DelegatedResult =
    val structural = for
      enclosingName <- canonicalTypeParameter(classView)
      direct <- bodyView.members match
        case member :: Nil => method(member)
        case _ => None
      if eligible(direct) && direct.name == "show"
      parameters <- ordinaryParameters(direct)
      if parameters.size == 1
      parameter = parameters.head
      if enclosing(parameter.parameterType).contains(enclosingName)
    yield (direct, parameter, enclosingName)

    structural match
      case Some((direct, parameter, enclosingName)) =>
        direct.resultType match
          case DirectTypeShape.Unsupported(kind, _, _) => DelegatedResult.NamedResultUnsupported(kind)
          case _ => DelegatedResult.ShapeRejected
      case None => DelegatedResult.ShapeRejected

  private def canonicalTypeParameter(classView: AnnotatedClassView): Option[String] =
    if classView.definitionKind != AnnotatedClassView.DefinitionKind.Trait || classView.constructorClauses.nonEmpty then None
    else
      classView.typeParameters match
        case parameter :: Nil
            if parameter.variance == AnnotatedClassView.Variance.Invariant &&
              parameter.isOrdinaryUnbounded &&
              !parameter.hasContextBounds => Some(parameter.name)
        case _ => None

  private def method(member: DirectMember): Option[DirectMethod] =
    Option.when(member.kind == DirectMemberKind.Method)(member.method).flatten

  private def eligible(method: DirectMethod): Boolean =
    method.typeParameters.isEmpty &&
      method.status == DirectMethodStatus.Abstract &&
      method.modifiers.visibility == DirectVisibility.Public &&
      !method.modifiers.hasAnnotations &&
      method.modifiers.annotationCount == 0 &&
      method.modifiers.unsupportedFlags.isEmpty

  private def ordinaryParameters(method: DirectMethod): Option[List[DirectMethodParameter]] =
    method.parameterClauses match
      case clause :: Nil
          if !clause.isContextual && !clause.isImplicit && !clause.isGiven &&
            clause.parameters.forall: parameter =>
              !parameter.isContextual &&
                !parameter.isImplicit &&
                !parameter.isGiven &&
                !parameter.isVal &&
                !parameter.isVar &&
                !parameter.hasDefault => Some(clause.parameters)
      case _ => None

  private def enclosing(shape: DirectTypeShape): Option[String] = shape match
    case DirectTypeShape.EnclosingTypeParameter(name, _) => Some(name)
    case DirectTypeShape.Unsupported(_, _, _) => None
