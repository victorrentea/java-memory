#!/bin/bash
# Launch Eclipse MAT as an MCP server.
# MCP protocol over stdin/stdout, diagnostics to stderr.
#
# Prerequisites: run 'mvn clean verify' once to build and prepare the work directory.
# After that, this script can be used directly without Maven.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$SCRIPT_DIR/target/work"
MAT_PLUGINS="/Applications/MemoryAnalyzer.app/Contents/Eclipse/plugins"
LAUNCHER="$MAT_PLUGINS/org.eclipse.equinox.launcher_1.6.600.v20231106-1826.jar"
JAVA_HOME="${JAVA_HOME:-/Users/victorrentea/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home}"

if [ ! -d "$WORK_DIR/configuration" ]; then
    echo "[mat-mcp] Work directory not found. Run 'cd $SCRIPT_DIR && mvn clean verify' first." >&2
    exit 1
fi

SUREFIRE_PROPS="$SCRIPT_DIR/target/surefire.properties"
if [ ! -f "$SUREFIRE_PROPS" ]; then
    echo "[mat-mcp] surefire.properties not found. Run 'cd $SCRIPT_DIR && mvn verify -Pmcp' once first." >&2
    exit 1
fi

# Pipe through grep to only pass JSON lines (MCP protocol), non-JSON goes to stderr
"$JAVA_HOME/bin/java" \
    -Dosgi.noShutdown=false \
    -Dosgi.os=macosx \
    -Dosgi.ws=cocoa \
    -Dosgi.arch=aarch64 \
    -XstartOnFirstThread \
    -Xmx2048m \
    -Dosgi.clean=true \
    -jar "$LAUNCHER" \
    -data "$WORK_DIR/data" \
    -install "$WORK_DIR" \
    -configuration "$WORK_DIR/configuration" \
    -application org.eclipse.tycho.surefire.osgibooter.uitest \
    -testproperties "$SUREFIRE_PROPS" \
    -testApplication org.eclipse.mat.ui.rcp.application \
    -product org.eclipse.mat.ui.rcp.MemoryAnalyzer \
    -nouithread \
    -timeout 86400000 \
    2>&2 | while IFS= read -r line; do
        case "$line" in
            '{"jsonrpc"'*) echo "$line" ;;
            *) echo "$line" >&2 ;;
        esac
    done
