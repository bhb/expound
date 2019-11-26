#!/bin/bash

set -o nounset
set -euo pipefail
set -o errexit

mydir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename $0).XXXXXXXXXXXX")

lein install
pushd bin && clojure deps.clj > "$mydir/actual_deps.txt"
popd

diff -u <(cat ./test/expected_deps.txt | perl -pe 's/__\d+\#//g') \
        <(cat "$mydir/actual_deps.txt" | perl -pe 's/__\d+\#//g')
