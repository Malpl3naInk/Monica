# Android Credential Exchange

Monica 通过 AndroidX Provider Events 接入 Android Credential Transfer，作为 Importer 接收 CXF，也注册 Exporter 供其他兼容应用选择。导入页可将应用交换、ZIP 和 CSV 数据写入用户选择的数据库。

## 官方依据

- [Android Credential Transfer](https://developer.android.com/identity/sign-in/credential-transfer)
- [Android Credential Provider](https://developer.android.com/identity/sign-in/credential-provider)
- [FIDO CXF 1.0，2025-08-14](https://fidoalliance.org/specs/cx/cxf-v1.0-ps-20250814.html)
- [Bitwarden Android，固定提交 a2586225](https://github.com/bitwarden/android/tree/a2586225f0174274e8b9cc6395fb9ffe9c6f2889/cxf)
- [Bitwarden CXF Passkey，固定提交 22a87d98](https://github.com/bitwarden/credential-exchange/blob/22a87d9852ae64a659ae37774bd9194fd362bdde/credential-exchange-format/src/passkey.rs)

Credential Transfer 通过 Google Play services 支持 Android 8 / API 26 及以上。它与 Android 14 起的第三方 Credential Provider 系统入口不同；仅升级 Android 版本并不能保证设备已获得凭据交换服务。可选来源由 Android 提供，Google 密码管理器、Bitwarden、1Password 是否出现还取决于各应用及服务版本。

## 依赖和 Manifest

`app/build.gradle`：

```groovy
implementation "androidx.credentials:credentials:1.6.0"
implementation "androidx.credentials:credentials-play-services-auth:1.6.0"
implementation "androidx.credentials.providerevents:providerevents:1.0.0-beta01"
implementation "androidx.credentials.providerevents:providerevents-play-services:1.0.0-beta01"
```

`app/src/main/AndroidManifest.xml` 中的导出入口：

```xml
<activity
    android:name=".credentialexchange.CredentialExportActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:theme="@style/Theme.Monica"
    android:windowSoftInputMode="adjustResize">
    <intent-filter>
        <action android:name="androidx.identitycredentials.action.IMPORT_CREDENTIALS" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="content" />
    </intent-filter>
    <intent-filter>
        <action android:name="androidx.identitycredentials.action.IMPORT_CREDENTIALS" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="content" />
        <data android:mimeType="application/octet-stream" />
        <data android:mimeType="application/json" />
    </intent-filter>
</activity>
```

`queries` 同时声明该 action 和 `content` scheme 的无类型查询，以及 `android:mimeType="*/*"` 的有类型查询，便于发现和校验实际处理 SDK 请求的应用。SDK 自带的传输 FileProvider 由依赖的 Manifest 合并提供。现有 `MonicaCredentialProviderService` 保留原有注册和认证职责。

SDK 的临时文件没有扩展名，`FileProvider.getType()` 返回 `application/octet-stream`。Android 启动 Activity 时会推断这个类型，只有 `content` scheme 的过滤器无法匹配。2026-09-16 的官方文档示例未包含 MIME；Bitwarden 实际 Manifest 明确包含 `application/octet-stream`。本实现兼容无类型 URI、SDK 二进制类型与 JSON 类型，传输数据仍须经过相同的身份、URI 授权和 CXF 格式校验。

Release 必须保留 SDK 的反射入口。`ProviderFactory` 从 Manifest metadata 读取适配类名，再通过 `Class.forName()` 和公开的 `Context` 构造函数实例化。`providerevents-play-services:1.0.0-beta01` 没有为它提供 consumer keep rule；仅在 Manifest 中保留服务并不足够，R8 会删除适配类，导致导入和导出注册同时报 `PROVIDER_CONFIGURATION`。`app/proguard-rules.pro` 中因此包含：

```proguard
-keep class androidx.credentials.providerevents.playservices.ProviderEventsApiProviderPlayServices {
    public <init>(android.content.Context);
}
```

此规则仅保护该动态入口，其他代码仍按原 Release 配置压缩和混淆。Debug 不启用 R8，不能代替此项 Release 验证。

## 关键调用与流程

应用导入位于 `ImportDataScreen`：

```kotlin
val response = ProviderEventsManager.create(context).importCredentials(
    activity,
    ImportCredentialsRequest(CredentialExchangeRegistrar.supportedTypes, emptySet()),
)
val source = TransferCallingAppVerifier.verifiedLabel(context, response.callingAppInfo)
    ?: throw SecurityException()
val decoded = CxfCredentialCodec.decode(response.response.responseJson)
// 展示来源和数量，用户确认后：
targetedImporter.importExchange(decoded, destination)
```

`CredentialExchangeRegistrar` 在 Monica 解锁后注册一个稳定的 Monica 入口，入口 ID 是本机生成并存储的随机 secret。入口本身不代表数据库已经解锁。

再次进入导入页会重试注册，注册失败不阻止读取现有保险库或尝试导入。`CredentialExchangeErrors` 区分服务配置错误、没有来源、系统错误、无效数据与身份校验失败；错误提示不再把所有异常都解释为设备不支持。错误显示前恢复按钮状态，注册与导入结果写入已有的安全诊断日志，仅包含固定分类、异常类名、Android API 和 Google Play services 版本号。不得记录异常原文、请求、URI 内容、注册 secret 或 CXF 数据。

`CredentialExportActivity` 通过 `IntentHandler.retrieveProviderImportCredentialsRequest()` 读取请求。校验通过后，用户选择来源库，重新验证主密码或生物识别；没有 Monica 主密码时要求设备锁屏验证。随后显示可导出数量和排除数量，用户再次确认才写入接收方 URI：

```kotlin
val result = Intent()
IntentHandler.setImportCredentialsResponse(
    context, incoming.uri, result, ImportCredentialsResponse(prepared.json),
)
setResult(Activity.RESULT_OK, result)
```

取消和错误也用 SDK 的 `setImportCredentialsException()` 返回，并按 API 约定使用 `RESULT_OK`；不要用空的 `RESULT_CANCELED` 替代结构化错误。

导出前必须同时满足注册 secret 匹配、调用包签名匹配、目标为调用包所属的 `content://` provider、已获得写 URI 授权，以及请求类型存在交集。验证授权绑定所选数据库，120 秒有效且仅可消费一次；切库、进入后台、取消均使其失效。导出窗口使用 `FLAG_SECURE`，私钥和密码不放入 saved state、日志或结果 Intent extras。

## 存储目标

| 目标 | 密码 | Passkey | 完成含义 |
| --- | --- | --- | --- |
| Monica 本地 | 设备加密存储 | 受保护的可移植私钥引用 | Room 写入完成 |
| KeePass / KDBX | 标准字段与自定义字段 | KeePassDX 兼容字段与 PKCS#8 私钥 | KDBX 实际落盘完成 |
| MDBX | 通过现有 Rust MDBX 仓库写入 | 原凭据 ID 与私钥写入原生文件 | 本地文件及既有工作副本提交完成 |
| Bitwarden | 原生 Login Cipher | `login.fido2Credentials`，仅 ES256 | 先进入持久待同步状态，服务器同步成功后才算上传完成 |

导入使用新本地记录 ID，并清除来源的数据库、云账户、分类及远端 Cipher 绑定。重复匹配限于目标数据库。KDBX / MDBX 还读取实际文件，避免本地索引尚未加载时制造重复条目。KDBX 写入失败会删除尚未提交的新本地投影，不能把它计为导入成功。

ZIP 的目标导入属于数据导入，不覆盖应用设置、连接或分类；完整备份恢复入口保留原本语义。CSV 保留密码、用户名中的空格、分号、冒号、引号、逗号和换行。第三方 CSV / CXF 的密码按明文处理，不把 `MDK|`、`V2|`、`C2|` 前缀误识别为本机密文。

原生 KDBX 文件的“打开”仍是添加独立数据库。ZIP / CSV / 凭据交换中的记录导入才使用目标数据库选择。

## Passkey 规则

- CXF §3.3.12 要求非零签名计数 Passkey 排除在导出之外。Monica 显示排除数量，不修改原计数，不替用户归零旧凭据。
- CXF 导入的计数为 0，保持 0。此实现不批量修改既有 Passkey，不改变它们的认证计数策略；原有加密 ZIP 备份恢复继续保留备份元数据。
- 凭据 ID、RP ID、用户 handle、PKCS#8 私钥均保真。P-256 / ES256、RSA / RS256、Ed25519 / EdDSA 经过解析与密钥一致性检查；Bitwarden 目标暂仅接收 ES256。
- 缺私钥、设备 Keystore 中不可导出的私钥、无效材料及不支持的算法会跳过。
- 当前认证器没有完整接入 CXF 的 PRF / hmac-secret seed，因此带非空 `fido2Extensions` 的 Passkey 整条跳过，不丢弃扩展后导入一个功能不完整的凭据。
- `scope.androidApps` 通过原生自定义字段保留证书信息。带证书约束的关联只有在本机已安装应用签名匹配时才成为自动填充绑定；未安装、未知散列算法或签名不匹配不会降级成无约束绑定。
- Bitwarden 导出只包含确认属于用户且未删除的项目，组织共享或无法确认的项目排除并提示。普通密码导出不会把 SSO 引用当作密码。

升级 AndroidX 后，浏览器来源解析使用官方 privileged apps allowlist。缓存随应用打包，认证时不为读取名单发起网络请求；Chrome HTTPS origin 与原生应用 signing-hash origin 分别验证。

## 设计、语言与验证

导入页先选目标，再选应用或文件；使用连续圆角操作行和固定底部主按钮，结果留在当前页。设计基于 M3E Canvas，可编辑草图及原生截图见任务交付文件。36 条界面文案已覆盖默认英文、中文、文言文、波兰语、德语、西班牙语、法语、日语、韩语、俄语和越南语；猫语沿用中文回退。

测试在公共 `Monica_Issue136_API_32` 模拟器执行，复用原 AVD，不清空原应用数据。测试使用合成账号和凭据，真实调用 Room、KDBX、Rust MDBX、AndroidX URI 传输及 HTTP 上传管线。构建、设备回归结果与尚未完成的外部验收见同目录 `credential-exchange-validation.md`。

模拟服务器和私钥验签不能替代 Google / Bitwarden / 1Password 的真实账号互传，也不能替代 Android 14+ Credential Manager 真机验收。
