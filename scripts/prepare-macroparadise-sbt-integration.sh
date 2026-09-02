#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"

macro_paradise_repository="https://github.com/DmytroMitin/macroparadise-scala3.git"
macro_paradise_commit="b8b11f19bd9eb6d0302bf1efd8b6fecffcf5173f"

fail() {
  printf 'Macro-Paradise sbt integration preparation failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/prepare-macroparadise-sbt-integration.sh from the product root"

for command in git sbt java; do
  command -v "$command" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command"
done

preparation_root="$(mktemp -d "${TMPDIR:-/tmp}/auxify-macroparadise-sbt-integration.XXXXXX")"
trap 'rm -rf -- "$preparation_root"' EXIT

macro_paradise_checkout="$preparation_root/macroparadise-scala3"

git clone --filter=blob:none --no-checkout \
  "$macro_paradise_repository" \
  "$macro_paradise_checkout"
git -C "$macro_paradise_checkout" checkout --detach "$macro_paradise_commit"

actual_commit="$(git -C "$macro_paradise_checkout" rev-parse HEAD)"
[[ "$actual_commit" == "$macro_paradise_commit" ]] ||
  fail "checkout identity mismatch: expected $macro_paradise_commit, found $actual_commit"

(
  cd "$macro_paradise_checkout/sbt-integration"
  sbt -batch verifyIntegrationPolicy publishLocal
)

printf 'MACRO_PARADISE_SBT_INTEGRATION_COMMIT=%s\n' "$actual_commit"
printf '%s\n' 'AUXIFY_MACROPARADISE_SBT_INTEGRATION_PREPARED'
