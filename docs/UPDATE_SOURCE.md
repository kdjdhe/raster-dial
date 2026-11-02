# 应用更新源：成都服务器 + GitHub 回退

App 内置更新检查。成都服务器是主源，GitHub 是回退源；两边都只放静态文件：
`update.json` + APK。没有后端服务。

## 1. 工作流

1. App 读取两个 manifest：
   - 成都：`https://update.litanghub.top:8443/raster-dial/update.json`
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
  "versionCode": 8,
  "versionName": "0.1.7",
  "minSdk": 28,
  "publishedAt": "2026-11-02T06:24:22Z",
  "sha256": "<apk 的 sha256>",
  "size": 8478717,
  "notes": "0.1.7: 更新检查切换到成都主源。",
  "apkUrls": [
    "https://update.litanghub.top:8443/raster-dial/raster-dial-0.1.7.apk",
    "https://github.com/kdjdhe/raster-dial/releases/download/v0.1.7/raster-dial-0.1.7.apk"
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
    "\"https://update.litanghub.top:8443/raster-dial/update.json\"")
buildConfigField("String", "UPDATE_MANIFEST_FALLBACK",
    "\"https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json\"")
```

主源地址编译进 APK（0.1.7 起）；换域名/端口需要重新构建发版。GitHub 回退随 `main` 的
`update.json` 生效，不用重建。

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

## 5. 成都服务器（已部署：2026-11-02）

实际部署参数（v0.1.7 起 App 内置此主源）：

| 项 | 值 |
|---|---|
| 公网 IP | `47.108.234.67`（阿里云 ECS，Ubuntu 26.04） |
| 域名 | `update.litanghub.top`，Cloudflare **DNS-only**（灰云）A 记录 → 上表 IP |
| 端口 | `8443`；阿里云安全组需放行 TCP 8443 |
| 证书 | Let's Encrypt：`certbot certonly --dns-cloudflare`（凭据 `/root/.secrets/cloudflare.ini`，DNS-01，已配置自动续期） |
| 证书路径 | `/etc/letsencrypt/live/update.litanghub.top/{fullchain.pem,privkey.pem}` |
| Web 根 | `/var/www`；更新文件在 `/var/www/raster-dial/` |
| nginx 配置 | `/etc/nginx/sites-enabled/raster-tls.conf` |

```nginx
server {
    listen 8443 ssl;
    server_name update.litanghub.top;

    ssl_certificate     /etc/letsencrypt/live/update.litanghub.top/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/update.litanghub.top/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    root /var/www;
    location /raster-dial/ {
        add_header Cache-Control "no-cache, max-age=60";
        try_files $uri =404;
    }
}
```

`/var/www/raster-dial/` 里放 `update.json` 和 `raster-dial-<version>.apk`；旧版本 APK 保留一份
给还没升级的用户。

### 实测注意（2026-11-02）

- Android 真机（Termux 的 curl）直连本端口：TLS 1.3 完整握手、证书链 `Verify return code: 0`、
  200 JSON —— **非标端口直连本身可用**。
- 但链路中间盒会按 ClientHello 形态注入 RST：Python requests、Windows Schannel 版 curl、
  以及**境外直连**（首尔实测）会被重置；浏览器类/Android 类握手不受影响。这类客户端会落到
  `apkUrls[1]` 的 GitHub 回退，不影响国内 App 用户。
- App 内“检查更新”访问主源时，服务器 nginx access log 有对应请求记录，可用于排查。

### 备选方案（换域名/端口或持续被干扰时）

1. **Cloudflare 代理（橙云）+ SSL 模式 Full**：用户连 CF 边缘 443（CF 自动证书），CF 回源到
   8443。注意 SSL 模式是 zone 级设置，会作用于该域下所有代理记录。
2. **Cloudflare Tunnel**：`cloudflared tunnel --url http://127.0.0.1:8080`，域名 CNAME 到隧道，
   无需开放端口。大陆 80/443 对外 Web 服务涉及备案，非标端口 + 隧道可绕开。

