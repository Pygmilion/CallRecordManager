#!/bin/bash

# Android 开发环境自动安装和编译脚本
# 适用于 macOS
# 作者：小跃
# 日期：2026-02-06

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_step() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

# 检查命令是否存在
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 检查是否在项目根目录
check_project_dir() {
    if [ ! -f "settings.gradle.kts" ]; then
        print_error "错误: 请在项目根目录运行此脚本"
        print_info "当前目录: $(pwd)"
        print_info "请执行: cd /Users/natsusakai/Documents/CallRecordManager"
        exit 1
    fi
}

# 安装 Homebrew
install_homebrew() {
    print_step "步骤 1/6: 检查并安装 Homebrew"
    
    if command_exists brew; then
        print_success "Homebrew 已安装"
        brew --version
    else
        print_info "正在安装 Homebrew..."
        print_warning "这可能需要几分钟，请耐心等待..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        
        # 配置环境变量
        if [[ $(uname -m) == 'arm64' ]]; then
            # Apple Silicon
            echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
            eval "$(/opt/homebrew/bin/brew shellenv)"
        else
            # Intel
            echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zprofile
            eval "$(/usr/local/bin/brew shellenv)"
        fi
        
        print_success "Homebrew 安装完成"
    fi
}

# 安装 Java JDK
install_java() {
    print_step "步骤 2/6: 检查并安装 Java JDK 17"
    
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 17 ]; then
            print_success "Java JDK 已安装 (版本 $JAVA_VERSION)"
            java -version
            return
        else
            print_warning "Java 版本过低 (当前: $JAVA_VERSION, 需要: 17+)"
        fi
    fi
    
    print_info "正在安装 Java JDK 17..."
    brew install openjdk@17
    
    # 配置环境变量
    echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
    echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@17"' >> ~/.zshrc
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    
    # 创建符号链接
    sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
    
    print_success "Java JDK 17 安装完成"
    java -version
}

# 下载并安装 Android 命令行工具
install_android_cmdline_tools() {
    print_step "步骤 3/6: 下载并安装 Android 命令行工具"
    
    ANDROID_HOME="$HOME/Library/Android/sdk"
    CMDLINE_TOOLS_DIR="$ANDROID_HOME/cmdline-tools"
    
    if [ -d "$CMDLINE_TOOLS_DIR/latest" ]; then
        print_success "Android 命令行工具已安装"
        return
    fi
    
    print_info "正在下载 Android 命令行工具..."
    print_warning "下载大小约 150 MB，请耐心等待..."
    
    # 创建目录
    mkdir -p "$CMDLINE_TOOLS_DIR"
    
    # 下载命令行工具
    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    DOWNLOAD_FILE="/tmp/cmdline-tools.zip"
    
    curl -L -o "$DOWNLOAD_FILE" "$CMDLINE_TOOLS_URL"
    
    print_info "正在解压..."
    unzip -q "$DOWNLOAD_FILE" -d "$CMDLINE_TOOLS_DIR"
    mv "$CMDLINE_TOOLS_DIR/cmdline-tools" "$CMDLINE_TOOLS_DIR/latest"
    rm "$DOWNLOAD_FILE"
    
    print_success "Android 命令行工具安装完成"
}

# 配置 Android SDK
setup_android_sdk() {
    print_step "步骤 4/6: 配置 Android SDK"
    
    ANDROID_HOME="$HOME/Library/Android/sdk"
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
    
    # 配置环境变量
    if ! grep -q "ANDROID_HOME" ~/.zshrc; then
        echo "" >> ~/.zshrc
        echo "# Android SDK" >> ~/.zshrc
        echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> ~/.zshrc
        echo "export PATH=\"\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin\"" >> ~/.zshrc
        echo "export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools\"" >> ~/.zshrc
        echo "export PATH=\"\$PATH:\$ANDROID_HOME/emulator\"" >> ~/.zshrc
    fi
    
    export ANDROID_HOME="$ANDROID_HOME"
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
    export PATH="$PATH:$ANDROID_HOME/platform-tools"
    
    print_info "正在安装必需的 SDK 组件..."
    print_warning "这可能需要几分钟，请耐心等待..."
    
    # 接受许可
    yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
    
    # 安装必需组件
    "$SDKMANAGER" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmdline-tools;latest"
    
    print_success "Android SDK 配置完成"
}

