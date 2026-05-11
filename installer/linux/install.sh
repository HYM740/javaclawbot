#!/bin/bash
# ============================================================
# NexusAI Linux 一键安装脚本
# 用法: bash install.sh
# 从内网服务器自动下载并安装所有运行时依赖
# ============================================================
set -e

APP_NAME="NexusAI"
APP_VERSION="2.3.0"
BASE_URL="http://101.68.93.109:9102/agent/releases/${APP_VERSION}"
INSTALL_DIR="$HOME/.local/share/NexusAI"
ARCH=$(uname -m)

# 架构映射
case "$ARCH" in
  aarch64|arm64) PLATFORM="linux-arm64" ;;
  x86_64)        PLATFORM="linux-x64" ;;
  *)
    echo "不支持的架构: $ARCH"
    exit 1
    ;;
esac

echo "========================================================"
echo "  NexusAI 一键安装脚本 (Linux)"
echo "  版本: ${APP_VERSION}"
echo "  架构: ${ARCH} -> ${PLATFORM}"
echo "  安装目录: ${INSTALL_DIR}"
echo "========================================================"
echo ""

# 确保基础工具可用
if ! command -v tar &>/dev/null; then
  echo "错误: 需要 tar 命令"
  exit 1
fi

mkdir -p "$INSTALL_DIR"

# ---- 1. JDK 17 运行时 ----
echo "[1/4] 检测 JDK 17 运行时..."
if command -v java &>/dev/null && java --version 2>&1 | grep -q "17"; then
  echo "  JDK 17 ✅ 已安装 (跳过)"
else
  echo "  下载 JDK 17 运行时..."
  curl -L --progress-bar -o "/tmp/jdk-17-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/jdk-17-${PLATFORM}.tar.gz"
  echo "  解压 JDK 17..."
  tar -xzf "/tmp/jdk-17-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  rm -f "/tmp/jdk-17-${PLATFORM}.tar.gz"
  echo "  JDK 17 ✅ 安装完成"
fi
echo ""

# ---- 2. Git ----
echo "[2/4] 检测 Git..."
if command -v git &>/dev/null; then
  echo "  Git ✅ 已安装 (跳过)"
else
  echo "  下载 Git..."
  curl -L --progress-bar -o "/tmp/git-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/git-${PLATFORM}.tar.gz"
  echo "  解压 Git..."
  tar -xzf "/tmp/git-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  rm -f "/tmp/git-${PLATFORM}.tar.gz"
  echo "  Git ✅ 安装完成"
fi
echo ""

# ---- 3. Python ----
echo "[3/4] 检测 Python..."
if command -v python3 &>/dev/null; then
  echo "  Python ✅ 已安装 (跳过)"
else
  echo "  下载 Python..."
  curl -L --progress-bar -o "/tmp/python-${PLATFORM}.tar.gz" \
    "${BASE_URL}/${PLATFORM}/python-${PLATFORM}.tar.gz"
  echo "  解压 Python..."
  tar -xzf "/tmp/python-${PLATFORM}.tar.gz" -C "$INSTALL_DIR/"
  rm -f "/tmp/python-${PLATFORM}.tar.gz"
  echo "  Python ✅ 安装完成"
fi
echo ""

# ---- 4. Node.js ----
echo "[4/4] 检测 Node.js..."
if command -v node &>/dev/null; then
  echo "  Node.js ✅ 已安装 (跳过)"
else
  echo "  下载 Node.js..."
  curl -L --progress-bar -o "/tmp/node-${PLATFORM}.tar.xz" \
    "${BASE_URL}/${PLATFORM}/node-${PLATFORM}.tar.xz"
  echo "  解压 Node.js..."
  tar -xJf "/tmp/node-${PLATFORM}.tar.xz" -C "$INSTALL_DIR/"
  rm -f "/tmp/node-${PLATFORM}.tar.xz"
  echo "  Node.js ✅ 安装完成"
fi
echo ""

# ---- 下载主程序 ----
echo "下载 NexusAI 主程序..."
curl -L --progress-bar -o "$INSTALL_DIR/NexusAI.jar" \
  "${BASE_URL}/NexusAI.jar"
echo ""

# ---- 配置 PATH ----
echo "配置环境变量..."
PROFILE_RC="$HOME/.profile"

if ! grep -q "# NexusAI Runtime PATH" "$PROFILE_RC" 2>/dev/null; then
  cat >> "$PROFILE_RC" << 'EOF'

# NexusAI Runtime PATH
export NEXUS_HOME="$HOME/.local/share/NexusAI"
export PATH="$NEXUS_HOME/java17/bin:$NEXUS_HOME/git/bin:$NEXUS_HOME/python/bin:$NEXUS_HOME/node/bin:$PATH"
EOF
  echo "  已追加 PATH 和 NEXUS_HOME 到 ${PROFILE_RC}"
else
  echo "  PATH 已配置 (跳过)"
fi

# ---- 创建 .desktop 快捷方式 ----
mkdir -p "$HOME/.local/share/applications"

cat > "$HOME/.local/share/applications/nexusai.desktop" << DESKTOPEOF
[Desktop Entry]
Name=NexusAI
Comment=NexusAI - AI Assistant
Exec=java -jar ${INSTALL_DIR}/NexusAI.jar
Icon=${INSTALL_DIR}/icon.png
Terminal=false
Type=Application
Categories=Development;
DESKTOPEOF

echo "  已创建桌面快捷方式"
echo ""

echo "========================================================"
echo "  NexusAI 安装完成!"
echo "  启动: java -jar ${INSTALL_DIR}/NexusAI.jar"
echo "  或从应用菜单中启动 NexusAI"
echo ""
echo "  ⚠ 请运行以下命令使 PATH 生效:"
echo "     source ${PROFILE_RC}"
echo "  或重新登录"
echo "========================================================"
