#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"
scala_version="${AUXIFY_SCALA_VERSION:-3.8.4}"

fail() {
  printf 'milestone verification failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/verify-milestone1.sh from the product root"

case "$scala_version" in
  3.3.8|3.8.4|3.9.0) ;;
  *) fail "unsupported exact Scala version: $scala_version; expected 3.3.8, 3.8.4, or 3.9.0" ;;
esac

run_sbt() {
  sbt -Dauxify.scalaVersion="$scala_version" -batch "$@"
}

if ! java_properties="$(java -XshowSettings:properties -version 2>&1)"; then
  fail "java runtime could not be inspected"
fi

java_feature="$({
  printf '%s\n' "$java_properties" |
    awk -F '= ' '/^[[:space:]]*java\.specification\.version = / { print $2; exit }'
} || true)"
[[ "$java_feature" == "25" ]] ||
  fail "Java feature version 25 is required; found ${java_feature:-unknown}"

printf 'AUXIFY_SCALA_VERSION=%s\n' "$scala_version"
printf 'AUXIFY_JAVA_FEATURE=%s\n' "$java_feature"

run_sbt verifyPublicModuleCoordinates verifyReleaseReadiness
run_sbt 'macroHandlers / Test / test'
run_sbt 'integrationTests / Test / test'
run_sbt 'macroAnnotations / publishLocal' 'macroHandlers / publishLocal'

negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-milestone1-negative.XXXXXX")"
full_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-full-apply-negative.XXXXXX")"
self_conflict_log="$(mktemp "${TMPDIR:-/tmp}/auxify-self-conflict-negative.XXXXXX")"
self_unsupported_log="$(mktemp "${TMPDIR:-/tmp}/auxify-self-unsupported-negative.XXXXXX")"
delegated_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-delegated-negative.XXXXXX")"
composition_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-composition-negative.XXXXXX")"
aux_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-aux-negative.XXXXXX")"
instance_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-instance-negative.XXXXXX")"
external_root=""
trap 'rm -f "$negative_log" "$full_negative_log" "$self_conflict_log" "$self_unsupported_log" "$delegated_negative_log" "$composition_negative_log" "$aux_negative_log" "$instance_negative_log"; [[ -z "$external_root" ]] || rm -rf -- "$external_root"' EXIT

if run_sbt 'negativeUnsupported / Compile / compile' >"$negative_log" 2>&1; then
  negative_status=0
else
  negative_status=$?
fi

printf '%s\n' '--- controlled unsupported-target diagnostic ---'
cat "$negative_log"

[[ "$negative_status" -ne 0 ]] ||
  fail "negativeUnsupported compiled successfully; the unsupported class was admitted"

grep -Eiq \
  'requires .*trait.*found class|(target profile|handler).*(unsupported|not supported|rejected)|(unsupported|not supported|rejected)[[:space:]]+(target|target profile|profile|handler)' \
  "$negative_log" ||
  fail "negative diagnostic does not identify an unsupported or rejected target/profile"

grep -Eiq \
  'UnsupportedApplyTarget|com\.github\.dmytromitin\.auxify\.macros\.apply|macroparadise|ApplyHandler' \
  "$negative_log" ||
  fail "negative diagnostic does not identify the annotated target or handler path"

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$negative_log"; then
  fail "negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$negative_log"; then
  fail "negative compile emitted an uncaught stack frame"
fi

if run_sbt 'negativeFullUnsupported / Compile / compile' >"$full_negative_log" 2>&1; then
  full_negative_status=0
else
  full_negative_status=$?
fi

printf '%s\n' '--- controlled full-apply source-shape diagnostics ---'
cat "$full_negative_log"

[[ "$full_negative_status" -ne 0 ]] ||
  fail "negativeFullUnsupported compiled successfully; unsupported full shapes were admitted"

