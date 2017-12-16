#!/bin/bash
for f in $(egrep -o -R "defn?-? [^ ]*" * --include '*.cljc' --exclude-dir resources | cut -d \  -f 2 | sort | uniq); do
  echo $f $(grep -R --include '*.cljc' --exclude-dir resources -- "$f" * | wc -l);
done | grep " 1$"
