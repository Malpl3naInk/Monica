# Credential Exchange 验证记录

日期：2026-09-16。范围：Android 的应用凭据交换、ZIP / CSV 目标导入，以及导入页交互。

## 环境与证据

- 公共 AVD：`Monica_Issue136_API_32`，Android 12L / API 32，x86_64；复用原数据盘，没有清空应用数据。
- Google Play services：`26.33.32 (190800-974685114)`。
- JDK 17；使用现有隔离构建目录，生成 arm64-v8a、armeabi-v7a、x86_64 Debug APK。
- 数据和账号均为合成测试夹具。测试结束只清理各自创建的记录、数据库、附件和密钥引用。
- 详细日志与原生截图保存在工作区 `.codex-tasks/credential-exchange-import-20260915/`。

## 检查范围

| 流程 | 检查内容 |
| --- | --- |
| CXF → 本地、KDBX、MDBX、Bitwarden | 正确归属、实际写入、密码字符保真、原凭据 ID / RP / user handle、私钥独立验签、重复导入不覆盖 |
| 原有 Passkey | 旧非零计数和私钥保持不变；CXF 导出排除非零计数，报告跳过数量 |
| CSV → 四种目标 | Chrome、KeePass、Bitwarden、Proton、密码键盘、Monica 共六种格式；UTF-8 BOM、引号、逗号、分号、冒号、前后空格、换行；损坏行报告；未闭合引号不生成错误密码 |
| ZIP → 四种目标 | 错误解密密码拒绝；只导入数据；不恢复旧分类、数据库或 Bitwarden 绑定 |
| ZIP 关联和附件 | 密码、Passkey、TOTP 和笔记关联到新记录 ID；密码及笔记附件字节保真；重复导入不重复 |
| 原生数据库去重 | KDBX / Rust MDBX 文件存在但 Room 索引未加载时不复制凭据 |
| 相似记录 | 同名密码的不同自定义字段保留；重复较早或较晚版本都能正确识别；同一文件内重复记录不复制字段 |
| 异常目标 | 已删除、锁定、只读目标拒绝；取消操作不退回写入本地；KDBX 落盘失败不算成功 |
| Bitwarden Cipher 上传 | HTTP 503 后保留待上传状态；重试生成标准 Login / FIDO2 字段；解密私钥后验签；确认后不重复上传 |
| Bitwarden 附件上传 | 密码、笔记父记录均成功上传；HTTP 503 后保留本地加密附件；重试实际 HTTP 上传两类附件；独立 JCE 验 MAC 并解密，核对原始字节和父记录；确认后清空持久队列，不重复上传 |
| 导出接收方校验 | 注册 secret、安装包签名、content URI 所有权、跨 UID 写授权及请求类型；撤销授权后拒绝继续导出 |
| Android 应用 scope | 证书约束跨库保留；签名不匹配时不降级为无约束应用绑定 |
| AndroidX 升级回归 | Chrome 的 HTTPS origin 与原生应用签名 hash origin |
| UI | 选择目标 → CSV 格式 → 文件结果 → 导入 → 结果留在本页；英文和波兰语 1.5 倍字体的目标选择及底部操作可达 |
| 系统来源选择入口 | 补充互操作检查已通过 Google Play 服务的真实选择器接收独立测试应用的合成密码和 Passkey，确认前不写库；第三方真实账号验收仍未完成，详见下方补充记录 |

KDBX 和 MDBX 检查读取真实落盘内容；Bitwarden 检查调用实际加密与 HTTP 上传代码，但服务器为 MockWebServer。

## 结果

核心 JVM 定向测试 28 项通过，覆盖 CXF 解析、PKCS#8 密钥材料、目标归属映射、一次性导出授权和 CSV 字符保真。它们使用实际生产源码，不是另写一套模拟实现。

设备回归 **19 个不同场景全部通过**：数据导入 11 项、ZIP 关联和附件 2 项、传输安全与兼容 3 项、导入 UI 3 项。统计取 `device-regression-4.log` 至 `device-regression-7.log` 中每个场景最后一次结果，详见 `device-final-summary.json`；发现失败后仅重跑受影响场景，未把重复运行累计为更多用例。这几轮使用同一生产 APK。

`device-build-3.log` 记录主应用和测试 APK 构建成功；最终测试 APK 构建见 `test-build-7.log`。arm64-v8a、armeabi-v7a、x86_64 Debug APK 均已生成。交付的 arm64 包通过 APK v2 签名校验；设备测试运行于同次构建的 x86_64 包，尚未在 arm64 真机执行。

