#!/usr/bin/env bash
# Qualify the public consumer through the complete canonical matrix.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
product_root="$(cd "$script_dir/.." && pwd -P)"
[[ "$(pwd -P)" == "$product_root" ]] || {
  printf 'run from the product root\n' >&2
  exit 1
}
log_root="$product_root/target/real-consumer-coherence"
mkdir -p "$log_root"
for scala_line in 3.3.8 3.8.4 3.9.0; do
  AUXIFY_SCALA_VERSION="$scala_line" "$script_dir/prepare-ci-dependencies.sh" \
    | tee "$log_root/prepare-$scala_line.log"
  AUXIFY_SCALA_VERSION="$scala_line" "$script_dir/verify-milestone1.sh" \
    | tee "$log_root/verify-$scala_line.log"
  grep -Fxq 'AUXIFY_SCALA3_REAL_CONSUMER_RUNTIME_PASS total=42 shown=total=42 emptyEvaluations=3 strictArguments=2 carrierEvaluations=1' \
    "$log_root/verify-$scala_line.log"
  grep -Fq "AUXIFY_SCALA3_APPLY_SHOW_MILESTONE1_PASS scala=$scala_line jdk=25" \
    "$log_root/verify-$scala_line.log"
done
"$script_dir/test-bootstrap-dev.sh" | tee "$log_root/bootstrap.log"
printf '%s\n' 'AUXIFY_SCALA3_INSTANCE_ABSTRACT_OUT_ROOTED_TAIL_PASS'
printf '%s\n' 'AUXIFY_SCALA3_REAL_CONSUMER_COHERENCE_PASS'
