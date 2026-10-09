#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"
source "$script_dir/dev-dependency-config.sh"

fail() {
  printf 'developer bootstrap test failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/test-bootstrap-dev.sh from the product root"

test_root="$(mktemp -d "${TMPDIR:-/tmp}/auxify-bootstrap-test.XXXXXX")"
trap 'rm -rf -- "$test_root"' EXIT

invalid_log="$test_root/invalid.log"
if AUXIFY_SCALA_VERSION=3.7.0 AUXIFY_DEV_IVY_HOME="$test_root/invalid-ivy" "$script_dir/bootstrap-dev.sh" >"$invalid_log" 2>&1; then
  fail "unsupported Scala line unexpectedly succeeded"
fi
grep -Fq 'unsupported exact Scala version: 3.7.0; expected 3.3.8, 3.8.4, or 3.9.0' "$invalid_log" ||
  fail "unsupported Scala line did not report the controlled diagnostic"

ivy_home="$test_root/ivy"
first_log="$test_root/first.log"
AUXIFY_SCALA_VERSION=3.8.4 AUXIFY_DEV_IVY_HOME="$ivy_home" "$script_dir/bootstrap-dev.sh" | tee "$first_log"

grep -Fq 'AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_PREPARED scala=3.8.4' "$first_log" ||
  fail "clean bootstrap did not report prepared state"
grep -Fq "macro_paradise=$AUXIFY_MACRO_PARADISE_COMMIT" "$first_log" ||
  fail "clean bootstrap did not report the exact Macro-Paradise source"
grep -Fq "quasiquotes=$AUXIFY_QUASIQUOTES_COMMIT" "$first_log" ||
  fail "clean bootstrap did not report the exact Quasiquotes source"

while IFS='|' read -r module version; do
  coordinate_root="$ivy_home/local/com.github.dmytromitin/$module/$version"
  [[ -f "$coordinate_root/ivys/ivy.xml" ]] ||
    fail "missing Ivy metadata for $module"
  [[ -f "$coordinate_root/jars/$module.jar" ]] ||
    fail "missing binary artifact for $module"
done < <(auxify_required_dependency_coordinates 3.8.4)

probe_module="quasiquotes-scala3-dotty-internal_3.8.4"
probe_jar="$ivy_home/local/com.github.dmytromitin/$probe_module/$AUXIFY_QUASIQUOTES_VERSION/jars/$probe_module.jar"
expected_probe_sha="$(sha256sum "$probe_jar" | awk '{ print $1 }')"

second_log="$test_root/second.log"
AUXIFY_SCALA_VERSION=3.8.4 AUXIFY_DEV_IVY_HOME="$ivy_home" "$script_dir/bootstrap-dev.sh" | tee "$second_log"
grep -Fq 'AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_ALREADY_PREPARED scala=3.8.4' "$second_log" ||
  fail "matching rerun did not take the idempotent fast path"

printf 'stale-snapshot' >>"$probe_jar"
[[ "$(sha256sum "$probe_jar" | awk '{ print $1 }')" != "$expected_probe_sha" ]] ||
  fail "stale-artifact fixture did not change the probe digest"

repair_log="$test_root/repair.log"
AUXIFY_SCALA_VERSION=3.8.4 AUXIFY_DEV_IVY_HOME="$ivy_home" "$script_dir/bootstrap-dev.sh" | tee "$repair_log"
grep -Fq 'AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_PREPARED scala=3.8.4' "$repair_log" ||
  fail "stale snapshot was not re-prepared"
[[ "$(sha256sum "$probe_jar" | awk '{ print $1 }')" == "$expected_probe_sha" ]] ||
  fail "stale snapshot was not restored to the accepted digest"

printf '%s\n' 'AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_TEST_PASS'
