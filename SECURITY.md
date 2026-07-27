# 安全策略

## 报告问题

请不要在公开 Issue 中发布密钥、用户数据、签名文件或可利用漏洞。请通过 GitHub Security Advisories 联系维护者；如果该功能不可用，请先联系仓库维护者后再提供最小复现信息。

## 当前安全边界

- Android 默认本地优先，不会自动上传用户图片。
- Web 的 Supabase anon key 只能作为公开客户端配置，数据库权限必须由 RLS 控制。
- Supabase service-role key 只能用于本地导入脚本或受保护的 CI Secret。
- 发布签名私钥只存在于受保护的本地目录或 GitHub Actions Secret。
- 公开仓库不接收真实采样数据和个人图片。
