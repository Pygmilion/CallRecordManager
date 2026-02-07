#!/bin/bash

# 命令行编译脚本
# 自动检测并使用可用的编译工具

set -e

PROJECT_DIR="/Users/natsusakai/Documents/CallRecordManager"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "          Android 应用命令行编译"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$PROJECT_DIR"

# 检查 Java
echo "🔍 检查 Java 环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    echo "✅ Java 已安装: 版本 $JAVA_VERSION"
    java -version
else
    echo "❌ 未找到 Java"
    echo ""
    echo "需要安装 Java JDK 17+"
    echo "请运行: brew install openjdk@17"
    exit 1
fi

echo ""

# 检查 Android SDK
echo "🔍 检查 Android SDK..."
if [ -d "$HOME/Library/Android/sdk" ]; then
    echo "✅ Android SDK 已安装"
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export PATH="$PATH:$ANDROID_HOME/platform-tools"
elif [ -d "$ANDROID_HOME" ]; then
    echo "✅ Android SDK 已安装: $ANDROID_HOME"
else
    echo "❌ 未找到 Android SDK"
    echo ""
    echo "Android SDK 路径应该在:"
    echo "  $HOME/Library/Android/sdk"
    echo ""
    echo "如果已安装 Android Studio，SDK 应该已经存在"
    echo "请在 local.properties 中配置 sdk.dir"
    exit 1
fi

echo ""

# 检查 gradlew
echo "🔍 检查 Gradle Wrapper..."
if [ -f "./gradlew" ]; then
    echo "✅ gradlew 已存在"
    chmod +x ./gradlew
else
    echo "❌ gradlew 不存在"
    exit 1
fi

echo ""

# 检查 local.properties
echo "🔍 检查配置文件..."
if [ -f "local.properties" ]; then
    echo "✅ local.properties 已存在"
    
    # 确保有 sdk.dir
    if ! grep -q "sdk.dir" local.properties; then
        echo "⚠️  添加 sdk.dir 到 local.properties"
        echo "sdk.dir=$ANDROID_HOME" >> local.properties
    fi
    
    # 检查 API Key
    if grep -q "STEPFUN_API_KEY=sk-" local.properties || grep -q "STEPFUN_API_KEY=1FYEJ" local.properties; then
        echo "✅ API Key 已配置"
    else
        echo "⚠️  API Key 未配置或格式错误"
    fi
else
    echo "❌ local.properties 不存在"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "开始编译..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 清理
echo "🧹 清理旧的构建文件..."
./gradlew clean

echo ""

# 编译
echo "🔨 编译调试版 APK..."
echo "⚠️  首次编译需要下载依赖，可能需要 10-15 分钟"
echo ""

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
    
    echo "📂 打开文件夹:"
    echo "   open app/build/outputs/apk/debug"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ 编译失败"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "请查看上方的错误信息"
    exit 1
fi
