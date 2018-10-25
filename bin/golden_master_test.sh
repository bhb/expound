#!/bin/bash

set -euo pipefail

mydir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename $0).XXXXXXXXXXXX")

pushd bin && clojure sample.clj > "$mydir/output.txt"
popd

diff -u ./test/expected_sample_out.txt "$mydir/output.txt"