for expected_diagnostic in \
  'unsupported full @apply source shape for `MismatchedEnclosingBounds`: enclosing type-parameter upper bounds must be the same named type' \
  'unsupported full @apply source shape for `AliasResult`: result type member `Out` must be abstract bounds, found alias' \
  'unsupported full @apply source shape for `LowerBoundedResult`: result type member `Out` must not define a lower bound' \
  'unsupported full @apply source shape for `MismatchedResult`: result type member `Out` upper bound must match enclosing bound `Nat`' \
  'unsupported full @apply source shape for `MultipleResults`: requires exactly one direct type member; found 2' \
  'unsupported full @apply source shape for `PolymorphicResult`: result type member `Out` must not declare type parameters' \
  'unsupported full @apply source shape for `ProtectedResult`: result type member `Out` must be public, unannotated, and free of unsupported modifiers' \
  'unsupported full @apply source shape for `AppliedBounds`: enclosing type-parameter upper bounds must be unqualified named types'; do
  grep -Fq -- "$expected_diagnostic" "$full_negative_log" ||
    fail "full-apply negative compile omitted expected diagnostic: $expected_diagnostic"
done

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$full_negative_log"; then
  fail "full-apply negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$full_negative_log"; then
  fail "full-apply negative compile emitted an uncaught stack frame"
fi

if run_sbt 'negativeSelfConflict / Compile / compile' >"$self_conflict_log" 2>&1; then
  self_conflict_status=0
else
  self_conflict_status=$?
fi

printf '%s\n' '--- controlled direct-Self-conflict diagnostic ---'
cat "$self_conflict_log"

[[ "$self_conflict_status" -ne 0 ]] ||
  fail "negativeSelfConflict compiled successfully; the direct Self conflict was admitted"

grep -Fq \
  'already contains direct type member `Self`; bounded self preparation requires deterministic rejection' \
  "$self_conflict_log" ||
  fail "direct Self conflict did not emit the stable project-owned diagnostic"

if run_sbt 'negativeSelfUnsupported / Compile / compile' >"$self_unsupported_log" 2>&1; then
  self_unsupported_status=0
else
  self_unsupported_status=$?
fi

printf '%s\n' '--- controlled unsupported-self-target diagnostic ---'
cat "$self_unsupported_log"

[[ "$self_unsupported_status" -ne 0 ]] ||
  fail "negativeSelfUnsupported compiled successfully; the unsupported class was admitted"

grep -Eiq \
  'requires .*trait.*found class.*UnsupportedSelfTarget|UnsupportedSelfTarget.*requires .*trait' \
  "$self_unsupported_log" ||
  fail "unsupported @self target did not emit the stable trait-profile diagnostic"

for self_negative_log in "$self_conflict_log" "$self_unsupported_log"; do
  if grep -Eiq \
    'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
    "$self_negative_log"; then
    fail "@self negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
  fi

  if grep -Eq \
    '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
    "$self_negative_log"; then
    fail "@self negative compile emitted an uncaught stack frame"
  fi
done

if run_sbt 'negativeDelegatedUnsupported / Compile / compile' >"$delegated_negative_log" 2>&1; then
  delegated_negative_status=0
else
  delegated_negative_status=$?
fi

printf '%s\n' '--- controlled delegated first-slice source-shape diagnostics ---'
cat "$delegated_negative_log"

[[ "$delegated_negative_status" -ne 0 ]] ||
  fail "negativeDelegatedUnsupported compiled successfully; unsupported delegated shapes were admitted"

for expected_diagnostic in \
  'unsupported @delegated source shape for `ConcreteDelegated`: direct method `show` must be abstract' \
  'unsupported @delegated source shape for `AppliedResultDelegated`: direct method `show` result type must be one unqualified named type' \
  'unsupported @delegated source shape for `PolymorphicDelegated`: direct method `show` must not declare method type parameters' \
  'unsupported @delegated source shape for `WrongTopologyDelegated`: direct method `show` requires exactly one ordinary parameter; found 2'; do
  grep -Fq -- "$expected_diagnostic" "$delegated_negative_log" ||
    fail "delegated negative compile omitted expected diagnostic: $expected_diagnostic"
done

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$delegated_negative_log"; then
  fail "delegated negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$delegated_negative_log"; then
  fail "delegated negative compile emitted an uncaught stack frame"
fi

