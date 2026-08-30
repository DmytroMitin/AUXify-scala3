#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"

fail() {
  printf 'milestone verification failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/verify-milestone1.sh from the product root"

if ! java_properties="$(java -XshowSettings:properties -version 2>&1)"; then
  fail "java runtime could not be inspected"
fi

java_feature="$({
  printf '%s\n' "$java_properties" |
    awk -F '= ' '/^[[:space:]]*java\.specification\.version = / { print $2; exit }'
} || true)"
[[ "$java_feature" == "25" ]] ||
  fail "Java feature version 25 is required; found ${java_feature:-unknown}"

sbt -batch verifyPublicModuleCoordinates
sbt -batch 'macroHandlers / Test / test'
sbt -batch 'integrationTests / Test / test'

negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-milestone1-negative.XXXXXX")"
full_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-full-apply-negative.XXXXXX")"
self_conflict_log="$(mktemp "${TMPDIR:-/tmp}/auxify-self-conflict-negative.XXXXXX")"
self_unsupported_log="$(mktemp "${TMPDIR:-/tmp}/auxify-self-unsupported-negative.XXXXXX")"
delegated_negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-delegated-negative.XXXXXX")"
trap 'rm -f "$negative_log" "$full_negative_log" "$self_conflict_log" "$self_unsupported_log" "$delegated_negative_log"' EXIT

if sbt -batch 'negativeUnsupported / Compile / compile' >"$negative_log" 2>&1; then
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

if sbt -batch 'negativeFullUnsupported / Compile / compile' >"$full_negative_log" 2>&1; then
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

if sbt -batch 'negativeSelfConflict / Compile / compile' >"$self_conflict_log" 2>&1; then
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

if sbt -batch 'negativeSelfUnsupported / Compile / compile' >"$self_unsupported_log" 2>&1; then
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

if sbt -batch 'negativeDelegatedUnsupported / Compile / compile' >"$delegated_negative_log" 2>&1; then
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

printf '%s\n' 'AUXIFY_SCALA3_SELF_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_DELEGATED_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_FULL_ADD_OUT_FIRST_SLICE_PASS'
printf '%s\n' 'AUXIFY_SCALA3_APPLY_SHOW_MILESTONE1_PASS'