### 5.5 上传

```bash
export CHENGDU_HOST=root@47.108.234.67
export CHENGDU_KEY=~/.ssh/id_ed25519_aliyun   # 可选，见 tools/sync_chengdu.sh
tools/sync_chengdu.sh work/raster-dial-0.1.7.apk
```

`CHENGDU_PATH` 默认 `/var/www/raster-dial`；脚本会 `mkdir -p` 后 `scp update.json` 和 APK。

## 6. 发布一个新版本

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`，更新 `CHANGELOG.md`。
2. 构建 release 签名包（需要 `keystore.properties`）：

   ```bash
   ./gradlew assembleRelease
   cp app/build/outputs/apk/release/app-release.apk work/raster-dial-<version>.apk
   ```

3. 生成 `update.json`（成都 + GitHub 双源）：

   ```bash
   python tools/publish_update.py \
     --apk work/raster-dial-<version>.apk \
     --asset-name raster-dial-<version>.apk \
     --chengdu-base https://update.litanghub.top:8443/raster-dial \
     --notes "<version>: ..."
   ```

4. 提交并推送 `update.json`（GitHub 回退源）。本机 git 配了
   `http.proxy=127.0.0.1:7897`，代理没开时 push 会失败：

   ```bash
   git add update.json && git commit -m "Release manifest for <version>" && git push
   ```

5. 创建 GitHub Release 并上传 APK（资产名与 `--asset-name` 一致）：

   ```bash
   gh release create v<version> work/raster-dial-<version>.apk \
     --title "Raster Dial v<version>" --notes-file work/release-notes.md
   ```

6. 上传到成都（同一份 APK 文件，不要在服务器上重新构建）：

   ```bash
   export CHENGDU_HOST=root@47.108.234.67
   export CHENGDU_KEY=~/.ssh/id_ed25519_aliyun
   tools/sync_chengdu.sh work/raster-dial-<version>.apk
   ```

7. 验证：

   ```bash
   curl -fsS https://update.litanghub.top:8443/raster-dial/update.json | jq .
   # 部分本机客户端（Python/Schannel）会被链路 RST 干扰，优先用真机或 openssl：
   echo | openssl s_client -connect update.litanghub.top:8443 \
     -servername update.litanghub.top -verify_hostname update.litanghub.top \
     -verify_return_error 2>&1 | grep -E "Cipher is|Verify return"
   ```

8. 手机 App 里点“检查更新”，或等 24 小时自动检查。

## 7. 签名（必读）

完整步骤、keystore 生成、debug→release 迁移见 [SIGNING.md](SIGNING.md)。

- Android 只允许同包名、同签名证书的 APK 覆盖安装。
- 当前 Release/APK 是 **release 签名**（keystore `~/.android-keys/raster-dial-release.jks`，
  见 [SIGNING.md](SIGNING.md)）；v0.1.3 起的所有版本可相互覆盖升级。
- 成都服务器必须放**同一份 APK 文件**，不要在服务器上重新构建；
  否则签名会不同，安装会报签名不一致。
- `update.json` 只校验文件完整性（SHA-256）；APK 签名由 Android 安装器校验。

## 8. 当前状态（2026-11-02）

- 成都主源 **已上线**：`https://update.litanghub.top:8443/raster-dial/update.json`
  （直连 TLS，Let's Encrypt，DNS-only A 记录；详见 §5）。
- 仓库 `update.json`（versionCode 8 / 0.1.7）与成都服务器上的清单是同一份，
  `apkUrls[0]` 指向成都、`apkUrls[1]` 指向 GitHub Release。
- `BuildConfig.UPDATE_MANIFEST_PRIMARY` 自 0.1.7 起指向成都主源；
  GitHub 回退始终可用，主源故障不会中断更新链路。
