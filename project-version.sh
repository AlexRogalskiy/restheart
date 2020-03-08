#!/bin/bash
set -e

echo "$PWD"
MVN_VERSION=$(mvn --quiet \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec 2>/dev/null)
export MVN_VERSION
echo "$MVN_VERSION"
