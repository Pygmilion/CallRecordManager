#!/bin/bash

# 方案 2：命令行完全自动安装脚本（无需交互）
# 适用于 macOS

set -e

ANDROID_HOME="$HOME/Library/Android/sdk"
PROJECT_DIR="/Users/natsusakai/Documents/CallRecordManager"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "          方案 2：命令行自动安装和编译"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 步骤 1: 安装 Homebrew
echo "📦 步骤 1/6: 安装 Homebrew..."
if ! command -v brew &> /dev/null; then
    echo "正在安装 Homebrew..."
    NONINTERACTIVE=1 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    # 配置环境变量
    if [[ $(uname -m) == 'arm64' ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
        echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
    else
        eval "$(/usr/local/bin/brew shellenv)"
        echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zprofile
    fi
    echo "✅ Homebrew 安装完成"
else
    echo "✅ Homebrew 已安装"
fi

# 步骤 2: 安装 Java JDK 17
echo ""
echo "☕ 步骤 2/6: 安装 Java JDK 17..."
if ! command -v java &> /dev/null || [[ $(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1) -lt 17 ]]; then
    echo "正在安装 Java JDK 17..."
    brew install openjdk@17
    
    # 配置环境变量
    echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
    echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@17"' >> ~/.zshrc
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    
    sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk 2>/dev/null || true
    echo "✅ Java JDK 17 安装完成"
else
    echo "✅ Java JDK 已安装"
fi

# 步骤 3: 下载 Android 命令行工具
echo ""
echo "📲 步骤 3/6: 下载 Android 命令行工具..."
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "正在下载 Android 命令行工具..."
    curl -L -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    
    echo "正在解压..."
    unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm /tmp/cmdline-tools.zip
    echo "✅ Android 命令行工具安装完成"
else
    echo "✅ Android 命令行工具已安装"
fi

# 步骤 4: 配置环境变量
echo ""
echo "⚙️  步骤 4/6: 配置环境变量..."
if ! grep -q "ANDROID_HOME" ~/.zshrc 2>/dev/null; then
    cat >> ~/.zshrc << 'EOF'

# Android SDK
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export PATH="$PATH:$ANDROID_HOME/emulator"
EOF
fi

export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
echo "✅ 环境变量配置完成"

# 步骤 5: 安装 Android SDK
echo ""
echo "📦 步骤 5/6: 安装 Android SDK..."
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

echo "正在接受许可..."
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

echo "正在安装 SDK 组件..."
"$SDKMANAGER" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmdline-tools;latest"
echo "✅ Android SDK 安装完成"

# 步骤 6: 编译 APK
echo ""
echo "🔨 步骤 6/6: 编译 APK..."
cd "$PROJECT_DIR"

# 确保 local.properties 有 SDK 路径
if ! grep -q "sdk.dir" local.properties 2>/dev/null; then
    echo "sdk.dir=$ANDROID_HOME" >> local.properties
fi

echo "正在清理..."
./gradlew clean

echo "正在编译调试版 APK..."
./gradlew assembleDebug

# 检查结果
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 编译成功！"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📦 APK 文件位置:"
    echo "   $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    SIZE=$(du -h "app/build/outputs/apk/debug/app-debug.apk" | cut -f1)
    echo "📊 文件大小: $SIZE"
    echo ""
    echo "📱 安装到手机:"
    echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo "❌ 编译失败"
    exit 1
fi
