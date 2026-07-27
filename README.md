# Ripple Chronicles

溯澜录（Ripple Chronicles）是一款面向水生生物实验室与野外采样场景的本地优先记录工具，覆盖图片采集、物种检索、数据集管理、备份和报告导出。

应用包名保持为 com.dlovel，以兼容已有安装；应用中文名称为溯澜录。

## 当前能力

- CameraX 连拍、变焦、曝光和 HDR/夜景/自动增强扩展
- 本地图片图库、物种关联、批量选择和导出
- 内置 JSON 物种库与 Excel 导入
- 数据集备份恢复、ZIP 图片导出和 Word 鉴定报告
- Android Jetpack Compose 应用与 Web 管理端
- 设置页手动打开 GitHub Releases 发布页

## 技术栈

- Android：Kotlin、Jetpack Compose、CameraX、Coil、Apache POI
- 本地数据：LocalAppStore JSON + 文件系统
- Web：React、TypeScript、Vite、Tailwind CSS、Supabase
- 构建：Android Gradle Plugin 8.2、Gradle 8.13、JDK 17、Node.js 20+

## 开发环境

Android 需要 Android Studio、JDK 17 和 Android SDK 34。Web 需要 Node.js 20 或更高版本。

~~~powershell
.\gradlew.bat :app:assembleDebug
npm ci
npm run check
npm run lint
~~~

Android wrapper 使用公开的 Gradle 发行地址，不依赖某台电脑上的私有压缩包。应用包名、版本号和签名配置请勿随意改动。

## 签名与升级兼容

历史 APK 使用 Android Debug 证书。当前发布配置支持通过仓库外的 signing.properties 继续使用同一证书，因此可以覆盖升级历史安装。

签名私钥永远不提交到 Git。默认查找位置为项目同级的 tupian-signing/signing.properties，也可以用 RIPPLE_SIGNING_PROPERTIES 指定路径。示例配置见 signing.properties.example。

公开发布时，GitHub Actions 从加密 Secrets 生成临时签名文件。发布前必须确认新 APK 的 applicationId、versionCode 和证书指纹都与上一版兼容。

## 配置云端能力

复制 .env.example 为本地 .env，并填写 Web 的 anon key。物种导入脚本需要 SUPABASE_SERVICE_ROLE_KEY；该密钥只能通过本地环境变量或密码管理器提供，不能写进源码。

Android 云端字段来自 Gradle 属性或环境变量 SUPABASE_URL、SUPABASE_ANON_KEY。默认产品仍然是本地优先模式。

## 仓库结构

- app：Android 应用
- src：Web 管理端
- scripts：物种数据构建与导入脚本
- supabase：数据库迁移
- docs：架构、发布和维护文档
- TODO.md：产品与工程路线图

## 贡献

请先阅读 CONTRIBUTING.md。Bug、功能建议和安全问题分别使用仓库模板提交；安全问题不要公开写在 Issue 中。

## 许可证

本项目使用 MIT License。第三方依赖和数据源仍以其各自许可证为准。
