# 架构说明

## Android

MainActivity 提供 Compose 宿主，MainScreen 负责导航和全局生命周期。页面通过 LocalAppStore 读取状态，StorageManager 管理图片文件，DatasetTransferService 管理备份恢复，ExportService 负责 ZIP、CSV、Excel、Word/PDF 导出。

当前 Android 数据链路是：

用户操作 → Compose 页面 → LocalAppStore 原子更新 → app_state.json 与图片文件 → 导出/分享。

LocalAppStore 仍然是现阶段的本地单一事实来源。每次更新记录操作历史；图片达到约 1000 张时，保存流程额外把数据集、图片和物种轻量元数据镜像到 SQLite。SQLite 只作为可回滚的性能层，不改变 LocalAppStore 公共接口，后续可替换为 Room。

备份清单包含数据集版本、采样事件、图片标注、比例尺、质量和鉴定历史；恢复前展示版本与图片数量，冲突时默认重命名，异常时回滚已复制文件。

## Web

Web 端使用 React 页面和 Supabase 客户端，当前更偏向数据管理与导入导出。Android 和 Web 的业务字段存在历史差异，统一协议是 TODO 中的 P1 任务。

## 发布

应用包名 com.dlovel 是兼容性约束。版本号必须递增，发布签名必须从仓库外注入，GitHub Releases 负责承载发布说明和 APK 资产。
