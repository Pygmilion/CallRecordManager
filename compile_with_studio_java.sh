#!/bin/bash

# 使用 Android Studio 内置的 Java 和工具进行编译

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "          使用 Android Studio 的 Java 编译"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

PROJECT_DIR="/Users/natsusakai/Documents/CallRecordManager"
cd "$PROJECT_DIR"

# 查找 Android Studio 内置的 Java
echo "🔍 查找 Android Studio 内置的 Java..."

# 可能的 Java 路径
JAVA_PATHS=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
    "/Applications/Android Studio.app/Contents/jre/Contents/Home/bin/java"
    "/Applications/Android Studio.app/Contents/jre/jdk/Contents/Home/bin/java"
)

JAVA_HOME=""
for path in "${JAVA_PATHS[@]}"; do
    if [ -f "$path" ]; then
        JAVA_HOME=$(dirname $(dirname "$path"))
        echo "✅ 找到 Java: $JAVA_HOME"
        break
    fi
done

if [ -z "$JAVA_HOME" ]; then
    echo "❌ 未找到 Android Studio 内置的 Java"
    echo ""
    echo "请使用 Android Studio 图形界面编译："
    echo "1. 打开 Android Studio"
    echo "2. File → Open → 选择项目文件夹"
    echo "3. Build → Build APK(s)"
    echo ""
    exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo ""
echo "☕ Java 版本:"
"$JAVA_HOME/bin/java" -version

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "开始编译..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 确保 gradlew 可执行
chmod +x ./gradlew

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
    open app/build/outputs/apk/debug
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ 编译失败"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
fi
