#!/bin/bash

set -o xtrace
set -o nounset
set -o errexit
set -uo pipefail

mydir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename $0).XXXXXXXXXXXX")

lein install
pushd bin && clojure deps.clj > "$mydir/actual_deps.txt"
popd

diff -u <(cat ./test/expected_deps.txt | perl -pe 's/__\d+\#//g') \
        <(cat "$mydir/actual_deps.txt" | perl -pe 's/__\d+\#//g')
