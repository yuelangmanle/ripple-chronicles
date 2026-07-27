# 贡献指南

感谢参与 Ripple Chronicles。提交代码前请先确认改动服务于溯澜录的本地优先科研记录场景。

## 提交流程

1. 从 main 创建短生命周期分支。
2. 保持包名 com.dlovel 不变，除非先提交迁移方案。
3. 新功能或修复同时补充测试、更新日志和必要文档。
4. 本地运行 Android 单元测试、Android Debug 构建、Web 类型检查和 lint。
5. 使用清晰的 Conventional Commits 风格提交信息，例如 feat: add sample metadata。
6. 创建 Pull Request，说明问题、方案、验证结果和兼容性影响。

## 不要提交

- keystore、签名 properties、.env、Supabase service-role key
- APK、AAB、heap dump 和本地 Gradle/Android Studio 目录
- 个人照片、二维码、原始 DOCX/XLSX 或没有明确再分发许可的资料
- 用户的真实采样数据

## 代码风格

Android 遵循现有 Kotlin/Compose 结构；Web 使用 TypeScript 严格类型。优先小范围、可回滚的改动，避免把产品逻辑藏在 UI 组件里。
