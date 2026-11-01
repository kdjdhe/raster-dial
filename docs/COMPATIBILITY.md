# 兼容其他 Android 设备的可行性分析

> 对象：`com.raster.dial` 光栅壁纸（14 组 / 41 张 / 1080×2400 / Canvas 渲染）
> 结论日期：2026-10-31
>
> **2026-10-31 落地状态**：minSdk 已降到 **28（Android 9+）**；平板改为
> **运行时按 surface 宽高比选竖/横素材 + aspect-fill + display rotation 补偿**；
> App 图标、UI 重做、自定义槽位已实现。桌面壁纸已在 NZONE 上验证；
> 锁屏仍不做。真实平板建议再做一次冒烟测试。

## 1. 结论先行

| 目标 | 可行性 | 判断 |
|---|---|---|
| **其他手机的桌面动态壁纸** | **高** | 核心是标准 `WallpaperService`，无 root、无系统权限；主要工作是比例/方向/传感器/渲染兜底 |
| **其他手机的锁屏光栅** | **低** | 锁屏壁纸在 EMUI/MIUI/One UI/ColorOS 上基本是独立的一套（杂志/主题），第三方动态壁纸不保证上锁屏；按 OEM 适配成本高 |
| **平板 / 折叠屏** | **中** | 需要横屏素材变体 + display rotation 补偿 + 比例裁切 |
| **无陀螺仪低端机** | **中高** | 加速度计兜底可用，但方向平滑度和斜向手感下降 |
| **Android 6–8** | **低** | 当前 `minSdk 28`（Android 9）装不上；若降到 23，老 GPU 的 Canvas/DST_IN 需要实测 |
| **Android TV / ChromeOS / Wear** | **低 / 不适配** | 桌面 launcher 通常不展示第三方动态壁纸 |

一句话：**“换一台手机当桌面壁纸”可行性很高；“换一台手机当锁屏”不建议按同一套方案承诺。**

---

## 2. 当前实现的硬编码假设（兼容性风险源）

| # | 假设 | 风险 |
|---|---|---|
| 1 | `minSdk 29` / `targetSdk 29` | Android 6–9 直接装不上；Play Store 需要 target 34+（侧载不受影响） |
| 2 | 素材固定 1080×2400，`drawBitmap(..., Rect(0,0,w,h))` 直接拉伸 | 19.5:9 / 21:9 / 平板 / 折叠屏会变形或构图被裁 |
| 3 | 传感器按“自然竖屏”的 x/y 轴解释 | 平板/折叠屏的自然方向可能是横屏，方向会整体转 90° |
| 4 | `lockHardwareCanvas()` + `saveLayer` + `PorterDuff.DST_IN` + `LinearGradient` | 部分 GPU/驱动上硬件画布不可用或 DST_IN 慢；软件回退在 1080p 会卡 |
| 5 | 每组 2–3 张 1080p ARGB 位图（约 31 MB） | 2 GB 低端机可能被系统回收壁纸进程 |
| 6 | `ACTION_CHANGE_LIVE_WALLPAPER` 能打开系统选择器 | 少数 OEM 的自有壁纸中心不响应，需要回退到设置页 |
| 7 | `WallDebugReceiver` 导出且无鉴权 | 任何 App 都能改组/调试值（只影响外观，但分发时应收紧） |
| 8 | 只做桌面壁纸，不接管锁屏 | 锁屏是否显示由 OEM 决定，不能承诺 |

---

## 3. 兼容性矩阵（按设备/系统）

