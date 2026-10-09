#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"

macro_paradise_repository="https://github.com/DmytroMitin/macroparadise-scala3.git"
macro_paradise_commit="aae704ca42ff01ee44e663fb024c726a357716c7"
macro_paradise_version="0.2.0-SNAPSHOT"
quasiquotes_repository="https://github.com/DmytroMitin/quasiquotes-scala3.git"
quasiquotes_commit="e5ee36156fa0ed75e5aa04de42c9eacb6db656fb"
quasiquotes_version="0.4.0-SNAPSHOT"
quasiquotes_binary_scala_version="3.3.8"
scala_version="${AUXIFY_SCALA_VERSION:-3.8.4}"

fail() {
  printf 'CI dependency preparation failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/prepare-ci-dependencies.sh from the product root"

case "$scala_version" in
  3.3.8|3.8.4|3.9.0) ;;
  *) fail "unsupported exact Scala version: $scala_version; expected 3.3.8, 3.8.4, or 3.9.0" ;;
esac

printf 'AUXIFY_SCALA_VERSION=%s\n' "$scala_version"
printf 'MACRO_PARADISE_EXPECTED_COMMIT=%s\n' "$macro_paradise_commit"
printf 'MACRO_PARADISE_DEVELOPMENT_VERSION=%s\n' "$macro_paradise_version"
printf 'QUASIQUOTES_EXPECTED_COMMIT=%s\n' "$quasiquotes_commit"
printf 'QUASIQUOTES_DEVELOPMENT_VERSION=%s\n' "$quasiquotes_version"
printf 'QUASIQUOTES_BINARY_ARTIFACT_SCALA_VERSION=%s\n' "$quasiquotes_binary_scala_version"

for command in git sbt java sha256sum; do
  command -v "$command" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command"
done

dependency_root="$(mktemp -d "${TMPDIR:-/tmp}/auxify-ci-dependencies.XXXXXX")"
trap 'rm -rf -- "$dependency_root"' EXIT

dependency_state_root="$product_root/target/ci-dependencies/$scala_version-$macro_paradise_commit-$quasiquotes_commit"
ivy_home="$dependency_state_root/ivy"
coursier_cache="$dependency_state_root/coursier-cache"
mkdir -p "$ivy_home" "$coursier_cache"
export COURSIER_CACHE="$coursier_cache"

printf 'AUXIFY_DEPENDENCY_STATE_ROOT=%s\n' "$dependency_state_root"
printf 'AUXIFY_IVY_HOME=%s\n' "$ivy_home"
printf 'AUXIFY_COURSIER_CACHE=%s\n' "$coursier_cache"

clone_at_commit() {
  local repository="$1"
  local commit="$2"
  local destination="$3"
  local producer="$4"

  git clone --filter=blob:none --no-checkout "$repository" "$destination"
  git -C "$destination" checkout --detach "$commit"

  local actual_commit
  actual_commit="$(git -C "$destination" rev-parse HEAD)"
  [[ "$actual_commit" == "$commit" ]] ||
    fail "$producer checkout identity mismatch: expected $commit, found $actual_commit"

  printf '%s_COMMIT=%s\n' "$producer" "$actual_commit"
}

macro_paradise_checkout="$dependency_root/macroparadise-scala3"
quasiquotes_checkout="$dependency_root/quasiquotes-scala3"

clone_at_commit \
  "$macro_paradise_repository" \
  "$macro_paradise_commit" \
  "$macro_paradise_checkout" \
  MACRO_PARADISE

(
  cd "$macro_paradise_checkout"
  sbt -batch \
    -Dsbt.ivy.home="$ivy_home" \
    -Dmacroparadise.exactScalaVersion="$scala_version" \
    "++$scala_version!" \
    "pluginApi/publishLocal" \
    "plugin/publishLocal"
)

clone_at_commit \
  "$quasiquotes_repository" \
  "$quasiquotes_commit" \
  "$quasiquotes_checkout" \
  QUASIQUOTES

(
  cd "$quasiquotes_checkout"
  sbt -batch \
    -Dsbt.ivy.home="$ivy_home" \
    "++$quasiquotes_binary_scala_version!" \
    "core/publishLocal" \
    "neutralScalameta/publishLocal" \
    "++$scala_version!" \
    "dottyInternal/publishLocal"
)

hash_single_artifact() {
  local module="$1"
  local artifact_glob="$2"
  local -a artifacts=()

  mapfile -t artifacts < <(find "$ivy_home/local/com.github.dmytromitin/$module" -type f -path "$artifact_glob" -print | sort)
  [[ "${#artifacts[@]}" -eq 1 ]] ||
    fail "expected one published $module artifact, found ${#artifacts[@]}"

  local digest
  digest="$(sha256sum "${artifacts[0]}" | awk '{ print $1 }')"
  printf 'QUASIQUOTES_ARTIFACT_SHA256 module=%s scala=%s sha256=%s file=%s\n' \
    "$module" \
    "$scala_version" \
    "$digest" \
    "${artifacts[0]}"
}

hash_single_artifact \
  "quasiquotes-scala3-core_3" \
  "*/$quasiquotes_version/jars/*.jar"
hash_single_artifact \
  "quasiquotes-scala3-neutral-scalameta_3" \
  "*/$quasiquotes_version/jars/*.jar"
hash_single_artifact \
  "quasiquotes-scala3-dotty-internal_$scala_version" \
  "*/$quasiquotes_version/jars/*.jar"

# The dependency state lives below the root target directory, so root/clean would
# delete the source-built artifacts. Clean every consumer subproject explicitly
# instead; this invalidates sbt update reports that may still name an older exact
# source snapshot while preserving the freshly prepared task-owned repository.
sbt -batch \
  -Dsbt.ivy.home="$ivy_home" \
  -Dauxify.scalaVersion="$scala_version" \
  'macroAnnotations / clean' \
  'macroHandlers / clean' \
  'integrationTests / clean' \
  'negativeUnsupported / clean' \
  'negativeFullUnsupported / clean' \
  'negativeSelfConflict / clean' \
  'negativeSelfUnsupported / clean' \
  'negativeDelegatedUnsupported / clean' \
  'negativeCompositionLateRejection / clean' \
  'negativeApplyInstanceComposition / clean' \
  'negativeInstanceMethodModifiers / clean' \
  'negativeDelegatedMethodModifiers / clean' \
  'negativeApplyInstanceMethodModifiers / clean' \
  'negativeTypeMemberModifiers / clean' \
  'negativeAuxUnsupported / clean' \
  'negativeInstanceUnsupported / clean' \
  'negativeSyntaxUnsupported / clean'

printf 'AUXIFY_SCALA3_CI_DEPENDENCIES_PREPARED scala=%s macro_paradise=%s quasiquotes=%s\n' \
  "$scala_version" \
  "$macro_paradise_version" \
  "$quasiquotes_version"
