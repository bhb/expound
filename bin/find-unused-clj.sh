#!/bin/bash
for f in $(egrep -o -R "defn?-?( \^\:private)? [^ ]*" * --include '*.cljc' --exclude-dir resources | sed 's/ \^\:private//g' | cut -d \  -f 2 | sort | uniq); do
  echo $f $(grep -R --include '*.cljc' --exclude-dir resources -- "$f" * | wc -l);
done | grep " 1$"
