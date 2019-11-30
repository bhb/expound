#!/bin/bash

set -o xtrace
set -o nounset
set -euo pipefail

pushd bin && clojure comparison.clj > ../doc/comparison.md
popd
