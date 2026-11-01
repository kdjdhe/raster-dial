# App 签名：debug、release 与更新源

Android 只允许**同包名、同签名证书**的 APK 覆盖安装。更新源要长期可用，必须每次都用同一把钥匙签名。

## 0. 当前状态

- v0.1.0 / v0.1.1 / v0.1.2 的 GitHub APK 都是 **debug 签名**：
  `~/.android/debug.keystore`，alias `androiddebugkey`，密码 `android`。
- 这些版本能互相覆盖，前提是都在同一台机器、同一个 debug keystore 构建。
- debug key 不是秘密；只适合个人侧载。公开分发请用 stable release key。
- 成都服务器必须放**同一份 APK 文件**，不要在服务器上重新构建。

## 1. 生成 release keystore

把 keystore 放在仓库外，例如 `C:/Users/<you>/.android-keys/raster-dial-release.jks`：

```bash
mkdir -p "$HOME/.android-keys"
keytool -genkeypair -v \
  -keystore "$HOME/.android-keys/raster-dial-release.jks" \
  -alias raster-dial \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Raster Dial, O=Raster Dial, C=CN"
```

- `keytool` 在 JDK 里；如果不在 PATH，用 `"$JAVA_HOME/bin/keytool"`。
- 会提示输入 store password 和 key password；PKCS12 下两者相同即可。
- **记住密码并备份 keystore**。丢失后无法给已安装用户发更新。
- 查看证书指纹：

```bash
keytool -list -v -keystore "$HOME/.android-keys/raster-dial-release.jks" -alias raster-dial
```

## 2. Gradle 配置

仓库里已经接好 `app/build.gradle.kts` 的 `signingConfigs`，它读取根目录的
`keystore.properties`（已加入 `.gitignore`）：

```properties
storeFile=C:/Users/you/.android-keys/raster-dial-release.jks
storePassword=你的store密码
keyAlias=raster-dial
keyPassword=你的key密码
```

从 `keystore.properties.example` 复制一份再改。`storeFile` 用正斜杠；
`keystore.properties`、`*.jks`、`*.keystore` 都不进仓库。

没有 `keystore.properties` 时，`assembleRelease` 仍会产出 **unsigned** APK；
debug 构建不受影响。

## 3. 构建并验证

```bash
./gradlew assembleRelease
```

签名后的 APK：

```text
app/build/outputs/apk/release/app-release.apk
```

验证：

```bash
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

`--print-certs` 会显示证书 SHA-256；把它和 keystore 的指纹对一下。
APK 的 SHA-256 用于 `update.json`，不是签名指纹。

## 4. 用于更新源

把 release APK 复制成发布文件名，再生成 manifest：

```bash
cp app/build/outputs/apk/release/app-release.apk work/raster-dial-0.1.3.apk

python tools/publish_update.py \
  --apk work/raster-dial-0.1.3.apk \
  --asset-name raster-dial-0.1.3.apk \
  --chengdu-base https://updates.example.com/raster-dial \
  --notes "0.1.3: ..."
```

- `--asset-name` 决定 GitHub Release 资产名和成都 URL 的文件名；
  不传则默认 `raster-dial-<version>-debug.apk`。
- 创建 Release 时上传同一个文件名：

```bash
gh release create v0.1.3 work/raster-dial-0.1.3.apk \
  --title "Raster Dial v0.1.3" --notes-file work/release-notes.md
```

- 成都同步：

```bash
CHENGDU_HOST=user@host tools/sync_chengdu.sh work/raster-dial-0.1.3.apk
```

`update.json` 里的 SHA-256 由 APK 文件计算；App 下载后校验；Android 安装器再校验签名。

## 5. 从 debug 迁移到 release

已安装的 debug 版**不能**被 release-signed APK 覆盖，会报签名不一致。需要：

1. 备份 App 数据（可选）：
   - App 的自定义组在私有目录 `files/custom`，SharedPreferences 在 `lock`；
   - `allowBackup=false`，所以 `adb backup` 不可用；
   - 设备已 root 时可直接打包 `/data/data/com.raster.dial`；
   - 或者接受重装后重新添加自定义图/设置。
2. 卸载：
   ```bash
   adb uninstall com.raster.dial
   # 想保留数据目录可试：adb uninstall -k com.raster.dial
   ```
3. 安装 release-signed APK：
   ```bash
   adb install -r work/raster-dial-0.1.3.apk
   ```
4. 重新设置壁纸/自定义组。

之后所有更新都必须用同一把 release key。**不要**再换 key，否则又要卸载重装。

> 如果只是个人使用，也可以继续用 debug key：备份 `~/.android/debug.keystore`，
> 始终在同一台机器构建，成都/GitHub 只镜像同一份 APK。这样不用重装，但 debug key
> 不是秘密，别用于公开分发。

## 6. 备份与 CI

- keystore + store/key password：密码管理器或离线加密备份；不要进仓库、不要发聊天记录。
- CI（GitHub Actions 等）：把 keystore base64 和密码放 Secrets，构建时还原到临时文件；
  构建结束删除。
- 如果以后上 Play Store：用 Play App Signing，上传 key 只用于签名上传包；
  App Signing key 由 Google 托管。

## 7. 验证设备上的签名

```bash
adb shell dumpsys package com.raster.dial | grep -A3 "signatures"
```

或者比较 APK 证书：

```bash
keytool -printcert -jarfile work/raster-dial-0.1.3.apk
```

两次的证书 SHA-256 必须一致；不一致时 `adb install -r` 会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 8. 当前发布

- v0.1.2 仍是 debug 签名，带应用内更新检查；GitHub/Chengdu 回退可用。
- 下一次正式发布（v0.1.3）建议用 release keystore 签，按第 5 节迁移一次。
- 签名配置已经接好；你只需要生成 keystore、填 `keystore.properties`、构建。