if run_sbt 'negativeAuxUnsupported / Compile / compile' >"$aux_negative_log" 2>&1; then
  aux_negative_status=0
else
  aux_negative_status=$?
fi

printf '%s\n' '--- controlled aux first-slice source-shape diagnostics ---'
cat "$aux_negative_log"

[[ "$aux_negative_status" -ne 0 ]] ||
  fail "negativeAuxUnsupported compiled successfully; unsupported aux shapes were admitted"

for expected_diagnostic in \
  'unsupported @aux source shape for `MismatchedEnclosingBounds`: enclosing type-parameter upper bounds must be the same named type' \
  'unsupported @aux source shape for `AliasResult`: result type member `Out` must be abstract bounds, found alias' \
  'unsupported @aux source shape for `LowerBoundedResult`: result type member `Out` must not define a lower bound' \
  'unsupported @aux source shape for `MismatchedResult`: result type member `Out` upper bound must match enclosing bound `Nat`' \
  'unsupported @aux source shape for `MultipleResults`: requires exactly one direct type member; found 2' \
  'unsupported @aux source shape for `PolymorphicResult`: result type member `Out` must not declare type parameters'; do
  grep -Fq -- "$expected_diagnostic" "$aux_negative_log" ||
    fail "aux negative compile omitted expected diagnostic: $expected_diagnostic"
done

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$aux_negative_log"; then
  fail "aux negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$aux_negative_log"; then
  fail "aux negative compile emitted an uncaught stack frame"
fi

if run_sbt 'negativeInstanceUnsupported / Compile / compile' >"$instance_negative_log" 2>&1; then
  instance_negative_status=0
else
  instance_negative_status=$?
fi

printf '%s\n' '--- controlled instance source-shape diagnostics ---'
cat "$instance_negative_log"

[[ "$instance_negative_status" -ne 0 ]] ||
  fail "negativeInstanceUnsupported compiled successfully; unsupported instance shapes were admitted"

for expected_diagnostic in \
  'found class `ClassTarget`' \
  'found 2 type parameters' \
  'type parameter `A` is covariant' \
  'type parameter `A` has an explicit or contextual bound' \
  'unsupported @instance source shape for `Concrete`: direct method `empty` must be abstract' \
  'unsupported @instance source shape for `Polymorphic`: direct method `combine` must not declare method type parameters' \
  'unsupported @instance source shape for `Reversed`: parameterless method `combine` must declare no parameter clauses; found 1' \
  'unsupported @instance source shape for `EmptyClause`: parameterless method `empty` must declare no parameter clauses; found 1' \
  'unsupported @instance source shape for `WrongArity`: binary method `combine` requires exactly two ordinary parameters; found 1' \
  'unsupported @instance source shape for `ContextualClause`: binary method `combine` parameter clause must be ordinary and non-contextual' \
  'unsupported @instance source shape for `Defaulted`: binary method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified' \
  'unsupported @instance source shape for `WrongParameter`: binary method `combine` parameter `a` must use enclosing type parameter `A`' \
  'unsupported @instance source shape for `WrongEmptyResult`: parameterless method `empty` result type must use enclosing type parameter `A`' \
  'unsupported @instance source shape for `WrongBinaryResult`: binary method `combine` result type must use enclosing type parameter `A`' \
  'unsupported @instance source shape for `ExtraVal`: requires exactly two direct body members; found 3' \
  'unsupported @instance source shape for `ExtraVar`: requires exactly two direct body members; found 3' \
  'unsupported @instance source shape for `ExtraType`: requires exactly two direct body members; found 3' \
  'unsupported @instance source shape for `ExtraNested`: requires exactly two direct body members; found 3' \
  'unsupported @instance source shape for `ProtectedMethod`: direct method `empty` must be public, unannotated, and free of unsupported modifiers' \
  'unsupported @instance source shape for `AnnotatedMethod`: direct method `combine` must be public, unannotated, and free of unsupported modifiers'; do
  grep -Fq -- "$expected_diagnostic" "$instance_negative_log" ||
    fail "instance negative compile omitted expected diagnostic: $expected_diagnostic"
