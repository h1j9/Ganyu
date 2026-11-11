# 绀羽 · Ganyu

<img src="app/src/main/res/mipmap-nodpi/ic_launcher.jpg" width="88" height="88" alt="绀羽图标">

ColorOS 设置外观与背景自定义 LSPosed 模块。

作者：**隐形人**。

**[下载安装包](https://github.com/h1j9/Ganyu/releases)** · [更新记录](CHANGELOG.md)

## 功能

- 自定义设置全局背景，调整图片不透明度。
- 关于本机 OTA 卡片、软件更新全屏背景独立设置。
- 可使用同一张原图，分别裁切两处 OTA 背景。
- 直接调用系统相册选图，支持拖动和双指缩放裁切。
- 右上角一键重启设置。
- 模块界面跟随系统浅色、深色模式。
- **使用自定义 OTA 图片时，对应动态背景会变为静态图片；恢复默认后恢复系统原生背景与动画。**

## 适配情况

目前功能仅在 **一加 Ace 5 至尊版（PLC110）／ColorOS 17** 上验证。其他机型、其他设置或软件更新应用版本尚未验证。

模块针对该版本系统应用的类、方法和资源适配，不能仅依据 Android 最低版本判断兼容性。部分独立于设置包的系统页面不属于全局背景覆盖范围。

v1.10 正式签名 Release 包已通过构建、签名与静态检查；正式签名包的真机安装迁移测试尚未完成，因此首发标为预发布版。

## 安装与使用

1. 安装 [Release APK](https://github.com/h1j9/Ganyu/releases)，在兼容的 LSPosed 框架中启用模块。
2. 勾选作用域：
   - 设置：`com.android.settings`
   - 软件更新：`com.oplus.ota`
3. 按框架提示重启作用域或手机，使模块加载。
4. 打开绀羽，选择背景并裁切保存。设置背景可点击右上角重启设置；软件更新背景重新进入对应页面查看。
5. 恢复默认将清除对应自定义图片，关闭对应页面进程，下次进入时加载系统原生效果。

如果已安装早期调试签名测试版，先备份需要保留的数据，再卸载测试版、安装正式签名版并重新启用模块。后续正式版本使用同一长期签名，可正常覆盖升级。

## 签名与校验

- 包名：`local.coloros.otastatic`
- 证书名称：`CN=隐形人, O=Personal`
- 正式签名证书 SHA-256：

```text
fbad1f74a49df069ce1850d99478e0efb6641925ea8f3d4ef54ec9c2505a8317
```

每个 Release 附带 APK、公开签名证书和 SHA-256 校验文件。公开证书不包含私钥。

## 从源码构建

需要 JDK 17 或 21、Android SDK Platform 35，以及对应 Android Build Tools。无需安装 Android Studio；也可使用 SDK 命令行工具准备环境。

在本仓库根目录执行：

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

```sh
# Linux / macOS
./gradlew :app:assembleDebug
```

本机 SDK 可通过 `ANDROID_HOME` 或未提交的 `local.properties` 指定。调试包输出在 `app/build/outputs/apk/debug/`。

Release 构建需要自己的签名配置。仓库不包含原作者私钥和密码。当前本地 Windows 发布流程使用项目同级的 `.ganyu-signing/signing.properties`，格式如下：

```properties
storeFile=your-release.p12
keyAlias=your-key-alias
storePassword=your-private-password
keyPassword=your-private-password
```

`storeFile` 相对于该属性文件目录解析。准备好自己的签名后执行：

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

不要将实际签名属性文件、私钥或密码提交到仓库。本仓库附带的 `scripts/` 是原作者本地签名及发布辅助脚本，构建缓存路径需按自己的环境调整。

## 反馈

请在 [Issues](https://github.com/h1j9/Ganyu/issues) 提供：

- 手机型号、ColorOS 版本。
- 设置和软件更新应用版本。
- 模块版本、复现步骤及适当的截图。

请遮住账号、设备标识及私人图片。无需上传私钥、密码或整份私人图库。

## 第三方素材

界面线性图标来自 [Lucide](https://lucide.dev/)，许可见 [LICENSE-Lucide.txt](app/src/main/assets/LICENSE-Lucide.txt)，同时随 APK 打包。
