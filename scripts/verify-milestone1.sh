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

sbt -batch 'macroHandlers / Test / test'
sbt -batch 'integrationTests / Test / test'

negative_log="$(mktemp "${TMPDIR:-/tmp}/auxify-milestone1-negative.XXXXXX")"
trap 'rm -f "$negative_log"' EXIT

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
  'unsupported|not supported|rejected|target profile|RestrictedGenericTraitApply|requires .*trait.*found class' \
  "$negative_log" ||
  fail "negative diagnostic does not identify an unsupported or rejected target/profile"

grep -Eiq \
  'UnsupportedApplyTarget|com\.github\.dmytromitin\.auxify\.macros\.apply|macroparadise|ApplyHandler' \
  "$negative_log" ||
  fail "negative diagnostic does not identify the annotated target or handler path"

if grep -Eiq \
  '(^|[[:space:]])at (java|scala|dotty)\.|Exception in thread|LinkageError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|AssertionError|assertion failed|compiler (assertion|crash)|uncaught (Java|Scala|exception)|StackOverflowError|FatalError' \
  "$negative_log"; then
  fail "negative compile emitted an uncaught stack trace, linkage/class-loading failure, assertion, or crash marker"
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
  fi
done

printf '%s\n' 'AUXIFY_SCALA3_APPLY_SHOW_MILESTONE1_PASS'