# 检查 API Key
check_api_key() {
    print_step "步骤 5/6: 检查 API Key 配置"
    
    if [ ! -f "local.properties" ]; then
        print_error "未找到 local.properties 文件"
        print_info "正在创建 local.properties..."
        cat > local.properties << EOF
# Android SDK 路径
sdk.dir=$HOME/Library/Android/sdk

# 阶跃星辰 API Key
# 请在下方填写你的 API Key
STEPFUN_API_KEY=
"
EOF
        print_warning "v1.1.0 起 API Key 已改为在 App 设置页面中配置"
        print_info "获取 API Key: https://platform.stepfun.com/"        exit 1
    fi
    
    if ! grep -q "STEPFUN_API_KEY" local.properties; then
        print_warning "v1.1.0 起 API Key 已改为在 App 设置页面中配置，可以跳过此步骤"
        print_info "获取 API Key: https://platform.stepfun.com/"
    fi
    
    # 添加 SDK 路径（如果没有）
    if ! grep -q "sdk.dir" local.properties; then
        echo "" >> local.properties
        echo "sdk.dir=$HOME/Library/Android/sdk" >> local.properties
    fi
    
    # 确保有 SDK 路径
    if ! grep -q "sdk.dir" local.properties; then
        echo "" >> local.properties
        echo "sdk.dir=$HOME/Library/Android/sdk" >> local.properties
    fi
    
    print_success "API Key 配置检查通过"
}

# 编译 APK
build_apk() {
    print_step "步骤 6/6: 编译 Android APK"
    
    print_info "正在清理旧的构建文件..."
    ./gradlew clean
    
    print_info "正在编译调试版 APK..."
    print_warning "首次编译需要下载依赖，可能需要 10-15 分钟"
    print_warning "请保持网络连接，建议使用 WiFi"
    
    ./gradlew assembleDebug
    
    if [ $? -eq 0 ]; then
        print_success "编译成功！"
        echo ""
        print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        print_success "APK 文件已生成："
        print_info "📦 位置: app/build/outputs/apk/debug/app-debug.apk"
        
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        if [ -f "$APK_PATH" ]; then
            APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
            print_info "📊 大小: $APK_SIZE"
        fi
        
        echo ""
        print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        print_success "安装方法："
        print_info "1️⃣  通过 USB 连接手机，然后执行："
        print_info "   adb install $APK_PATH"
        echo ""
        print_info "2️⃣  或者将 APK 文件传输到手机，点击安装"
        echo ""
        print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    else
        print_error "编译失败"
        print_info "请查看上方的错误信息"
        exit 1
    fi
}

# 主函数
main() {
    clear
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "          Android 开发环境自动安装和编译脚本"
    echo "                  通话录音管理应用"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    print_warning "此脚本将自动安装以下工具："
    echo "  • Homebrew (如果未安装)"
    echo "  • Java JDK 17"
    echo "  • Android 命令行工具"
    echo "  • Android SDK (API 34)"
    echo ""
    print_warning "预计下载大小: 约 500 MB - 1 GB"
    print_warning "预计时间: 15-30 分钟"
    echo ""
    read -p "是否继续？(y/n) " -n 1 -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "已取消安装"
        exit 0
    fi
    
    echo ""
    print_info "开始安装..."
    sleep 2
    
    # 检查项目目录
    check_project_dir
    
    # 执行安装步骤
    install_homebrew
    install_java
    install_android_cmdline_tools
    setup_android_sdk
    check_api_key
    build_apk
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print_success "🎉 全部完成！"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    print_info "下次编译只需执行："
    print_info "  ./gradlew assembleDebug"
    echo ""
    print_info "环境变量已添加到 ~/.zshrc"
    print_info "请执行以下命令使其生效："
    print_info "  source ~/.zshrc"
    echo ""
}

# 运行主函数
main
