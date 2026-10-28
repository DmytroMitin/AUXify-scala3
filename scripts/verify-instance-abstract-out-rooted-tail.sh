#!/usr/bin/env bash
# Emit the feature marker only after the complete canonical qualification.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"
[[ "$(pwd -P)" == "$product_root" ]] || {
  printf 'run from the product root\n' >&2
  exit 1
}
log_root="$product_root/target/instance-abstract-out-rooted-tail"
mkdir -p "$log_root"
for scala_line in 3.3.8 3.8.4 3.9.0; do
  AUXIFY_SCALA_VERSION="$scala_line" "$script_dir/prepare-ci-dependencies.sh" \
    | tee "$log_root/prepare-$scala_line.log"
  AUXIFY_SCALA_VERSION="$scala_line" "$script_dir/verify-milestone1.sh" \
    | tee "$log_root/verify-$scala_line.log"
done
"$script_dir/test-bootstrap-dev.sh" | tee "$log_root/bootstrap.log"
printf '%s\n' 'AUXIFY_SCALA3_INSTANCE_ABSTRACT_OUT_ROOTED_TAIL_PASS'