done

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$instance_negative_log"; then
  fail "instance negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$instance_negative_log"; then
  fail "instance negative compile emitted an uncaught stack frame"
fi

run_sbt 'negativeCompositionLateRejection / clean'
if run_sbt 'negativeCompositionLateRejection / Compile / compile' >"$composition_negative_log" 2>&1; then
  composition_negative_status=0
else
  composition_negative_status=$?
fi

printf '%s\n' '--- controlled late composition rejection and rollback ---'
cat "$composition_negative_log"

[[ "$composition_negative_status" -ne 0 ]] ||
  fail "negativeCompositionLateRejection compiled successfully; the late delegated rejection was lost"

grep -Fq \
  'unsupported @delegated source shape for `LateDelegatedRejection`: direct method `show` result type must be one unqualified named type' \
  "$composition_negative_log" ||
  fail "late composition rejection omitted the deterministic delegated decoder diagnostic"

if grep -Eiq \
  'Exception in thread|(^|[[:space:]])([[:alpha:]_$][[:alnum:]_$]*\.)+[[:alpha:]_$][[:alnum:]_$]*(Exception|Error)(:|[[:space:]]|$)|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$composition_negative_log"; then
  fail "late composition rejection emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
fi

if grep -Eq \
  '^[[:space:]]*at[[:space:]]+[[:alnum:]_$./<>-]+\.[[:alnum:]_$<>-]+\([^)]*\)[[:space:]]*$' \
  "$composition_negative_log"; then
  fail "late composition rejection emitted an uncaught stack frame"
fi

composition_classes="$product_root/negative-composition-late-rejection/target/scala-$scala_version/classes"
if [[ -d "$composition_classes" ]] && find "$composition_classes" -type f \
  \( -name '*.class' -o -name '*.tasty' \) -print -quit | grep -q .; then
  fail "late composition rejection left partial class or TASTy output"
fi

mapfile -d '' build_config_sources < <(
  git ls-files -z -- \
    'build.sbt' '*.sbt' 'project/**' '.sbtopts' '.jvmopts' \
    'build.sc' '*.mill' 'pom.xml' 'gradle/**' 'gradle.properties' \
    'settings.gradle' 'settings.gradle.kts' 'build.gradle' 'build.gradle.kts'
)

[[ "${#build_config_sources[@]}" -gt 0 ]] ||
  fail "no tracked build/config sources were found for the coupling scan"

for forbidden in \
  '/home/dmytro/projects_personal/quasiquotes-scala3' \
  '/home/dmytro/projects_personal/macroparadise-scala3' \
  '../quasiquotes-scala3' \
  '../macroparadise-scala3' \
  'ProjectRef' \
  'RootProject'; do
  if grep -FnH -- "$forbidden" "${build_config_sources[@]}"; then
    fail "tracked build/config sources contain forbidden dependency source coupling: $forbidden"
  else
    grep_status=$?
    [[ "$grep_status" -eq 1 ]] ||
      fail "could not inspect tracked build/config sources for forbidden dependency source coupling"
  fi
done

external_root="$(mktemp -d "${TMPDIR:-/tmp}/auxify-external-consumer.XXXXXX")"
cp -R "$product_root/qualification/external-consumer/." "$external_root"

(
  cd "$external_root"
  sbt -Dauxify.scalaVersion="$scala_version" -batch \
    clean \
    verifyExternalPolicy \
    run
)

rm -rf -- "$external_root"
external_root=""

printf '%s\n' 'AUXIFY_SCALA3_SELF_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_DELEGATED_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_FULL_ADD_OUT_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_AUX_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_INSTANCE_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_DELEGATED_COMPOSITION_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_AUX_POSITIVE_ROWS_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_AUX_SOURCE_DECODER_LATE_REJECTION_STRUCTURALLY_UNREACHABLE'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_AUX_BOUNDED_COMPOSITION_PASS'
printf 'AUXIFY_SCALA3_APPLY_SHOW_MILESTONE1_PASS scala=%s jdk=%s\n' \
  "$scala_version" \
  "$java_feature"
