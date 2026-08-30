# Public `@delegated` First Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the public one-method `@delegated` slice from normalized source-shape decoding through exact peer lowering, plugin-backed use, negative evidence, external consumption, and truthful documentation.

**Architecture:** AUXify owns admission refinement, semantic-name derivation, evidence-name freshness, and typed Scalameta `Defn.Def` authoring. Quasiquotes exclusively validates and lowers that neutral definition through `DelegatedForwardingMethodPeerBridge`; Macro-Paradise exclusively owns normalized class/body evidence, annotation lifecycle, companion placement, direct-name conflict handling, and rollback.

**Tech Stack:** Scala 3.8.4, JDK 25, sbt, Scalameta 4.17.3, MUnit 1.0.4, Macro-Paradise 0.1.1-SNAPSHOT, Quasiquotes 0.3.0-SNAPSHOT.

**Spec:** `/home/dmytro/projects_personal/AUXify-scala3-control/prompts/029_codex_implement_public_delegated_first_slice.md`

## Global Constraints

- Consume Macro-Paradise product `a7d0e3787550629f96df2f4d00e9dbcb03980565` and Quasiquotes product `bf498a602aeb5e389203cfd2980aabab2c890016` from disposable source checkouts only.
- Do not edit, build, clean, or otherwise mutate designated peer checkouts.
- Decode only `AnnotatedClassView` and `AnnotatedClassBodyView`; never recover semantics from raw Dotty trees or `Unsupported.summary`.
- Build a statically typed Scalameta `Defn.Def`; never parse strings, cast, or author raw Dotty forwarding trees.
- Lower only through `DelegatedForwardingMethodPeerBridge.lower` and place only through `ExpansionHelpers.addMethodToCompanion`.
- Use `CompanionMethodConflictPolicy.PreserveExisting` with its bounded direct raw-name meaning.
- Preserve simple/full `@apply`, first-slice `@self`, public coordinates, canonical milestone, external consumer, and runtime handler absence.
- Keep inputs 039, 041, and 045 open; create no peer request unless a newly proved blocker requires one.
- Execution remains unstaged, uncommitted, and unpushed; omit the generic skill's commit steps because `CODEX_GIT_WORKFLOW.md` and Prompt 029 prohibit them.

---

### Task 1: Reproducible peer pin

**Files:**
- Modify: `scripts/prepare-ci-dependencies.sh`

**Interfaces:**
- Consumes: exact Quasiquotes delivery SHA from Prompt 029.
- Produces: disposable preparation output `QUASIQUOTES_COMMIT=bf498a602aeb5e389203cfd2980aabab2c890016` while leaving Macro-Paradise and sbt-integration pins unchanged.

- [ ] Change only `quasiquotes_commit` to the exact input-043 delivery SHA.
- [ ] Run `bash -n scripts/prepare-ci-dependencies.sh` and confirm success.
- [ ] Run the complete preparation script before focused compilation so the delivered bridge is the resolved local artifact; repeat from the final source state and record exact identities.

### Task 2: Decoder red-green cycle

**Files:**
- Create: `macro-handlers/src/test/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedSourceShapeDecoderSuite.scala`
- Create: `macro-handlers/src/main/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedSourceShapeDecoder.scala`

**Interfaces:**
- Consumes: `AnnotatedClassView`, `AnnotatedClassBodyView`, and their normalized method/type-shape enums.
- Produces: `DelegatedSourceShapeDecoder.SourceShape(traitName, typeParameterName, methodName, parameterName, resultTypeName)` or one `ExpansionDiagnostic` beginning `unsupported @delegated source shape for`.

- [ ] Write parser-backed decoder tests for canonical and coherently renamed facts.
- [ ] Add literal rejection expectations for concrete, polymorphic, zero/multiple parameter, contextual/default/val-var, non-enclosing parameter, applied/qualified/function/unsupported result, extra member, and modifier-bearing shapes.
- [ ] Run the focused suite and verify RED because `DelegatedSourceShapeDecoder` does not exist.
- [ ] Implement the smallest decoder using normalized fields only, with one exact eligible direct method and no `Unsupported.summary` inspection.
- [ ] Run the focused suite and verify GREEN.

### Task 3: Builder red-green cycle

**Files:**
- Create: `macro-handlers/src/test/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedDefinitionBuilderSuite.scala`
- Create: `macro-handlers/src/main/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedDefinitionBuilder.scala`

**Interfaces:**
- Consumes: `DelegatedSourceShapeDecoder.SourceShape`.
- Produces: `definition(shape): Defn.Def`, collision-safe `inst`/`inst1` evidence naming, and `lower(shape)(using Context): Either[DelegatedForwardingMethodPeerBridge.Failure, Lowered]` with virtual source `AuxifyGenerated<Trait><Method>Delegated.scala`.

- [ ] Write literal source/topology tests for canonical, renamed, and ordinary-parameter `inst` collision cases.
- [ ] Exercise the real Quasiquotes bridge and assert exact generated source, virtual source, untyped topology, source provenance, spans, and symbol-free trees.
- [ ] Run the focused suite and verify RED because the builder does not exist.
- [ ] Implement typed Scalameta carriers and quasiquotes with no parser, cast, or raw Dotty construction.
- [ ] Run the focused suite and verify GREEN.

