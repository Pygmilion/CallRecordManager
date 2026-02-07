#!/bin/bash

# 快速编译脚本（假设环境已配置）
# 使用方法: ./quick_build.sh

set -e

echo "🚀 开始快速编译..."
echo ""

# 检查是否在项目根目录
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

# 检查 API Key
if ! grep -q "STEPFUN_API_KEY=sk-" local.properties 2>/dev/null; then
    echo "⚠️  警告: 未找到有效的 API Key"
    echo "请在 local.properties 中配置 STEPFUN_API_KEY"
    exit 1
fi

# 清理
echo "🧹 清理旧的构建文件..."
./gradlew clean

# 编译
echo "🔨 编译调试版 APK..."
./gradlew assembleDebug

# 检查结果
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 编译成功！"
    echo ""
    echo "📦 APK 位置: app/build/outputs/apk/debug/app-debug.apk"
    
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        SIZE=$(du -h "app/build/outputs/apk/debug/app-debug.apk" | cut -f1)
        echo "📊 文件大小: $SIZE"
    fi
    
    echo ""
    echo "📱 安装到手机:"
    echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo ""
    echo "❌ 编译失败"
    exit 1
fi
