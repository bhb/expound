#!/bin/bash

set -o xtrace
set -o nounset
set -uo pipefail

mydir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename $0).XXXXXXXXXXXX")

pushd bin && clojure sample.clj > "$mydir/output.txt"
popd

diff -u <(cat ./test/expected_sample_out.txt | perl -pe 's/__\d+\#//g') \
        <(cat "$mydir/output.txt" | perl -pe 's/__\d+\#//g')
