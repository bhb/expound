#!/bin/bash

set -euo pipefail

stdin=$(cat -)

# ignore expected warning
warnings=$(echo "$stdin")

if grep -q -i "warning" <(echo $warnings); then
    echo "$warnings" | grep -i "warning"
    exit 1
else
    exit 0
fi
