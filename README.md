# 抖音解析图库（Douyin Gallery）

[![Android CI](https://github.com/zhexianqaq/douyin-gallery/actions/workflows/android.yml/badge.svg)](https://github.com/zhexianqaq/douyin-gallery/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/zhexianqaq/douyin-gallery?display_name=tag&sort=semver)](https://github.com/zhexianqaq/douyin-gallery/releases/latest)

「抖音解析」APK 的配套私有图库。按 **UP主 → 作品 → 文件** 浏览 `/storage/emulated/0/抖音解析/` 下的图片和视频，**全程不联网、不上传，也不让这些文件重新出现在系统相册里**。

- 包名：`com.zhexian.douyingallery`
- App 名：抖音解析图库
- 版本：1.0（versionCode 1）
- minSdk 24 / targetSdk 35 / compileSdk 35
- 技术栈：Kotlin 2.0 + Jetpack Compose (Material 3) + Room + Paging 3 + Coil 3 + Telephoto + Media3

---

## 1. 构建

前置：JDK 17+、Android SDK Platform 35 + Build-Tools 35。

```bash
# 首次构建（会在 ~/.gradle 下载依赖）
./gradlew :app:assembleDebug          # 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # 正式包：app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest      # 单元测试（PathParser，21 个用例）
```

Android Studio 直接用 **Open** 打开本目录即可（Gradle Kotlin DSL，单模块 `:app`）。

> 如果本机 `local.properties` 缺失，Android Studio 会自动生成；命令行构建也可以用 `ANDROID_HOME` 环境变量指定 SDK。
>
> 内存较小的机器可以在 `gradle.properties` 里把 `org.gradle.jvmargs` 调小（默认 `-Xmx3g`）；
> 反之 R8 打 release 包时若内存吃紧，调大 `-Xmx` 并加 `--max-workers=1`。

## 2. 签名

密钥与口令**不进仓库**（`.gitignore` 已排除 `*.jks` / `local.properties`）。构建时按以下顺序读取：

1. 环境变量 `ANDROID_KEYSTORE_PATH` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`（CI 用）
2. `local.properties` 里的 `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`（本机用）
3. 默认 `app/douyingallery.jks`

三者都没命中时 **release 只产未签名 APK**（AGP 会命名为 `app-release-unsigned.apk`），构建不会失败，方便没密钥的机器/CI 做冒烟验证。

本机第一次构建：把自己的 keystore 放好，然后在 `local.properties` 里写：

```properties
RELEASE_STORE_FILE=douyingallery.jks
RELEASE_STORE_PASSWORD=******
RELEASE_KEY_ALIAS=douyingallery
RELEASE_KEY_PASSWORD=******
```

新建 keystore：

```bash
keytool -genkeypair -v -keystore app/douyingallery.jks -alias douyingallery \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=DouyinGallery, O=Zhexian, C=CN"
```

- **以后升级必须用同一个 keystore**，否则不能覆盖安装；请自行备份 keystore 与口令。
- 换签名：替换 `local.properties` 里的四项即可。
- 证书 SHA-256 指纹用 `apksigner verify --print-certs app-release.apk` 查看。

## 2.1 CI 自动构建（GitHub Actions）

工作流：`.github/workflows/android.yml`，触发条件为 push 到 `main`、PR、`v*` 标签、手动 `workflow_dispatch`。

| Job | 做什么 |
|---|---|
| `wrapper` | `gradle/actions/wrapper-validation` 校验 `gradle-wrapper.jar` 未被篡改 |
| `build` | JDK 17 + Android SDK 35 → 跑 21 个单元测试 → `assembleDebug` + `assembleRelease` → 校验签名并用 `aapt2` 打印包名/权限 → 上传 APK 与测试报告 |
| `release` | 打 `v*` 标签时，下载产物、重命名、创建 GitHub Release 并挂上 APK |

**要在 CI 里出签名包**，在仓库 `Settings → Secrets and variables → Actions` 加 4 个 Secret：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 app/douyingallery.jks` 的输出 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 口令 |
| `ANDROID_KEY_ALIAS` | key 别名 |
| `ANDROID_KEY_PASSWORD` | key 口令 |

```bash
# 生成 base64（复制输出粘贴到 Secret）
base64 -w0 app/douyingallery.jks
```

不配这 4 个 Secret 也能跑，只是产物是未签名的 release 包。

**发版流程**：

```bash
git tag v1.0.1 && git push origin v1.0.1
```

CI 会自动建 Release 并上传 `douyin-gallery-v1.0.1-release.apk` / `-debug.apk`。
发版前记得改 `app/build.gradle.kts` 里的 `versionCode` / `versionName`（versionCode 必须递增）。

依赖自动升级：`.github/dependabot.yml` 每周查 Gradle 依赖（androidx / kotlin / coil 分组）、每月查 Actions 版本。

## 2.2 首次上传到 GitHub

仓库还没建时，用自带脚本一步到位（建仓 + 推 main + 推标签）：

```bash
GH_TOKEN=ghp_xxx ./tools/push-to-github.sh                  # 公开仓库 douyin-gallery
GH_TOKEN=ghp_xxx ./tools/push-to-github.sh my-name --private  # 私有仓库、自定义名字
```

Token 在 [Settings → Developer settings → Personal access tokens (classic)](https://github.com/settings/tokens) 新建，勾 **repo** + **workflow**（少了 workflow 会拒绝推送 `.github/workflows/`）。
推送用的 token 只出现在这一次的 URL 里，不会写进 `.git/config`；用完建议立刻删除。

也可以手动来：

```bash
git remote add origin https://github.com/<你>/douyin-gallery.git
git push -u origin main --tags
```

推完去 `Actions` 页看第一次构建，`v*` 标签会自动产出 Release。

## 3. 权限

| 系统版本 | 需要什么 | 代码位置 |
|---|---|---|
| Android 11+ (API 30+) | `MANAGE_EXTERNAL_STORAGE`（所有文件访问） | `StoragePermission.hasAccess()` → `Environment.isExternalStorageManager()` |
| Android 10 及以下 | `READ/WRITE_EXTERNAL_STORAGE` 运行时权限 + `requestLegacyExternalStorage` | 同上 |
| 全版本 | `USE_BIOMETRIC`（仅应用锁用到） | — |

**为什么要「所有文件访问」**：`抖音解析` 每一层目录都放了 `.nomedia`，系统媒体库（MediaStore）不会收录这些目录，只能自己扫文件系统。

**关于上架**：这个权限基本过不了应用商店审核，但这是自用侧载 App，没有影响。

**隐私**：Manifest 里**没有 `INTERNET` 权限**，APK 无法联网；依赖（Coil）带进来的
`ACCESS_NETWORK_STATE` 也已经用 `tools:node="remove"` 移除了。
只剩 `USE_FINGERPRINT` / `USE_BIOMETRIC`（应用锁用）。

授权流程：启动 → 检查权限 → 未授权显示引导页 → 按钮跳
`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`（vivo 等机型不支持时自动回退到
`ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`，再回退到应用详情页）→ 回到前台自动重新检查。

## 4. 怎么用

1. 首次启动授予「所有文件访问」；
2. 首页两个 tab：
   - **UP主**：UP主卡片网格（封面、作品数、文件数、最近更新）→ UP主页（作品列表）→ 作品页（文件网格）
   - **全部**：所有文件按修改时间倒序，按「今天 / 昨天 / 日期」分组
3. 顶栏：排序、搜索、设置。作品页长按进入多选（全选 / 分享 / 导出 / 收藏 / 删除）。
4. 查看器：左右滑动，单击隐藏/显示顶栏底栏，双击放大；视频支持拖动、倍速（0.5/1/1.25/1.5/2）、循环、静音、横屏全屏，并记住播放位置。
5. 设置里可以改根目录、网格列数、默认循环/静音、重建索引、清除缩略图缓存、看存储统计、开应用锁。

### 数据源支持

| 结构 | 例子 | 作品名 |
|---|---|---|
| 抖音（v1.4 起） | `<UP主>/<简介前15字>/<角色>-001.jpg` | 二级目录名 |
| 微信公众号 | `<公众号名>/<标题前15字>/<角色>-001.jpg` | 同上 |
| 旧版（v4.6.0 及更早） | `<作者>/视频/xxx.mp4`、`<作者>/图片/xxx.jpg` | 固定「旧版下载」 |

支持的格式：图片 jpg/jpeg/png/webp/gif/heic/heif/avif，视频 mp4/mov/m4v/webm/mkv/3gp。
忽略：`.nomedia`、`.` 开头文件、`meta.json`、0 字节、`.tmp`/`.part`、不支持的扩展名。

### 隐藏性怎么保证

1. 从不删 `.nomedia`（唯一例外：删除文件后目录里只剩 `.nomedia` 时，连同空目录一起清理）；
2. 缩略图缓存在 App 私有目录 `cacheDir/thumbs`，MediaStore 扫不到；
3. 「导出到相册」是显式复制到 `Pictures/抖音解析导出`（视频到 `Movies/抖音解析导出`），原文件保持隐藏、不动；
4. 根目录永远不删。

## 5. 测试数据

`tools/make_test_data.py` 会生成三种结构 + 边界数据的样例（含 1080×20000 超长图、GIF、真实 mp4 需要本机有 ffmpeg）：

```bash
pip install pillow
python3 tools/make_test_data.py --push           # 生成并 adb push 到 /sdcard/抖音解析_test
python3 tools/make_test_data.py --push --stress 10000   # 再额外造 1 万个文件压测
./tools/make_test_data.sh --push                # 等价写法
```

推完后把图库设置里的**根目录**改成 `/storage/emulated/0/抖音解析_test` 即可浏览。
（测试目录同样每层都有 `.nomedia`，所以系统相册看不到——这本身就是一条验收点。）

## 6. 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

`app/src/test/kotlin/.../PathParserTest.kt` 覆盖：普通编号、四位序号、角色名自带 `-`、
`角色-正文` / `角色-正文-2`、旧版结构、不匹配文件名、隐藏文件 / `.nomedia` / `meta.json` / 0 字节 /
`.tmp` / `.part` 排除、emoji 与生僻字（按码点）、扩展名大小写。

增量扫描（新增文件、删除文件、删除整个作品目录、新增 UP主）依赖真实文件系统与 Room，
在真机上验证（见技术方案 §9），仓库里没有做纯 JVM 化的 instrumentation 测试。

## 7. 代码结构

```
app/src/main/kotlin/com/zhexian/douyingallery
├─ App.kt                 Application + 全局 ImageLoader + Graph（手写 DI）
├─ MainActivity.kt        权限门禁、应用锁、深链入口
├─ data/
│  ├─ storage/            StorageSource / FileSource（SAF 预留同一接口）
│  ├─ scan/               PathParser（纯 Kotlin、可测）、Scanner、MetaReader、MediaAddedReceiver
│  ├─ db/                 MediaEntity / WorkEntity / WorkFts + DAO + AppDatabase
│  ├─ repo/               MediaRepository（唯一数据入口）
│  └─ prefs/              SettingsStore（DataStore）
├─ domain/                模型、筛选/排序枚举、ViewerScope、MediaSorter
├─ ui/
│  ├─ permission/ home/ creator/ work/ viewer/ search/ settings/ common/
│  └─ GalleryNavHost.kt   路由（含 viewer 参数编解码）
└─ util/                  FileOps（分享/导出/删除）、Format、MetadataEnricher、PlayerHolder、StoragePermission、BiometricLock
```

扫描策略（对应技术方案 §5.4）：

- **全量**：只遍历三层，500 条一批 `upsert`，扫完 `DELETE WHERE scanGen < :gen` 清掉已删除的文件，再重算 work 汇总与 FTS；
- **增量**：回到前台 / 下拉刷新时只 `stat` 每个作品目录，`dirMtime` 变了的才重新列文件；目录消失则删记录；
- **懒补全**：宽高在缩略图加载成功时回写，视频时长在打开播放器时用 `MediaMetadataRetriever` 补（并发 2）；
- 全部在 `Dispatchers.IO`，进度通过 `StateFlow<ScanProgress>` 上报，扫描期间已扫到的部分立即可见。

## 8. 和「抖音解析」APK 的联动（可选）

1. **下载完成刷新**（已实现接收端）：下载器 `SaveBridge.finish()` 成功后发
   ```java
   Intent i = new Intent("com.zhexian.douyingallery.ACTION_MEDIA_ADDED");
   i.setPackage("com.zhexian.douyingallery");
   i.putExtra("path", file.getAbsolutePath());
   context.sendBroadcast(i);
   ```
   图库收到后只做单文件 upsert（`MediaAddedReceiver` → `Scanner.indexFile`）。
2. **`meta.json`**（已实现读取）：作品目录下放
   ```json
   {"v":1,"source":"douyin","author":"...","desc":"完整简介含话题",
    "tags":["话题1","话题2"],"url":"https://v.douyin.com/xxx/","downloadedAt":1731700000000}
   ```
   有就填进 `WorkEntity`（完整简介、话题、原始链接），搜索也会命中；没有就忽略。写入端需要改下载器的 `NameLogic.reserve()`，属于下载器侧改动。
3. **深链**（已实现）：`douyingallery://work?path=/storage/emulated/0/抖音解析/UP/作品` 直接跳到作品页。
4. 「打开图库」按钮需要在下载器网页里加 bridge 调用，属于下载器侧改动，本次未包含。

## 9. 已知限制 / 未做

| 项 | 说明 |
|---|---|
| SAF 备选模式（技术方案 §4.3） | **未实现**。`StorageSource` 接口已抽好，加一个 `SafSource`（用 `DocumentsContract.buildChildDocumentsUriUsingTree` + `query` 批量查）即可接入，属第三期可选项。 |
| 视频缩略图取「第 1 秒」 | coil3 **3.0.4** 只提供 `videoFrameIndex`（没有 `videoFrameMillis`），当前取关键帧（默认首帧）。升级 coil3 后可换成按毫秒取帧。 |
| 「全部」页查看器的滑动范围 | 分页列表最多取最近 3000 条喂给查看器（`timelineList(limit = 3000)`），超出部分仍可在网格里点开，但不会连续左右滑。 |
| 动图在网格里 | 使用 `repeatCount(1)`（播放一次后停住），没有完全冻结首帧——coil3 3.0.4 没有单请求关闭动画的开关。 |
| `.trash` 回收站 | 未实现（可选）。删除是直接删，有二次确认。 |
| 应用商店 | 因「所有文件访问」权限不适合上架，仅侧载自用。 |

## 10. 真机验证清单（技术方案 §9）

- [ ] Android 10 / 11 / 13 / 14 各一台：授权、拒绝、再次进入设置授权
- [ ] vivo 机型：`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 不可用时的两级回退
- [ ] 三种目录结构（含旧版 `视频`/`图片`）全部可见、可打开、可播放
- [ ] 1080×20000 超长图不 OOM，可竖向滚动、双击放大
- [ ] 4K / HEVC 视频播放（不支持时有提示）
- [ ] 1 万文件压测：滚动帧率、首次全量扫描 < 5s、增量 < 300ms
- [ ] 删除后数据库与磁盘一致；作品目录只剩 `.nomedia` 时目录被清理，根目录仍在
- [ ] 导出后系统相册（vivo 相册）能看到 `Pictures/抖音解析导出`，原目录仍然看不到
- [ ] 分享到微信 / QQ 能正常打开
- [ ] 开应用锁后：回到前台超时需指纹，最近任务界面空白，无法截屏
