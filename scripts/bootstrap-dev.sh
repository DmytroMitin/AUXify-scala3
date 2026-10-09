#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"

source "$script_dir/dev-dependency-config.sh"

fail() {
  printf 'developer bootstrap failed: %s\n' "$1" >&2
  exit 1
}

[[ "$(pwd -P)" == "$product_root" ]] ||
  fail "run scripts/bootstrap-dev.sh from the product root"

scala_version="${AUXIFY_SCALA_VERSION:-$AUXIFY_DEFAULT_SCALA_VERSION}"
auxify_validate_scala_version "$scala_version" || exit 1

dev_ivy_home="${AUXIFY_DEV_IVY_HOME:-${HOME:?HOME must name the contributor home}/.ivy2}"
[[ "$dev_ivy_home" == /* ]] ||
  fail "AUXIFY_DEV_IVY_HOME must be an absolute path"

for command in cmp cp git java mkdir mv sbt sha256sum; do
  command -v "$command" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command"
done

java_properties="$(java -XshowSettings:properties -version 2>&1)" ||
  fail "java runtime could not be inspected"
java_feature="$({
  printf '%s\n' "$java_properties" |
    awk -F '= ' '/^[[:space:]]*java\.specification\.version = / { print $2; exit }'
} || true)"
[[ "$java_feature" == "25" ]] ||
  fail "Java feature version 25 is required; found ${java_feature:-unknown}"

dependency_state_root="$(auxify_dependency_state_root "$product_root" "$scala_version")"
source_ivy_home="$dependency_state_root/ivy"
source_provenance_dir="$dependency_state_root/provenance"
source_metadata="$source_provenance_dir/dependencies.env"
source_checksums="$source_provenance_dir/dependencies.sha256"

stamp_dir="$dev_ivy_home/.auxify-scala3-bootstrap"
local_metadata="$stamp_dir/$scala_version.env"
local_checksums="$stamp_dir/$scala_version.sha256"

metadata_matches() {
  local metadata_file="$1"
  [[ -f "$metadata_file" ]] &&
    cmp -s <(auxify_dependency_provenance "$scala_version") "$metadata_file"
}

checksums_match() {
  local ivy_home="$1"
  local checksum_file="$2"
  [[ -f "$checksum_file" ]] &&
    (cd "$ivy_home" && sha256sum --check --status "$checksum_file")
}

local_state_matches() {
  metadata_matches "$local_metadata" &&
    checksums_match "$dev_ivy_home" "$local_checksums"
}

source_state_matches() {
  metadata_matches "$source_metadata" &&
    checksums_match "$source_ivy_home" "$source_checksums"
}

report_state() {
  local marker="$1"
  printf '%s scala=%s ivy_home=%s macro_paradise=%s quasiquotes=%s\n' "$marker" "$scala_version" "$dev_ivy_home" "$AUXIFY_MACRO_PARADISE_COMMIT" "$AUXIFY_QUASIQUOTES_COMMIT"
}

if local_state_matches; then
  report_state "AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_ALREADY_PREPARED"
  exit 0
fi

if ! source_state_matches; then
  printf '%s\n' "Preparing exact source-built dependencies in the SHA-keyed isolated repository."
  AUXIFY_SCALA_VERSION="$scala_version" "$script_dir/prepare-ci-dependencies.sh"
fi

source_state_matches ||
  fail "canonical preparation did not produce valid exact dependency provenance"

mkdir -p "$dev_ivy_home/local/com.github.dmytromitin" "$stamp_dir"

while IFS='|' read -r module version; do
  source_coordinate="$source_ivy_home/local/com.github.dmytromitin/$module/$version"
  destination_coordinate="$dev_ivy_home/local/com.github.dmytromitin/$module/$version"
  [[ -d "$source_coordinate" ]] ||
    fail "prepared source coordinate is missing: $module:$version"
  mkdir -p "$destination_coordinate"
  cp -a "$source_coordinate/." "$destination_coordinate/"
done < <(auxify_required_dependency_coordinates "$scala_version")

metadata_tmp="$(mktemp "$stamp_dir/$scala_version.env.XXXXXX")"
checksums_tmp="$(mktemp "$stamp_dir/$scala_version.sha256.XXXXXX")"
auxify_dependency_provenance "$scala_version" >"$metadata_tmp"
cp "$source_checksums" "$checksums_tmp"

checksums_match "$dev_ivy_home" "$checksums_tmp" ||
  fail "copied local dependency graph failed checksum validation"

mv "$checksums_tmp" "$local_checksums"
mv "$metadata_tmp" "$local_metadata"

local_state_matches ||
  fail "normal local Ivy repository failed final provenance validation"

report_state "AUXIFY_SCALA3_CONTRIBUTOR_BOOTSTRAP_PREPARED"
