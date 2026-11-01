# 应用更新源：成都服务器 + GitHub 回退

App 内置更新检查。成都服务器是主源，GitHub 是回退源；两边都只放静态文件：
`update.json` + APK。没有后端服务。

## 1. 工作流

1. App 读取两个 manifest：
   - 成都：`https://<成都域名>/raster-dial/update.json`
   - GitHub raw：`https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json`
2. 取 `versionCode` 较大的 manifest。
3. 如果 `versionCode` 大于已安装版本，弹窗展示版本、说明和大小。
4. 用户点“下载并安装”后，按 `apkUrls` 顺序下载（成都先，GitHub 后）。
5. 下载完成校验 SHA-256；通过后交给系统安装器。
6. 某个 URL 失败或 SHA 不匹配，自动试下一个。
7. 首次安装需要允许“安装未知应用”。

## 2. `update.json`

```json
{
  "versionCode": 3,
  "versionName": "0.1.2",
  "minSdk": 28,
  "publishedAt": "2026-11-01T00:00:00Z",
  "sha256": "<apk 的 sha256>",
  "size": 8860470,
  "notes": "0.1.2: in-app update check; 5 demo groups.",
  "apkUrls": [
    "https://updates.example.com/raster-dial/raster-dial-0.1.2-debug.apk",
    "https://github.com/kdjdhe/raster-dial/releases/download/v0.1.2/raster-dial-0.1.2-debug.apk"
  ]
}
```

- `versionCode` 必须和 `app/build.gradle.kts` 一致。
- `sha256` 是 APK 文件的 SHA-256，不是签名证书指纹。
- `size` 是字节数。
- `apkUrls` 按优先级排列；App 会逐个尝试。

## 3. App 端

- `UpdateChecker.java`：检查、下载、SHA-256 校验、打开系统安装器。
- `ApkProvider.java`：把 `cache/updates` 下的 APK 以 `content://` 暴露给系统安装器，
  不依赖 AndroidX。
- `MainActivity` 工具卡片有“检查更新”；`onResume` 每 24 小时自动检查一次。
- `AndroidManifest.xml`：`INTERNET`、`REQUEST_INSTALL_PACKAGES`、`.ApkProvider`。
- `app/build.gradle.kts` 的两个地址：

```kotlin
buildConfigField("String", "UPDATE_MANIFEST_PRIMARY",
    "\"https://updates.example.com/raster-dial/update.json\"")
buildConfigField("String", "UPDATE_MANIFEST_FALLBACK",
    "\"https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json\"")
```

当前 `updates.example.com` 是占位。换成你的成都域名后重新构建；GitHub 回退现在就能用。

> 0.1.1 及更早版本没有更新检查。需要手动安装一次 0.1.2，之后才能在 App 内更新。

## 4. GitHub 源

- 仓库根目录的 `update.json`（`main` 分支）就是回退 manifest。
- raw 地址：
  `https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json`
- `apkUrls` 第二项指向 GitHub Release 资产：
  `https://github.com/kdjdhe/raster-dial/releases/download/v<version>/raster-dial-<version>-debug.apk`
- `tools/publish_update.py` 默认生成这个 Release 资产 URL。
- GitHub raw 有 CDN 缓存，推送后可能几分钟才生效；App 会加 `Cache-Control: no-cache`，
  但 raw CDN 不保证立即刷新。急用可让 App 走成都主源。

## 5. 成都服务器

### 5.1 目录

```bash
sudo mkdir -p /var/www/raster-dial
sudo chown -R "$USER":www-data /var/www/raster-dial
```

最终两个文件：

```text
/var/www/raster-dial/update.json
/var/www/raster-dial/raster-dial-0.1.2-debug.apk
```

### 5.2 Caddy（推荐，自动 HTTPS）

```caddyfile
updates.example.com {
    root * /var/www
    file_server
    header /raster-dial/update.json Cache-Control "no-cache, max-age=60"
    header /raster-dial/*.apk Cache-Control "public, max-age=31536000, immutable"
}
```

- DNS 的 A/AAAA 记录指向成都服务器公网 IP；
- 放行 80/443；
- Caddy 自动申请 Let's Encrypt 证书；
- 验证：`curl -fsS https://updates.example.com/raster-dial/update.json`。

### 5.3 Nginx + certbot

```nginx
server {
    listen 443 ssl http2;
    server_name updates.example.com;
    root /var/www;

    location = /raster-dial/update.json {
        add_header Cache-Control "no-cache, max-age=60";
        try_files $uri =404;
    }
    location /raster-dial/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
        try_files $uri =404;
    }

    ssl_certificate /etc/letsencrypt/live/updates.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/updates.example.com/privkey.pem;
}
```

