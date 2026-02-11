#!/bin/bash

# GitHub Actions 快速推送脚本
# 使用方法: ./push_to_github.sh YOUR_GITHUB_USERNAME

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "          GitHub Actions 云端编译 - 快速推送脚本"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 检查参数
if [ -z "$1" ]; then
    echo "❌ 错误: 请提供你的 GitHub 用户名"
    echo ""
    echo "使用方法:"
    echo "  ./push_to_github.sh YOUR_GITHUB_USERNAME"
    echo ""
    echo "示例:"
    echo "  ./push_to_github.sh natsusakai"
    echo ""
    exit 1
fi

GITHUB_USERNAME=$1
REPO_NAME="CallRecordManager"

echo "📝 配置信息:"
echo "   GitHub 用户名: $GITHUB_USERNAME"
echo "   仓库名称: $REPO_NAME"
echo ""

# 进入项目目录
cd /Users/natsusakai/Documents/CallRecordManager

# 检查 Git 配置
echo "🔧 检查 Git 配置..."
if ! git config user.email > /dev/null 2>&1; then
    git config user.email "natsusakai@example.com"
    git config user.name "Natsu Sakai"
    echo "✅ Git 用户信息已配置"
fi

# 添加所有文件
echo ""
echo "📦 添加所有文件..."
git add .

# 提交
echo ""
echo "💾 提交代码..."
git commit -m "Initial commit: Complete Android app with all features" || echo "⚠️  没有新的更改需要提交"

# 检查是否已有远程仓库
if git remote get-url origin > /dev/null 2>&1; then
    echo ""
    echo "⚠️  远程仓库已存在，将使用现有配置"
else
    # 添加远程仓库
    echo ""
    echo "🔗 添加远程仓库..."
    git remote add origin "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git"
    echo "✅ 远程仓库已添加"
fi

# 推送到 GitHub
echo ""
echo "🚀 推送代码到 GitHub..."
echo ""
echo "⚠️  注意: 首次推送需要输入 GitHub 凭据"
echo "   用户名: 你的 GitHub 用户名"
echo "   密码: Personal Access Token (不是账号密码)"
echo ""
echo "如何获取 Personal Access Token:"
echo "   1. 访问: https://github.com/settings/tokens"
echo "   2. 点击 'Generate new token (classic)'"
echo "   3. 勾选 'repo' 权限"
echo "   4. 生成并复制 token"
echo "   5. 在下方提示时粘贴 token"
echo ""

git branch -M main
git push -u origin main

if [ $? -eq 0 ]; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 代码推送成功！"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📱 下一步:"
    echo ""
    echo "1. 配置 API Key Secret:"
    echo "   访问: https://github.com/$GITHUB_USERNAME/$REPO_NAME/settings/secrets/actions"
    echo "   点击 'New repository secret'"
    echo "   Name: STEPFUN_API_KEY"
    echo "   Secret: your_api_key_here"
    echo ""
    echo "   ⚠️  注意: v1.1.0 起 API Key 已改为在 App 设置页面中配置，无需编译时写入"
    echo ""
    echo "2. 查看编译进度:"
    echo "   访问: https://github.com/$GITHUB_USERNAME/$REPO_NAME/actions"
    echo ""
    echo "3. 下载 APK:"
    echo "   编译完成后，在 Actions 页面底部的 Artifacts 中下载"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ 推送失败"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "可能的原因:"
    echo "1. GitHub 用户名错误"
    echo "2. 仓库不存在（请先在 GitHub 创建仓库）"
    echo "3. 认证失败（需要 Personal Access Token）"
    echo "4. 网络问题"
    echo ""
    echo "解决方法:"
    echo "1. 确认已在 GitHub 创建名为 '$REPO_NAME' 的仓库"
    echo "2. 获取 Personal Access Token:"
    echo "   https://github.com/settings/tokens"
    echo "3. 重新运行此脚本"
    echo ""
fi
