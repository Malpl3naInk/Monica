# 源码研究与结论边界

2026-09-20。只读检查，不修改参考项目或应用源码。

## Keyguard 版本

旧参考目录 `参考项目/keyguard-app-master` 不是独立 Git 仓库，不能在该目录直接 git pull（会落到外层 Monica 仓库）。本轮通过 GitHub API 查询 `AChep/keyguard-app/commits/master`，得到 `7e124b57189eb87fcff85c8a0b51d4debfa440b1`，与现有独立参考 `.codex-tmp/keyguard-new` 的 HEAD 一致，且该参考工作树干净。因此复用已是该上游版本的代码，没有覆盖旧参考文件。普通 git ls-remote 发生连接重置，API 查询成功。

[上游版本](https://github.com/AChep/keyguard-app/tree/7e124b57189eb87fcff85c8a0b51d4debfa440b1)；此处“当前”仅指本次查询时点，不保证之后上游不更新。

## 真正借鉴的实现

以下路径相对 Keyguard 仓库；行号针对上述提交。

| 源码 | 已读内容 | Monica 设计采用什么 |
|---|---|---|
| `common/src/commonMain/kotlin/com/artemchep/keyguard/feature/home/vault/add/AddScreen.kt:115` | ScaffoldLazyColumn、LargeToolbar、收藏、OptionsButton、Save FAB | 稳定公共骨架，顶栏动作一致；先显示轻量页面 |
| 同文件 `:289` | 按 item.id 构建列表项，AnyField 使用 animateItem，按相邻类型决定 shapeState | 字段稳定身份、局部位置动效、相连字段组 |
| `common/src/commonMain/kotlin/com/artemchep/keyguard/feature/add/AddStateItem.kt` | Title、Username、Password、Totp、Passkey、Attachment 等独立状态项 | 类型专属组件和状态，避免统一大表单强塞所有能力 |
| `common/src/commonMain/kotlin/com/artemchep/keyguard/feature/home/vault/add/AddStateProducer.kt:2283` | typeBasedAddItem 依据可添加类型生成操作 | “添加更多”随类型与能力变化，已无可加项时不显示无用按钮 |
| 同文件 `:2350` 附近 | FieldBakeryScope、稳定 UUID、moveUp/moveDown/delete、移除确认 | 可移动组件有可访问的菜单操作，移除数据有明确语义 |
| `common/src/commonMain/kotlin/com/artemchep/keyguard/feature/add/AddScreenScope.kt:22` | 首次焦点一次性请求，现有实现延迟 100ms | 焦点只请求一次；不照搬固定延迟来掩盖首屏卡顿 |
| `common/src/commonMain/kotlin/com/artemchep/keyguard/feature/home/vault/add/AddStateProducer.kt:255` | 从 currentKoinScope 获取依赖，GPG 有导入/材料协调/校验服务 | 复用解锁会话依赖；密钥解析与视图分离 |

实际源码中明确看到上下移动和删除，不能据此声称 Keyguard 已支持任意整页拖拽。Monica 提案的“整理字段 + 拖动柄”是额外设计，仍需原生可用性验证。也不因 Keyguard 使用 Rust 就将其页面流畅直接归因于 Rust。

官方 Keyguard 运行版之前有许可证确认门槛，用户已选择免费版本或源码对照。本轮只做源码对照，没有启动官方收费流程、没有提供虚构的 Keyguard 延迟数据，也没有构建 Keyguard。

## Monica 现状与必须保留的能力

路径相对 Android 工程。

| 源码 / 既有记录 | 已确认情况 | 对方案的约束 |
|---|---|---|
| `app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt:412` 起 | 多凭据、OTP、网站、个人/地址/支付资料、元数据各自有状态；已有 LazyColumn | 不是简单“把 Column 改 LazyColumn”就结束；必须拆分可观察状态与大 item，按需挂载附加区 |
| 同文件 `:2858` | MultiStorageTargetSelectorCard；默认目标由当前筛选推导 | 归属置顶，承接入口上下文，不能悄悄回落到本地库 |
| 同文件 `:5822` 附近 | 现有 SSO 编辑控制 | V2 不提供新增，但读取/编辑其他字段时保留旧 SSO |
| `data/PasswordEntry.kt:44` 起 | 地址/支付是密码字段；SSO、SSH、Wi-Fi、Passkey 和多个后端身份字段并存 | 隐藏控件不等于删除字段，不能重新从可见表单拼整条记录 |
| `data/model/StorageTarget.kt:6` | Local、KeePass、Bitwarden、MDBX 四种目标，含文件夹和副本语义 | 保留全部目标；能力必须按类型细分 |
| `viewmodel/PasswordViewModel.kt:4784` | 多目标保存按目标执行，次目标失败可能继续；结果有成功 ID 列表 | 新方案必须补全逐目标失败/待同步反馈与重试去重，不能只凭首个 ID 退出 |
| `ui/screens/KeePassNativeEntrySaveCoordinator.kt:14` | 原生更新使用版本 token；主字段与附件可能分阶段完成 | 独立处理原生 UUID/版本与附件结果，不把所有保存想成 Room 事务 |
| `ui/SimpleMainScreen.kt:242` | 卡包统一新建壳包含银行卡、证件、地址，已有收藏回调 | 继续覆盖三种入口，收藏在每类数据支持时有效 |
| `ui/components/EntryTypeChip.kt:42` | PASSWORD/WIFI/SSH_KEY/BARCODE/API_TOKEN/GPG_KEY | 密码库类型切换必须全部覆盖，不能遗漏 GPG/条码/API |
| `viewmodel/NativeApiTokenEditorViewModel.kt:19` | 原生 MDBX Token 独立模型，敏感草稿仅内存 | 不将 API Token 强行转换为 PasswordEntry 或扩大到未支持库 |
| `utils/GpgKeyGenerator.kt:28` | 现有 GPG 使用 Bouncy Castle，RSA 主密钥/子密钥 | 当前算法与导出格式是兼容基线；Rust 替换须独立验证 |
| `docs/new-entry-security-reuse.md` | 已移除重复 SecurityManager 初始化和嵌套转场；仍有长帧 | 保留既有优化，不能承诺单靠重设计达到 Keyguard 性能 |
| `docs/ssh-gpg-android-cli-compatibility.md` | SSH/GPG 的 MDBX 字段、身份、未知内层属性与密钥换行契约 | 沿用格式；已有文档明确未知外层 payload 还不能保证无损，因此 V2 放行前须补覆盖或阻止有损保存 |

本轮没有对全部后端适配器做完整正确性审计；已列证据足以界定设计风险，不足以宣称所有数据库端到端通过。具体放行依照验收矩阵。