凭据交换相关修改的 `git diff --check` 通过，见 `feature-diff-check.log`。全工作树检查仅另报既有 `LocalKeePassScreen.kt:1029` 的尾部空格，未改动该文件。

本轮导入回归发现并修正的问题：

- ZIP 笔记、卡片及 TOTP 读取保留来源 ID，仅用于重建附件和记录关联；落库仍分配新 ID。
- 去重将自定义字段纳入候选筛选，避免第一条同名记录掩盖真正的重复项。
- ZIP 导入前规范化 TOTP 的可选字段与默认值，避免再次导入时误判为新记录；附件找不到父记录会报告失败，不能静默丢失。
- KDBX 纯 Passkey 条目的用户名和 URL 不再被导出为额外空密码；含实际密码的混合条目仍可导出密码。
- 文件选择和底部操作统一为 M3E Canvas 草图中的圆角样式，说明文字放入滚动内容，避免挤占操作区。

最后一次笔记附件失败来自测试样本：样本把正文直接放在要求结构化数据的 `itemData` 字段，生产同步代码因格式无效拒绝上传笔记。样本已改用应用实际的 `NoteContentCodec.encode()`，并增加父记录、笔记正文及两份附件的服务器载荷断言；未为通过测试放宽生产数据校验。最后两项附件回归均通过，日志见 `device-regression-7.log`。

已检查实际截图中的导入结果、文件选择、系统入口返回页及波兰语大字体布局；主操作可达，结果留在本页。截图保存在任务目录 `ui-final/`，可编辑设计见 `DESIGN.md`。

交付包：`apk/Monica-Android-arm64-v8a-1.0.311-26091612-16.APK`（81.94 MiB）。SHA-256：

```text
057C66051B4FC2469AE407414E376B1122178DB5C587A61CB3115AC358C9F5ED
```

## 尚未完成的外部验收

- Google 密码管理器、Bitwarden、1Password 的真实账号双向迁移；来源是否出现取决于应用版本和服务端开放情况。
- 真实 Bitwarden / Vaultwarden 服务的跨设备同步、组织策略、附件配额与 Premium 权限组合。
- Android 14 及以上真机 Credential Manager 登录及导入凭据后真实网站登录。
- 系统 DocumentsUI 的完整人工文件选择；现有 UI 回归投递生产文件选择回调，再执行真实导入与数据库检查。

这些边界不能由合成凭据、模拟服务器或私钥验签替代。当前测试结果只证明列明的环境和流程，不表示所有第三方服务、设备或网站均已验收。

## 2026-09-16 互操作修复补充

本次针对用户报告的系统交接失败单独检查，没有重跑或重新累计上面的 19 个数据库与 UI 场景。证据保存在 `.codex-tasks/credential-exchange-interop-20260916/`。

已证实的根因：AndroidX 的无扩展名传输文件由 FileProvider 推断为 `application/octet-stream`，而原导出 Activity 仅声明 `content` scheme。旧 Debug 18 在 Google 的真实选择器中可见，但选中后 Google 进程报 `ActivityNotFoundException`；使用 SDK 实际 URI 的回归同样无法解析到导出 Activity。补充 MIME 过滤器后，该回归在 Debug 19 通过，独立应用选中 Monica 后能进入包含接收方、来源数据库和“Verify and export”的导出页面。这里只验证到身份验证前的页面，没有向测试应用导出已有保险库数据。

另修正了注册失败静默丢失、导入异常一律显示设备不支持，以及等待 Snackbar 关闭期间按钮仍忙碌的问题。诊断仅记录固定操作、错误分类、异常类名、Android API 和 Google Play services 版本，不记录凭据内容或异常原文。

本次结果：

- `assembleDebug`、`assembleDebugAndroidTest` 和 3 项错误分类／日志保密单测通过，见 `fix-build.log`。
- 2 项设备回归通过：实际 SDK 文件类型的 Activity 解析，以及注册 secret、调用包签名、跨 UID URI 权限和请求类型校验，见 `regression-fixed.log`。
- 独立测试应用通过 Google 的真实选择器交接成功：Monica 展示来源 `CXF Test Peer`、1 条密码、1 个 PKCS#8 Passkey，并断言确认前写库次数为 0，见 `live-fixed.log` 和 `live-received.png`。测试没有替换生产系统入口，也没有确认实际数据库写入。
- 反向系统跳转的页面文本与 Activity 记录见 `fixed-export-entry.xml` 和 `fixed-export-activity.txt`；查看后取消。测试应用收到 SDK 的 `ImportCredentialsUnknownErrorException`，因此不把这一轮取消算作完整导出或成功的取消类型传递。
- 复用公共 API 32 AVD；安装同次构建的 Debug 19 x86_64 主包和测试包，保留原有应用数据。未改动现有 Passkey 签名、私钥或计数处理。测试结束已卸载独立测试应用和 instrumentation 包，并停止本次启动的 AVD，保留配置与数据盘供复用。

