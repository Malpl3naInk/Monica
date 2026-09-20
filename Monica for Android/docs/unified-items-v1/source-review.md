# 当前源码核对

检查日期：2026-09-20。以下行号是本轮读取时的位置，其他任务可能继续修改文件。检查生产源码为只读，没有执行真实数据库迁移或多端往返。

| 源码 | 已确认行为 | 对统一项目的影响 |
|---|---|---|
| [PasswordDatabase.kt](../../app/src/main/java/takagi/ru/monica/data/PasswordDatabase.kt) :49、:2353 | Room version 78；builder 注册正向迁移并启用多实例通知，未见降级迁移/破坏性回退 | 不能提高 schema 后宣称旧版照常开库；IME 跨进程也要接入新数据失效机制 |
| [Converters.kt](../../app/src/main/java/takagi/ru/monica/data/Converters.kt) :27 | ItemType.valueOf 直接解析 | 在旧表写新枚举会有旧版读取异常风险 |
| [PasswordEntry.kt](../../app/src/main/java/takagi/ru/monica/data/PasswordEntry.kt) | 固定密码字段，boundNoteId 单个引用；Passkey/SSH/Wi-Fi 扩展和后端身份并存 | 不是多组件模型；固定字段不能一次表达多篇笔记 |
| [SecureItem.kt](../../app/src/main/java/takagi/ru/monica/data/SecureItem.kt) | 另表存 itemType/itemData；Bitwarden vault/cipher 有唯一约束 | 不应把同 Cipher 的两个笔记复制成两个可写行 |
| [SecureItemModels.kt](../../app/src/main/java/takagi/ru/monica/data/model/SecureItemModels.kt) :357 | NoteData 有 content/tags/isMarkdown/customFields | 保留笔记渲染和类型字段；已知数据类不等于未知 JSON 能往返 |
| [PasskeyEntry.kt](../../app/src/main/java/takagi/ru/monica/data/PasskeyEntry.kt) | 独立 passkeys 表、记录 ID、真实归属、计数与 privateKeyAlias | 不能只移动 UI 绑定就改变安全归属；注释不是所有模式都只存 Keystore 私钥的证明 |
| [CustomField.kt](../../app/src/main/java/takagi/ru/monica/data/CustomField.kt) | 外键仅绑定 PasswordEntry，级联删除；isProtected 描述 UI 隐藏 | 不能假定它是通用加密容器 |
| [CustomFieldRepository.kt](../../app/src/main/java/takagi/ru/monica/repository/CustomFieldRepository.kt) :69 | 保存以有效草稿替换全部字段；此仓库不执行值加密 | 不把新项目私钥原文塞这里；旧全量替换可能删未知字段 |
| [AttachmentOwner.kt](../../app/src/main/java/takagi/ru/monica/attachments/model/AttachmentOwner.kt) | owner 只有 PASSWORD/SECURE_ITEM，数字 ID 必须带类型 | 新组件附件要有项目/组件身份，兼容阶段不直接给旧枚举增加类型 |
| [PasswordViewModel.kt](../../app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt) :242、:4949 | 多凭据生成多个独立 PasswordEntry，再循环多个目标 | 现有批量与同项目多账号本质不同；UI 必须分开说明 |
| [Mdbx2Repository.kt](../../app/src/main/java/takagi/ru/monica/repository/Mdbx2Repository.kt) :1667、:1703、:1731 | 三类 mutation 用 JSONObject() 从已知字段重建 payload | 新增外层扩展会有旧端回写丢失风险，不是 Rust 库自动帮忙保留 |
| [ApiTokenPayload.kt](../../app/src/main/java/takagi/ru/monica/data/ApiTokenPayload.kt) :12、:30 | 原生 Token MAX_BYTES=16 KiB；update 在既有 JsonObject 上替换一项，保留未知键 | Token 局部修改已有基础；不能泛化成所有类型都保留扩展，不能突破其大小契约 |
| [NativeApiTokenExtrasStore.kt](../../app/src/main/java/takagi/ru/monica/repository/NativeApiTokenExtrasStore.kt) :11、[ApiTokenMetadata.kt](../../app/src/main/java/takagi/ru/monica/data/ApiTokenMetadata.kt) :7 | 附加资料已在独立 object label/assignment 中，应用限额 64 KiB；重复副本阻止编辑 | 有可复用基础，不必先把 gateway payload 上限调大；统一所有权与大正文仍需验证 |
| [Mdbx2BatchSupport.kt](../../app/src/main/java/takagi/ru/monica/repository/Mdbx2BatchSupport.kt) :112、:304 | 批次按命令、payload/intent 字节限制，单命令上限 16 MiB、批量 payload 64 MiB | 这些是硬上限，不适合作为日常 JNI 批次目标；不能只数项目个数 |
| [KeePassEntryFieldPatch.kt](../../app/src/main/java/takagi/ru/monica/keepass/KeePassEntryFieldPatch.kt) :12 | 遍历原字段保留未移除字段，再合入 replacementFields | 适合局部补丁，需保留 protected 属性 |
| [KeePassKdbxService.kt](../../app/src/main/java/takagi/ru/monica/utils/KeePassKdbxService.kt) :1851、:1882、:4327 | 原生编辑有 replaceAllFields=true；普通密码使用 field patch | 不能只根据其中一个安全路径就保证所有编辑都无损 |
| [BitwardenApi.kt](../../app/src/main/java/takagi/ru/monica/bitwarden/api/BitwardenApi.kt) :530、:594 | 一个 Cipher type；Login 单 username/password/totp、fido2Credentials 列表 | 多种内容可以在 Monica 聚合，但官方原生类型有明确限制 |
| [CipherUploadProcessor.kt](../../app/src/main/java/takagi/ru/monica/bitwarden/service/CipherUploadProcessor.kt) :1419 | secure-item 上传中按字段 key 合并基准字段，部分卡片字段明确移除 | 有保留未知字段的基础，仍需区分上传路径与保留范围 |
| [PasskeyBackupPortabilityPolicy.kt](../../app/src/main/java/takagi/ru/monica/passkey/PasskeyBackupPortabilityPolicy.kt) | 可移植私钥备份要求整库加密，恢复可用材料或 REFERENCE | 组织方式变更不能弱化现有私钥导出限制 |
| [MonicaExpandableCard.kt](../../app/src/main/java/takagi/ru/monica/ui/components/MonicaExpandableCard.kt) | 已有公共展开内容与圆角反馈 | 新页面应接入这些真实动效组件，不重新造静态卡片 |

