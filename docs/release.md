# 发布指南

## 本地发布前检查

1. 更新 app/build.gradle 的 versionName 和 versionCode。
2. 更新 CHANGELOG.md、更新日志.md 和应用内 changelog。
3. 确认 applicationId 仍为 com.dlovel。
4. 确认 RIPPLE_SIGNING_PROPERTIES 指向长期保存的签名配置。
5. 构建 release APK，并用 Android SDK 的 apksigner 检查证书指纹。
6. 确认新版本 versionCode 大于上一版，并在测试设备上覆盖安装。
7. 创建同名 Git tag 和 GitHub Release，附 APK、校验和和变更摘要。

## GitHub Actions 发布

在仓库 Secrets 中配置：

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

向 main 推送 v 开头的 tag 会触发发布工作流。工作流把 keystore 写到临时目录，生成 signing.properties，构建 release APK，并将它上传到 GitHub Release。任何私钥都不会写入仓库。

## 签名兼容

历史 v1.7.2 APK 的证书是 Android Debug 证书。当前这台开发机的持久化签名目录保存了同一证书的受控副本。备份该目录，并在迁移到正式发布证书前设计 Android signing certificate lineage；直接换钥匙会导致旧安装无法覆盖升级。
