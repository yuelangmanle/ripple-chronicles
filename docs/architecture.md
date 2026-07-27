# 架构说明

## Android

MainActivity 提供 Compose 宿主，MainScreen 负责导航和全局生命周期。页面通过 LocalAppStore 读取状态，StorageManager 管理图片文件，DatasetTransferService 管理备份恢复，ExportService 负责 ZIP/Word 导出。

当前 Android 数据链路是：

用户操作 → Compose 页面 → LocalAppStore 原子更新 → app_state.json 与图片文件 → 导出/分享。

LocalAppStore 仍然是现阶段的本地单一事实来源。后续 schema 版本、原子替换、迁移和备份恢复应在这一边界内完成，避免把存储细节扩散到 UI。

## Web

Web 端使用 React 页面和 Supabase 客户端，当前更偏向数据管理与导入导出。Android 和 Web 的业务字段存在历史差异，统一协议是 TODO 中的 P1 任务。

## 发布

应用包名 com.dlovel 是兼容性约束。版本号必须递增，发布签名必须从仓库外注入，GitHub Releases 负责承载发布说明和 APK 资产。