| 设备/系统 | 桌面壁纸 | 锁屏 | 关键问题 |
|---|---|---|---|
| Android 10–15 手机，有陀螺仪 | 高 | 低–中 | 比例裁切、OEM 选择器；锁屏多为独立壁纸 |
| Android 10–15 手机，无陀螺仪 | 中高 | 低–中 | 加速度计兜底；方向/斜向平滑度下降 |
| Android 6–9 手机 | **不支持** | — | 已定 minSdk 28（Android 9+） |
| 平板（自然横屏） | 中 | 低 | 横屏素材 + rotation 补偿；竖屏素材会被裁 |
| 折叠屏（展开/折叠切换） | 中 | 低 | surface 尺寸会变；rotation/比例需动态处理 |
| 2 GB 低端机 | 中 | 低 | 内存/帧率；需 720p + RGB_565 + 30fps |
| Samsung One UI | 高（桌面） | 低 | 锁屏壁纸独立；动态壁纸可用 |
| Xiaomi MIUI/HyperOS | 高（桌面） | 低 | 壁纸轮播/超级壁纸可能覆盖锁屏 |
| OPPO/OnePlus ColorOS | 高（桌面） | 低 | 自有壁纸中心；选择器可能需手动路径 |
| vivo OriginOS | 高（桌面） | 低 | 官方“光栅锁屏”是系统功能，第三方无法复刻到锁屏 |
| Android TV / ChromeOS | 低 | — | launcher 通常不支持第三方动态壁纸 |

> “低”指“不能保证/需要 OEM 专项适配”，不是“完全不可能”。

---

## 4. 逐项修复建议

### 4.1 比例与分辨率（必做，低风险）

当前直接把 1080×2400 拉伸到 surface。改为 **center-crop 的 aspect-fill**：

```java
// 目标 surface 的宽高比
float target = (float) w / ht;
float src = (float) bm.getWidth() / bm.getHeight();
Rect srcRect;
if (src > target) {
    int cw = (int) (bm.getHeight() * target);
    int left = (bm.getWidth() - cw) / 2;
    srcRect = new Rect(left, 0, left + cw, bm.getHeight());
} else {
    int ch = (int) (bm.getWidth() / target);
    int top = (bm.getHeight() - ch) / 2;
    srcRect = new Rect(0, top, bm.getWidth(), top + ch);
}
c.drawBitmap(bm, srcRect, new Rect(0, 0, w, ht), paint);
```

- 手机 20:9 / 19.5:9：几乎不裁；
- 21:9：轻裁上下；
- 平板横屏：会裁成中间一条——**需要横屏素材变体**，否则人物构图会被裁掉。
- 工作量：0.5 天。

### 4.2 屏幕旋转 / 自然方向（必做）

传感器坐标是相对**自然方向**的。手机自然竖屏没问题；平板/折叠屏自然横屏时，`(tx,ty)` 需要按 `Display.getRotation()` 旋转到当前屏幕坐标：

```java
int rot = windowManager.getDefaultDisplay().getRotation();
switch (rot) {
    case Surface.ROTATION_0:   /* (tx, ty) */ break;
    case Surface.ROTATION_90:  /* (-ty, tx) 或 (ty,-tx)，需上机确认 */ break;
    case Surface.ROTATION_180: /* (-tx, -ty) */ break;
    case Surface.ROTATION_270: /* (ty, -tx) */ break;
}
```

- 手机竖屏（ROTATION_0）不受影响；
- 平板/折叠屏必须做，否则左右/上下方向会整体转 90°；
- 工作量：0.5–1 天（含上机确认符号）。

### 4.3 渲染后端（建议做，中风险）

当前 Canvas 路径的兼容性隐患：

- `lockHardwareCanvas()` 在部分设备返回 null/抛异常 → 回退软件画布；
- `saveLayer` + `DST_IN` 在全屏 1080p 上，低端 GPU 可能 10–30ms/帧；
- 软件画布下 1080p 的 `saveLayer` 更慢。

**建议把壁纸渲染改成 EGL + GLES2**：

- 复用现有 `LenticularView` 的 shader（单条渐变 + 2D 方向），只需把 `GLSurfaceView` 换成 `WallpaperService + EGLSurface`；
- 不依赖 `lockHardwareCanvas` / `PorterDuff`；
- Mali/Adreno/PowerVR 对 GLES2 支持普遍；
- 代价：EGL 初始化/线程/生命周期代码约 300–500 行；工作量 2–4 天；
- 低端机可再降 720p 纹理 + 30fps。