AndroidX 注册、Google 运行时预检、独立新测试包在系统选择器中的双向可见性均已观察到。因此当前环境的故障不能归结为“Monica 一律被 Google 包名白名单阻止”，但这些证据不证明所有设备或服务端开放批次都相同。用户手机上的原始导入失败没有在本环境复现；仍需使用新包的错误分类核对，不能把本次合成传输当作 Google／Bitwarden／1Password 真实账号互传验收。

交付包保存在 `.codex-tasks/credential-exchange-interop-20260916/apk/Monica-Android-arm64-v8a-1.0.311-26091612-19.APK`，通过 APK v2 签名校验。SHA-256：

```text
44BDE3AA52DEBA6951D84C682FDF09D18444A75D822A965C2B756E7AE65EF7E9
```

## 2026-09-16 Release 混淆修复补充

用户后续日志显示，混淆后的版本在 `REGISTER` 和 `IMPORT` 两处均报 `PROVIDER_CONFIGURATION`，尚未读取待导入数据。实际 Release 的 Manifest 保留了 SDK 适配类名，但 R8 已将 `ProviderEventsApiProviderPlayServices` 整类删除；因此 `ProviderFactory` 的反射加载失败。此前 Debug 不启用混淆，未覆盖这个差异。

修复在 `app/proguard-rules.pro` 中仅保留该类及公开的 `Context` 构造函数，完整规则见实现文档。没有关闭 Release 压缩，没有更改 Passkey 签名、计数、私钥或目标数据库映射。证据保存在 `.codex-tasks/credential-exchange-release-discovery-20260916/`。

本次验证区分三个层次：

- 实际二进制：`check_provider_dex.py` 检查 DEX 类定义和构造函数签名。修复前完整 Monica Release 的检查失败；修复后完整 Release APK 的检查通过，见 `release-baseline-check.log` 和 `release-after-check.log`。
- 同 SDK 运行对比：独立测试应用使用同样的 Release 混淆规则，在同一公共 AVD 上复现注册失败及 `importCredentialsAsync no provider dependencies found`。只加入保留规则后，注册返回成功，Google 的真实来源选择器正常打开。没有把源码文本匹配当作运行验证。
- 完整应用入口：`:app:assembleRelease` 成功，生成 `1.0.311-26091612-20`。安装同次构建的 x86_64 Release 包后，从系统选择器选中 Monica，可进入显示接收方、来源数据库和身份验证按钮的真实导出页；随后取消，没有导出已有保险库数据。

完整 Release 包的导入页面未完成本轮操作，公共 AVD 的既有 Monica 主密码不可用；本轮没有重置它。上面的同 SDK 运行对比与完整 APK 检查用于锁定这次初始化缺陷，不能替代用户真机或 Google／Bitwarden／1Password 真实账号迁移验收。前述数据库导入回归没有在本轮重复执行或累计。

交付的是启用 R8 和资源压缩、使用既有 Debug 签名的 Release 验证包；不是普通 Debug 构建。路径为 `.codex-tasks/credential-exchange-release-discovery-20260916/apk/Monica-Android-arm64-v8a-1.0.311-26091612-20.APK`，APK v2 签名检查通过，SHA-256：

```text
AF6FF08BCE8C2948597B8699D9D2500EF6C7C77C9E2AD845C7B51F30A76BE44D
```

测试应用已卸载，本轮启动的公共 AVD 已停止，保留原数据盘及 Monica 应用数据。

## 复现入口

设备测试类位于 `app/src/androidTest/java/takagi/ru/monica/credentialexchange/`：

```text
CredentialImportInstrumentedTest
CredentialImportAttachmentsInstrumentedTest
TransferSecurityInstrumentedTest
ImportScreenInstrumentedTest
```

核心测试位于 `app/src/test/java/takagi/ru/monica/credentialexchange/`。依赖、Manifest、官方规范链接与导入 / 导出 API 见 [credential-exchange.md](credential-exchange.md)。
