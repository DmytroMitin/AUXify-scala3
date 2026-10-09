#!/usr/bin/env bash

readonly AUXIFY_MACRO_PARADISE_REPOSITORY="https://github.com/DmytroMitin/macroparadise-scala3.git"
readonly AUXIFY_MACRO_PARADISE_COMMIT="aae704ca42ff01ee44e663fb024c726a357716c7"
readonly AUXIFY_MACRO_PARADISE_VERSION="0.2.0-SNAPSHOT"
readonly AUXIFY_QUASIQUOTES_REPOSITORY="https://github.com/DmytroMitin/quasiquotes-scala3.git"
readonly AUXIFY_QUASIQUOTES_COMMIT="e5ee36156fa0ed75e5aa04de42c9eacb6db656fb"
readonly AUXIFY_QUASIQUOTES_VERSION="0.4.0-SNAPSHOT"
readonly AUXIFY_QUASIQUOTES_BINARY_SCALA_VERSION="3.3.8"
readonly AUXIFY_DEFAULT_SCALA_VERSION="3.8.4"

auxify_validate_scala_version() {
  local scala_version="$1"
  case "$scala_version" in
    3.3.8|3.8.4|3.9.0) return 0 ;;
    *)
      printf 'unsupported exact Scala version: %s; expected 3.3.8, 3.8.4, or 3.9.0\n' "$scala_version" >&2
      return 1
      ;;
  esac
}

auxify_dependency_state_root() {
  local product_root="$1"
  local scala_version="$2"
  printf '%s/target/ci-dependencies/%s-%s-%s\n' "$product_root" "$scala_version" "$AUXIFY_MACRO_PARADISE_COMMIT" "$AUXIFY_QUASIQUOTES_COMMIT"
}

auxify_required_dependency_coordinates() {
  local scala_version="$1"
  printf '%s|%s\n' "macroparadise-scala3-plugin-api_$scala_version" "$AUXIFY_MACRO_PARADISE_VERSION"
  printf '%s|%s\n' "macroparadise-scala3-plugin_$scala_version" "$AUXIFY_MACRO_PARADISE_VERSION"
  printf '%s|%s\n' "quasiquotes-scala3-core_3" "$AUXIFY_QUASIQUOTES_VERSION"
  printf '%s|%s\n' "quasiquotes-scala3-neutral-scalameta_3" "$AUXIFY_QUASIQUOTES_VERSION"
  printf '%s|%s\n' "quasiquotes-scala3-dotty-internal_$scala_version" "$AUXIFY_QUASIQUOTES_VERSION"
}

auxify_required_dependency_artifacts() {
  local scala_version="$1"
  local module
  local version
  while IFS='|' read -r module version; do
    printf 'local/com.github.dmytromitin/%s/%s/ivys/ivy.xml\n' "$module" "$version"
    printf 'local/com.github.dmytromitin/%s/%s/jars/%s.jar\n' "$module" "$version" "$module"
  done < <(auxify_required_dependency_coordinates "$scala_version")
}

auxify_dependency_provenance() {
  local scala_version="$1"
  printf 'format=1\n'
  printf 'scala_version=%s\n' "$scala_version"
  printf 'macro_paradise_repository=%s\n' "$AUXIFY_MACRO_PARADISE_REPOSITORY"
  printf 'macro_paradise_commit=%s\n' "$AUXIFY_MACRO_PARADISE_COMMIT"
  printf 'macro_paradise_version=%s\n' "$AUXIFY_MACRO_PARADISE_VERSION"
  printf 'quasiquotes_repository=%s\n' "$AUXIFY_QUASIQUOTES_REPOSITORY"
  printf 'quasiquotes_commit=%s\n' "$AUXIFY_QUASIQUOTES_COMMIT"
  printf 'quasiquotes_version=%s\n' "$AUXIFY_QUASIQUOTES_VERSION"
  printf 'quasiquotes_binary_scala_version=%s\n' "$AUXIFY_QUASIQUOTES_BINARY_SCALA_VERSION"
}
