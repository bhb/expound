#!/bin/bash

set -euo pipefail

stdin=$(cat -)

# ignore expected warning
warnings=$(echo "$stdin")

if grep -q "WARNING" <(echo $warnings); then
    echo "$warnings" | grep "WARNING" 
    exit 1
else
    exit 0
fi