如果暂时不改 GL，至少保留 Canvas 回退并加 **帧耗时检测**：`avgMs > 20` 时降到 30fps / 半分辨率。

### 4.4 传感器可用性（必做，低风险）

当前已经按 `GAME_ROTATION_VECTOR → ROTATION_VECTOR → ACCELEROMETER` 回退，但需要：

- 在调试行显示实际使用的传感器类型；
- 无陀螺仪设备（加速度计）上加更强的滤波/死区，避免方向抖动；
- 磁力计版 `ROTATION_VECTOR` 在金属/磁干扰环境可能漂移，建议优先 `GAME_ROTATION_VECTOR`；
- 某些设备的传感器安装方向异常（OEM 校准问题），提供“方向反转/旋转”调试开关；
- 工作量：0.5 天。

### 4.5 内存与低端机（建议做）

- 每组 3 张 1080p ARGB ≈ 31 MB；2 GB 设备容易被回收；
- 方案：
  - `ActivityManager.isLowRamDevice()` 时加载 **720×1600** 变体；
  - 或 `BitmapFactory.Options.inPreferredConfig = RGB_565`（内存减半，渐变处可能有轻微 banding）；
  - 30fps 上限；
  - 组切换时先加载新组再回收旧组（当前已这样做），峰值约 2 组内存；
- 工作量：0.5–1 天 + 素材变体 1 天。

### 4.6 OEM 壁纸选择器（低风险）

`ACTION_CHANGE_LIVE_WALLPAPER` 在多数设备可用；少数 OEM 不响应时：

- 回退 `Settings.ACTION_WALLPAPER_SETTINGS`；
- 或提示用户手动路径（设置 → 壁纸 → 动态壁纸）；
- 工作量：0.5 天。

### 4.7 锁屏（低可行，不建议）

- EMUI/MIUI/One UI/ColorOS 的锁屏壁纸基本独立于桌面动态壁纸；
- 官方“光栅锁屏”（vivo）是系统级功能，第三方无法通过壁纸通道复刻；
- 如果一定要锁屏，需要按 OEM 做主题/锁屏插件/系统级注入，成本高、版本绑定；
- 结论：**对外只承诺桌面壁纸**；锁屏作为“部分设备可能显示”的附赠，不做兼容承诺。

### 4.8 安全与分发

- `WallDebugReceiver` 目前 `exported=true`，任何 App 都能改组/调试值；分发前建议：
  - 改为 `exported=false` + 仅调试构建启用；
  - 或加 `signature` 级自定义权限；
- 素材来自《绝区零》官方「绳网情报站」Wiki，版权归米哈游；**公开仓库/上架有 IP 风险**，不要把它当作可再分发的素材；
- 若上架 Play Store：targetSdk 34+、隐私政策（传感器仅壁纸可见时使用）、无数据收集。

---

## 5. 改造清单与工作量

| 项 | 优先级 | 工作量 | 说明 |
|---|---|---|---|
| aspect-fill 裁切 | P0 | 0.5 天 | 消除拉伸变形 |
| display rotation 补偿 | P0 | 0.5–1 天 | 平板/折叠屏方向 |
| minSdk 降到 23 + `clear()` 兼容 | P1 | 0.5 天 | Android 6–9 |
| 传感器类型显示/滤波加强 | P1 | 0.5 天 | 无陀螺仪设备 |
| 720p/RGB_565/30fps 低端兜底 | P1 | 1–1.5 天 | 2 GB 设备 |
| 横屏素材变体 | P1 | 1–2 天 | 平板/折叠屏构图 |
| EGL + GLES2 壁纸渲染 | P2 | 2–4 天 | 替代 Canvas/DST_IN |
| OEM 选择器回退 | P2 | 0.5 天 | 少数设备 |
| 测试矩阵 | P0 | 3–5 天 | 见下 |

**总计：约 6–10 个工作日**（桌面壁纸，不含锁屏 OEM 适配）。

---

## 6. 推荐路线

