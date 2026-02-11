#!/bin/bash

# Android 应用编译脚本
# 使用方法: ./build.sh

echo "🚀 开始编译 Android 应用..."

# 检查是否在项目根目录
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

# 检查 API Key 是否配置
if ! grep -q "STEPFUN_API_KEY" local.properties 2>/dev/null; then
    echo "⚠️  提示: local.properties 中未配置 STEPFUN_API_KEY"
    echo "⚠️  v1.1.0 起 API Key 已改为在 App 设置页面中配置，可以跳过此步骤"
fi

# 清理旧的构建文件
echo "🧹 清理旧的构建文件..."
./gradlew clean

# 编译调试版 APK
echo "🔨 编译调试版 APK..."
./gradlew assembleDebug

# 检查编译结果
if [ $? -eq 0 ]; then
    echo "✅ 编译成功!"
    echo ""
    echo "📦 APK 文件位置:"
    echo "   app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📱 安装方法:"
    echo "   1. 将 APK 文件传输到手机"
    echo "   2. 在手机上点击安装"
    echo "   或使用命令: adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ 编译失败，请查看错误信息"
    exit 1
fi
