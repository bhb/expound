#!/bin/bash

set -euo pipefail

stdin=$(cat -)

# ignore expected warning
warnings=$(echo "$stdin" | grep -v "WARNING: Wrong number of args (1) passed to expound.alpha-test/test-instrument-adder")

if grep -q "WARNING" <(echo $warnings); then
    echo "$warnings" | grep "WARNING" 
    exit 1
else
    exit 0
fi
