#!/bin/bash
# ============================================================
# TokenRing - Script chạy và test local / demo LAN
# Sử dụng: ./run.sh [compile|jar|A|B|C|all|stop] [--delay ms]
# ============================================================

JAVA=/home/dngnguyen/Documents/oracleJdk-21/bin/java
JAVAC=/home/dngnguyen/Documents/oracleJdk-21/bin/javac
JAVA_HOME=/home/dngnguyen/Documents/oracleJdk-21
GSON=/home/dngnguyen/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
SRC_DIR=src/main/java
OUT_DIR=out
CP="$OUT_DIR:$GSON"
CONFIG=nodes.config

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

compile() {
    echo -e "${YELLOW}>>> Compiling...${NC}"
    rm -rf "$OUT_DIR" && mkdir -p "$OUT_DIR"
    SOURCES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')
    $JAVAC -cp "$GSON" -d "$OUT_DIR" $SOURCES 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}>>> Compile OK!${NC}"
    else
        echo -e "${RED}>>> Compile FAILED!${NC}"
        exit 1
    fi
}

build_jar() {
    compile
    echo -e "${YELLOW}>>> Building fat JAR (Gson bundled)...${NC}"
    mkdir -p "$OUT_DIR"
    cd "$OUT_DIR" && $JAVA_HOME/bin/jar xf "$GSON" 2>/dev/null; cd ..
    $JAVA_HOME/bin/jar cfe TokenRing.jar tokenRing.Main -C "$OUT_DIR" .
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}>>> Built: TokenRing.jar ($(du -sh TokenRing.jar | cut -f1))${NC}"
        echo ""
        echo "Copy TokenRing.jar + nodes.config sang các máy khác, rồi chạy:"
        echo "  java -jar TokenRing.jar A nodes.config --delay 5000"
        echo "  java -jar TokenRing.jar B nodes.config --delay 5000"
        echo "  java -jar TokenRing.jar C nodes.config --delay 5000"
    else
        echo -e "${RED}>>> Build FAILED!${NC}"
    fi
}

run_node() {
    NODE=$1; shift
    EXTRA="$@"
    echo -e "${BLUE}>>> Starting Node $NODE... $EXTRA${NC}"
    $JAVA -cp "$CP" tokenRing.Main "$NODE" "$CONFIG" $EXTRA
}

stop_all() {
    echo -e "${RED}>>> Stopping all nodes...${NC}"
    pkill -f "tokenRing.Main" 2>/dev/null
    echo "Done."
}

run_all_background() {
    echo -e "${YELLOW}>>> Starting B, C in background, A in foreground...${NC}"
    $JAVA -cp "$CP" tokenRing.Main B "$CONFIG" --delay 3000 > /tmp/nodeB.log 2>&1 &
    echo "Node B started (PID $!, log: /tmp/nodeB.log)"
    $JAVA -cp "$CP" tokenRing.Main C "$CONFIG" --delay 3000 > /tmp/nodeC.log 2>&1 &
    echo "Node C started (PID $!, log: /tmp/nodeC.log)"
    sleep 1
    echo -e "${GREEN}>>> Node A (foreground, Ctrl+C để dừng):${NC}"
    $JAVA -cp "$CP" tokenRing.Main A "$CONFIG" --delay 3000
    stop_all
}

case "$1" in
    compile)
        compile
        ;;
    jar)
        build_jar
        ;;
    A|B|C)
        NODE=$1; shift
        run_node "$NODE" "$@"
        ;;
    all)
        compile && run_all_background
        ;;
    stop)
        stop_all
        ;;
    *)
        echo "Usage: ./run.sh [compile|jar|A|B|C|all|stop] [--delay ms]"
        echo ""
        echo "  compile       - Biên dịch project"
        echo "  jar           - Build fat JAR để copy sang máy khác (LAN demo)"
        echo "  A/B/C         - Chạy một node cụ thể (foreground)"
        echo "  all           - Compile + chạy cả 3 node (B/C ngầm, A hiển thị)"
        echo "  stop          - Dừng tất cả node đang chạy"
        echo ""
        echo "  --delay <ms>  - Thời gian giữ token (ms), mặc định 500ms"
        echo ""
        echo "--- Test local (3 terminal) ---"
        echo "  ./run.sh compile"
        echo "  Terminal 1: ./run.sh B --delay 5000"
        echo "  Terminal 2: ./run.sh C --delay 5000"
        echo "  Terminal 3: ./run.sh A --delay 5000"
        echo ""
        echo "--- Demo LAN ---"
        echo "  ./run.sh jar"
        echo "  # Sửa nodes.config với IP thật của từng máy"
        echo "  # Copy TokenRing.jar + nodes.config sang mỗi máy"
        echo "  # Máy A: java -jar TokenRing.jar A nodes.config --delay 5000"
        echo "  # Máy B: java -jar TokenRing.jar B nodes.config --delay 5000"
        echo "  # Máy C: java -jar TokenRing.jar C nodes.config --delay 5000"
        ;;
esac