Keyguard 继续沿用上一版已读取的参考 `7e124b57189eb87fcff85c8a0b51d4debfa440b1`，本轮重新读取 AddScreen 的 item.id/animateItem/shapeState 与 AddStateProducer 的局部 move/delete 操作。这些支持采用稳定组件 ID 和局部追加；没有证据表明 Keyguard 已实现本方案的任意混合项目。

上一版 [源码研究](../new-entry-v2/source-review.md) 和 [SSH/GPG 格式记录](../ssh-gpg-android-cli-compatibility.md) 作为背景资料；本轮不声称拉取了最新 Keyguard，也不将背景性能结果移作本方案测试结果。

## MDBX 与 CLI 限制复核

补充只读核对了工作区实际独立仓库（没有修改这些源码）：

- [CLI vault.rs](../../../../monica-pass-cli/src/vault.rs) :35、:43、:269：gateway 的 16 KiB 读取上限，以及 StoredCredential 的 deny_unknown_fields；用于真实反序列化路径。16 KiB 以内新增未知字段也会被拒绝。
- [MDBX object_disclosure.rs](../../../../mdbx/crates/mdbx-storage/src/object_disclosure.rs) :15、:18：通用对象读取默认 8 MiB，硬上限 64 MiB，并允许调用方传更小预算。
- [MDBX operation_coordinator.rs](../../../../mdbx/crates/mdbx-storage/src/repo/operation_coordinator.rs) :20、:21：单命令写入默认 1 MiB，硬上限 16 MiB。读取上限不等于可无限写入。
- CLI 的 Cargo.toml 引用 ../mdbx/crates；Android mdbx-engine 使用已打包的 JNA/UniFFI 桥接。上述 Rust 源码用于区分限制所在层，未验证 APK 二进制与该源码完全同版。

因此「MDBX 引擎只能存 16 KiB」不成立。准确说法是当前 Token 通道采取更小、严格的协议与资源边界；本任务先利用既有扩展能力评估，无须据此直接改引擎。

## 已证实、推断、待验证

- **已证实**：Room 78、旧类型直接 valueOf、当前实体分表、批量创建多个项目、Android/CLI Token 16 KiB、CLI 严格字段、已有独立附加资料、MDBX 已知字段重建、KDBX 全字段编辑入口、Bitwarden 原生主类型及有限合并逻辑。
- **风险推断**：直接新增枚举/外层 JSON 后降级读取或回写会失败或丢字段；巨大项目会增加解密/跨 JNI/同步成本。具体受影响的历史版本尚未逐一复现。
- **待验证**：本地加密扩展与 Room 的崩溃一致性、目标兼容旧版号、各 KDBX 客户端/历史恢复是否保留扩展、Bitwarden/自托管的字段容量及版本检查、APK 内的 MDBX 引擎能力、混合组件端到端与 Rust 收益。

本轮不审计所有真实用户数据库，不测试真实账号，不改变 Passkey 计数，不提交或发布新格式。并存的其他任务工作区改动未纳入本方案。