### Task 4: Public marker and handler red-green cycle

**Files:**
- Create: `macro-annotations/src/main/scala/com/github/dmytromitin/auxify/macros/delegated.scala`
- Create: `macro-handlers/src/test/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedHandlerSuite.scala`
- Create: `macro-handlers/src/main/scala/com/github/dmytromitin/auxify/macros/internal/DelegatedHandler.scala`

**Interfaces:**
- Consumes: normalized views, decoder, builder lowering, and Macro-Paradise placement helper.
- Produces: public annotation metadata pointing at `DelegatedHandler`; handler profile `RestrictedGenericTraitApply`, `consumesExistingCompanion = true`, and atomic rejected/structured outcomes.

- [ ] Write handler tests for exact profile, canonical structured output, unrelated companion preservation, direct same-name `PreserveExisting`, decoder rejection fallback identity, and injected bridge failure with no partial companion edit.
- [ ] Add the public plugin-backed fixtures/tests now so the marker/handler path also participates in the initial RED proof.
- [ ] Run the focused suite and verify RED because marker/handler are absent.
- [ ] Implement the marker in established annotation style.
- [ ] Implement `expandWithLowering` seam, normalized-view flow, exact bridge failure diagnostic, and `PreserveExisting` placement.
- [ ] Run decoder/builder/handler suites together and verify GREEN.

### Task 5: Real plugin integration and controlled negatives

**Files:**
- Create: `integration-tests/src/main/scala/com/github/dmytromitin/auxify/integration/delegated/DelegatedFixtures.scala`
- Create: `integration-tests/src/test/scala/com/github/dmytromitin/auxify/integration/delegated/DelegatedIntegrationSuite.scala`
- Create: `negative-delegated-unsupported/src/main/scala/com/github/dmytromitin/auxify/negative/delegated/DelegatedNegativeTypes.scala`
- Modify: `build.sbt`
- Modify: `scripts/verify-milestone1.sh`

**Interfaces:**
- Consumes: public marker artifact and hidden handler path through the real compiler plugin.
- Produces: canonical `Show.show(42)`, renamed forwarding, existing companion merge, direct-conflict preservation, and deterministic compile failures for concrete/applied-result/method-polymorphic/wrong-topology shapes.

- [ ] Re-run the public plugin-backed fixtures/tests after the handler GREEN cycle and verify canonical, renamed, unrelated-companion, and direct-conflict behavior.
- [ ] Add the bounded negative subproject and extend the canonical script with literal AUXify diagnostic checks and crash/stack-frame guards.
- [ ] Run focused integration and negative compilation, fixing only behavior revealed by failing tests.
- [ ] Run simple/full apply and self integration regressions from the same state.

### Task 6: Public docs, controller reconciliation, and handoff

**Files:**
- Modify: `README.md`
- Modify: `/home/dmytro/projects_personal/AUXify-scala3-control/ROADMAP.md`
- Create: `/home/dmytro/projects_personal/AUXify-scala3-control/reviews/029_chatgpt_implement_public_delegated_first_slice_handoff/SUMMARY.md`
- Create: `/home/dmytro/projects_personal/AUXify-scala3-control/reviews/029_chatgpt_implement_public_delegated_first_slice_handoff/VERIFICATION.md`
- Create: `/home/dmytro/projects_personal/AUXify-scala3-control/reviews/029_chatgpt_implement_public_delegated_first_slice_handoff/DELEGATED_CONTRACT.md`

**Interfaces:**
- Consumes: final observed verification facts and peer refresh.
- Produces: truthful first-slice support status, exact ownership split, deferred parity list, open input statuses, changed paths, and execution Git state.

- [ ] Update the support matrix and exact narrow boundary without claiming historical parity.
- [ ] Change only the delegated first-slice ROADMAP state from stale waiting to DONE after all local gates pass.
- [ ] Re-read live product/control/input histories for both peers and record only task-relevant changes.
- [ ] Write the three required handoff files with every mandatory Prompt 029 field.

### Task 7: Final verification and external consumer

**Files:**
- Modify only if a failing test exposes a covered defect.

**Interfaces:**
- Consumes: final product/controller trees.
- Produces: evidence for fresh preparation, focused tests, integrations, negatives, canonical milestone, public coordinates, standalone generic sbt consumer, runtime handler absence, and clean diff checks.

- [ ] Run fresh exact peer dependency preparation using disposable clones.
- [ ] Run focused decoder/builder/handler tests.
- [ ] Run all macro-handler tests and all plugin-backed integration tests.
- [ ] Run the delegated negative matrix and every existing negative regression.
- [ ] Run `verifyPublicModuleCoordinates` and `scripts/verify-milestone1.sh`.
- [ ] Prepare the unchanged generic Macro-Paradise sbt integration and create a fresh `/tmp` standalone consumer using only public artifacts; exercise simple/full apply, self, and delegated.
- [ ] Assert the handler artifact and its closure are absent from ordinary runtime.
- [ ] Run script syntax, forbidden coupling, `git diff --check`, staged-diff, status, and changed-path audits in both writable repositories.
- [ ] Confirm no stage, commit, push, tag, release, PR, peer mutation, or new peer request occurred.