### 路线 A：只覆盖“其他手机桌面”（推荐）

1. aspect-fill；
2. display rotation 补偿；
3. minSdk 23；
4. 传感器类型显示 + 加速度计滤波；
5. 低端 720p/30fps；
6. 3–5 台真机测试。

**可行性：高。** 不碰系统、不碰锁屏，风险和成本都低。

### 路线 B：手机 + 平板/折叠屏

在 A 基础上加：

- 横屏素材变体（原图本来就是横屏，直接不旋转、按横屏 fit）；
- 动态 surface 尺寸/旋转处理；
- 折叠屏展开/折叠切换测试。

**可行性：中。** 主要是素材和构图问题。

### 路线 C：锁屏

不建议按“壁纸”方案承诺。若必须做，需要按 OEM 单独评估（主题/锁屏插件/系统注入），成本和风险都远高于桌面壁纸。

---

## 7. 测试矩阵（建议）

| 维度 | 样本 |
|---|---|
| Android 版本 | 6 / 9 / 10 / 12 / 14 |
| 传感器 | 有陀螺仪 / 无陀螺仪 / 只有加速度计 |
| 屏幕 | 1080×2400、1080×2340、1440×3200、平板 2560×1600、折叠屏 |
| OEM | EMUI、MIUI/HyperOS、One UI、ColorOS、原生/AOSP |
| 场景 | 桌面、应用抽屉、锁屏、分屏、折叠展开、低电量模式 |
| 性能 | 30 分钟壁纸常驻，观察内存/掉帧/发热 |

---

## 8. 结论

- **桌面动态壁纸：高可行**。核心 API 都是标准的，主要工作是比例、方向、传感器兜底和渲染后端。
- **锁屏：低可行**。不是技术难点，而是 OEM 锁屏壁纸体系不开放；不要对外承诺。
- **平板/折叠屏：中可行**。需要横屏素材和 rotation 补偿。
- **低端/无陀螺仪：中高可行**。加速度计兜底 + 降分辨率/帧率。
- 当前代码已经具备“可移植”的骨架（标准 `WallpaperService`、传感器回退、多组素材、调试通道），缺的是上面 P0/P1 的适配层。

## 9. 已落地（2026-10-31）

| 项 | 状态 |
|---|---|
| minSdk / targetSdk | **28 / 34**（Android 9+） |
| 平板/横屏 | `onSurfaceChanged` 判断宽高比，竖屏 `groups/`、横屏 `groups_land/`；aspect-fill 裁切；`Display.getRotation()` 方向补偿 |
| 自定义槽位 | 1–6 个槽位；选图 / 旋转 90° / ↑↓ 排序 / 删除 / 使用此组；master + 1600px display |
| App 图标 | 由 `IMG_0310.JPG` 生成 mdpi–xxxhdpi + round |
| UI | 深色卡片、图标头图、分组 Spinner、灵敏度 chips、预览 chips |
| 实机验证 | 自定义组壁纸（`group=custom count=2`）、编辑器旋转/排序、横屏预览（`LENTI landscape=true, surface 2292×1080`）、壁纸横屏日志（`WALL landscape=true, surface 2160×1080`） |
| 待补 | 真实平板冒烟测试；编辑器“选图/添加/删除”的文件选择器交互 |

### 平板放置方案（回答“先识别分辨率吗”）

不是只看分辨率数值，而是：

1. `onSurfaceChanged` 拿 `width/height`，判断 `landscape = width > height`；
2. 竖屏加载 1080×2400 旋转素材，横屏加载 1920×1080 原图素材；
3. 绘制时按目标宽高比 **aspect-fill** 中心裁切，不拉伸；
4. 传感器方向按 `Display.getRotation()` 旋转到屏幕坐标；
5. 自定义图片存 master + 1600px display，绘制时同样 aspect-fill，槽位可手动旋转 90°。

这样同一套代码覆盖 20:9 / 19.5:9 / 21:9 / 16:10 平板 / 折叠屏，不需要按分辨率硬编码。
