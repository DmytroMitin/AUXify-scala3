#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"

macro_paradise_repository="https://github.com/DmytroMitin/macroparadise-scala3.git"
macro_paradise_commit="4f07eb6cb738455e19c5a9551e99a4f4e5010a6b"
quasiquotes_repository="https://github.com/DmytroMitin/quasiquotes-scala3.git"
quasiquotes_commit="c4345b50e004f83a9c09a5732c33837a35422a4f"
scala_version="${AUXIFY_SCALA_VERSION:-3.8.4}"

fail() {
  printf 'CI dependency preparation failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/prepare-ci-dependencies.sh from the product root"

case "$scala_version" in
  3.3.8|3.8.4) ;;
  *) fail "unsupported exact Scala version: $scala_version; expected 3.3.8 or 3.8.4" ;;
esac

printf 'AUXIFY_SCALA_VERSION=%s\n' "$scala_version"
printf 'MACRO_PARADISE_EXPECTED_COMMIT=%s\n' "$macro_paradise_commit"
printf 'QUASIQUOTES_EXPECTED_COMMIT=%s\n' "$quasiquotes_commit"

for command in git sbt java; do
  command -v "$command" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command"
done

dependency_root="$(mktemp -d "${TMPDIR:-/tmp}/auxify-ci-dependencies.XXXXXX")"
trap 'rm -rf -- "$dependency_root"' EXIT

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

clone_at_commit \
  "$quasiquotes_repository" \
  "$quasiquotes_commit" \
  "$quasiquotes_checkout" \
  QUASIQUOTES

(
  cd "$macro_paradise_checkout"
  sbt \
    -Dmacroparadise.exactScalaVersion="$scala_version" \
    -batch \
    "++$scala_version!" \
    "pluginApi/publishLocal" \
    "plugin/publishLocal"
)

(
  cd "$quasiquotes_checkout"
  sbt -batch \
    "++$scala_version!" \
    "core/publishLocal" \
    "set neutralScalameta / publish / skip := false" \
    "neutralScalameta/publishLocal" \
    "set dottyInternal / publish / skip := false" \
    "dottyInternal/publishLocal"
)

AUXIFY_SCALA_VERSION="$scala_version" \
  "$script_dir/prepare-macroparadise-sbt-integration.sh"

printf 'AUXIFY_SCALA3_CI_DEPENDENCIES_PREPARED scala=%s macro_paradise=%s quasiquotes=%s\n' \
  "$scala_version" \
  "$macro_paradise_commit" \
  "$quasiquotes_commit"