```bash
sudo certbot --nginx -d updates.example.com
```

### 5.4 没有公网 80/443 或备案时

大陆服务器对 80/443 对外提供 Web 服务通常涉及备案。没有备案时可以考虑：

1. **Cloudflare Tunnel**：成都服务器只出站，Cloudflare 边缘终止 TLS：

   ```bash
   cloudflared tunnel --url http://127.0.0.1:8080
   ```

   Caddy 监听 `127.0.0.1:8080`，域名 CNAME 到 Tunnel。

2. 用香港/海外节点做入口，成都服务器只做源站。
3. 用非标准端口 + 有效证书（仍建议域名 + HTTPS）。

备案/合规要求请按你的实际情况确认；这里只给技术方案。

### 5.5 上传

```bash
export CHENGDU_HOST=user@your-chengdu-host
tools/sync_chengdu.sh work/raster-dial-0.1.2-debug.apk
```

`CHENGDU_PATH` 默认 `/var/www/raster-dial`；脚本会 `mkdir -p` 后 `scp update.json` 和 APK。

## 6. 发布一个新版本

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`。
2. 构建（个人调试 `assembleDebug`；正式签名的更新包用 `assembleRelease`）：

   ```bash
   ./gradlew assembleDebug
   # 或：./gradlew assembleRelease
   ```

   > release 签名包：把 `app/build/outputs/apk/release/app-release.apk` 复制成
   > `work/raster-dial-0.1.3.apk`，再上传。

3. 创建 GitHub Release 并上传 APK：

   ```bash
   gh release create v0.1.3 work/raster-dial-0.1.3-debug.apk \
     --title "Raster Dial v0.1.3" --notes-file work/release-notes.md
   ```

4. 生成 `update.json`：

   ```bash
   python tools/publish_update.py \
     --chengdu-base https://updates.example.com/raster-dial \
     --notes "0.1.3: ..."
   ```

   > release 签名包：加 `--apk work/raster-dial-0.1.3.apk --asset-name raster-dial-0.1.3.apk`。

5. 提交并推送 `update.json`（GitHub 回退源）：

   ```bash
   git add update.json && git commit -m "Update manifest for 0.1.3" && git push
   ```

6. 上传到成都：

   ```bash
   CHENGDU_HOST=user@your-chengdu-host tools/sync_chengdu.sh work/raster-dial-0.1.3-debug.apk
   # release 签名包：... tools/sync_chengdu.sh work/raster-dial-0.1.3.apk
   ```

7. 验证：

   ```bash
   curl -fsS https://updates.example.com/raster-dial/update.json | jq .
   curl -fsSI https://updates.example.com/raster-dial/raster-dial-0.1.3-debug.apk | head
   sha256sum work/raster-dial-0.1.3-debug.apk
   ```

8. 手机 App 里点“检查更新”，或等 24 小时自动检查。

## 7. 签名（必读）

完整步骤、keystore 生成、debug→release 迁移见 [SIGNING.md](SIGNING.md)。

- Android 只允许同包名、同签名证书的 APK 覆盖安装。
- 当前 Release/APK 是 **debug 签名**（`~/.android/debug.keystore`）。
  v0.1.0 / 0.1.1 / 0.1.2 能互相覆盖，前提是都在同一台机器、同一个 debug keystore 构建。
- 成都服务器必须放**同一份 APK 文件**，不要在服务器上重新构建；
  否则 debug key 不同，安装会报签名不一致。
- 如果以后改用 stable release keystore：
  - 已安装的 debug 版不能直接升级，需要卸载重装一次；
  - `update.json` 的 `sha256` 要对应新签名 APK；
  - 私钥不要进仓库。
- `update.json` 只校验文件完整性（SHA-256）；APK 签名由 Android 安装器校验。

## 8. 当前占位

- 仓库 `update.json` 和 `BuildConfig.UPDATE_MANIFEST_PRIMARY` 里的 `updates.example.com` 是占位。
- 换成你的成都域名后：
  - 重新构建 APK（主源 URL 编译进 App）；
  - 更新 `update.json` 的 `apkUrls[0]`；
  - 用 `tools/publish_update.py --chengdu-base https://你的域名/raster-dial` 重新生成；
  - 上传。
- 成都服务器没准备好之前，App 会自动走 GitHub 回退源，功能不受影响。
