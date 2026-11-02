#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${LEGACY_JAVA_HOME:-}" ]]; then
    task_java_home="$LEGACY_JAVA_HOME"
elif [[ -x /usr/libexec/java_home ]]; then
    task_java_home="$(/usr/libexec/java_home -v 1.8)"
    # Some macOS hosts list the old browser JRE before the full JDK.
    if [[ ! -f "$task_java_home/lib/tools.jar" ]]; then
        for task_tools in /Library/Java/JavaVirtualMachines/*/Contents/Home/lib/tools.jar; do
            task_candidate="${task_tools%/lib/tools.jar}"
            if [[ -f "$task_tools" ]] && "$task_candidate/bin/java" -version 2>&1 | grep -q 'version "1.8.'; then
                task_java_home="$task_candidate"
                break
            fi
        done
    fi
else
    task_java_home="${JAVA_HOME:?Set LEGACY_JAVA_HOME to a JDK 8 installation}"
fi
task_java_version="$("$task_java_home/bin/java" -version 2>&1)"
if [[ "$task_java_version" != *'version "1.8.'* || ! -f "$task_java_home/lib/tools.jar" ]]; then
    echo "Baseline requires JDK 8. Set LEGACY_JAVA_HOME to its installation directory." >&2
    exit 1
fi
cd "$task_root"
JAVA_HOME="$task_java_home" PATH="$task_java_home/bin:$PATH" \
    mvn -B -f baseline/pom.xml "$@" test
