#!/bin/bash
# Patch dlight.nativeexecution module to add cnd.lsp as a friend module.
# This is required because cnd.lsp uses ExecutionEnvironment API but is not
# in the original friends list of nativeexecution module in NetBeans.
#
# Usage: ./scripts/patch-nativeexecution.sh /path/to/netbeans
#
set -e

NB_HOME="${1:?Usage: $0 /path/to/netbeans}"
JAR="$NB_HOME/ide/modules/org-netbeans-modules-dlight-nativeexecution.jar"

if [ ! -f "$JAR" ]; then
    echo "ERROR: Cannot find $JAR"
    exit 1
fi

# Check if already patched
if unzip -p "$JAR" META-INF/MANIFEST.MF 2>/dev/null | grep -q "org.netbeans.modules.cnd.lsp"; then
    echo "Already patched — cnd.lsp is already a friend of nativeexecution."
    exit 0
fi

WORK=$(mktemp -d)
trap "rm -rf $WORK" EXIT

cd "$WORK"
jar xf "$JAR"

# Add org.netbeans.modules.cnd.lsp to the friends list
sed -i 's/OpenIDE-Module-Friends: /OpenIDE-Module-Friends: org.netbeans.modules.cnd.lsp, /' META-INF/MANIFEST.MF

# Repackage
jar cfm "$JAR" META-INF/MANIFEST.MF $(find . -not -path "./META-INF/MANIFEST.MF" -not -name "." -type f | sed 's|^\./||')

echo "Patched: added org.netbeans.modules.cnd.lsp to nativeexecution friends list."
