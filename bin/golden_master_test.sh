#!/bin/bash

set -o errexit
set -euo pipefail

mydir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename $0).XXXXXXXXXXXX")

pushd bin && clojure sample.clj > "$mydir/output.txt"
popd

diff -u <(cat ./test/expected_sample_out.txt | perl -pe 's/__\d+\#//g') \
        <(cat "$mydir/output.txt" | perl -pe 's/__\d+\#//g')
