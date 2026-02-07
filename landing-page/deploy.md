# 语音快记 - 发布网页部署指南

## 一、GitHub Pages 部署（推荐）

### 前提条件
- 代码已推送到 GitHub 仓库

### 步骤

#### 1. 配置 GitHub Pages
1. 打开 GitHub 仓库页面
2. 进入 **Settings** → **Pages**
3. **Source** 选择 `Deploy from a branch`
4. **Branch** 选择 `main`，目录选择 `/ (root)`
5. 点击 **Save**

#### 2. 配置 GitHub Actions 自动发布 APK
在 `.github/workflows/` 中已有 CI 配置，扩展添加 Release 发布：
```yaml
# 在 android-build.yml 中添加 Release 步骤
- name: 🏷️ 创建 Release
  if: startsWith(github.ref, 'refs/tags/')
  uses: softprops/action-gh-release@v1
  with:
    files: app/build/outputs/apk/debug/app-debug.apk
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

#### 3. 发布新版本
```bash
# 打标签触发 Release
git tag v1.0.0
git push origin v1.0.0
```

#### 4. 更新下载链接
编辑 `landing-page/index.html`，将下载按钮的 href 更新为：
```
https://github.com/<你的用户名>/CallRecordManager/releases/latest/download/app-debug.apk
```

### 访问地址
```
https://<你的用户名>.github.io/CallRecordManager/landing-page/
```

---

## 二、Vercel 部署

### 步骤
1. 访问 [vercel.com](https://vercel.com)
2. 用 GitHub 账号登录
3. Import 项目 `CallRecordManager`
4. **Root Directory** 设置为 `landing-page`
5. **Framework Preset** 选择 `Other`
6. 点击 **Deploy**

### 自定义域名
1. 在 Vercel 项目 Settings → Domains
2. 添加你的自定义域名
3. 按照提示配置 DNS 记录

---

## 三、Netlify 部署

### 步骤
1. 访问 [netlify.com](https://netlify.com)
2. 用 GitHub 账号登录
3. **New site from Git** → 选择仓库
4. **Base directory** 设置为 `landing-page`
5. **Publish directory** 设置为 `landing-page`
6. 点击 **Deploy site**

---

## 四、添加应用截图

### 截图规范
- 分辨率：1080 x 1920（竖屏）
- 格式：PNG 或 WebP
- 命名规范：
  - `screenshot-home.png` — Hero 区域展示
  - `screenshot-1.png` — 录音列表
  - `screenshot-2.png` — 录音详情
  - `screenshot-3.png` — 会谈纪要
  - `screenshot-4.png` — 时间线概览

### 获取截图方法
1. **真机截图**：在手机上运行 App，截图后传输到电脑
2. **模拟器截图**：Android Studio 模拟器中截图
3. 将截图放入 `landing-page/images/` 目录

---

## 五、生成下载二维码

推荐使用以下工具生成 APK 下载链接的二维码：
- [qr-code-generator.com](https://www.qr-code-generator.com/)
- [草料二维码](https://cli.im/)

将生成的二维码图片保存为 `landing-page/images/qr-download.png`

---

## 六、发布到蒲公英/fir.im（国内分发）

### 蒲公英 (pgyer.com)
1. 注册账号
2. 上传 APK
3. 获取下载页面链接和二维码
4. 将链接更新到落地页

### fir.im
1. 注册账号
2. 上传 APK
3. 获取短链接
4. 更新到落地页下载按钮
