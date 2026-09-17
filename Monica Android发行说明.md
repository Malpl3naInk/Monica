# Monica for Android 1.0.313

## 中文

### 简要

- 重做去重引擎，支持单库或多库整理、冲突预览、可用 Passkey 去重和完整字段保存。
- 优化 Dock 连续切换，大列表分组与排序接入 Rust。
- 快速初始化新增六套预设、独立 Dock 设置及应用导入入口。
- 新增繁體中文，采用香港常用书面用语。
- 中文语言选项收纳为可展开的长卡片，简体、繁体、猫语和文言文集中选择。
- 修复标记“非自动填充”后仍出现解锁提示的问题。
- 改进工号、学号、分步登录及网页表单的填充，减少漏填和误填。

### 详细

- **去重整理**：来源、目标与冲突策略集中在连接式圆角操作组；数据库和合并明细支持搜索，新增、冲突、跳过分别筛选，写入进度与主操作保持可达。支持将单个或多个来源整理到独立的本地或 MDBX 目标，保留来源与目标已有内容；同账号但内容不同的目标条目另存一份。判重区分 URL 路径、非邮箱账号大小写、密码空格及 OTP 参数，保留自定义字段、附件、照片和卡面，同名不同内容的附件分别保留；再次整理不会重复新增，MDBX 重新打开后内容完整。取消或重新扫描不会执行旧结果，Passkey 按凭据身份和密钥判重，同账号的不同钥匙分别保留；非零计数、不可用或冲突记录会列明跳过原因。批量移动先保存目标再清理来源，删除副本不影响仍在使用的共享私钥。
- **Dock 与列表性能**：切换页面复用已初始化的加密组件，返回密码页复用完全匹配的分组结果；数据或堆叠设置变化时仍及时重算。大列表的归组、评分和稳定排序使用 Rust 批量处理，保留原有卡片顺序与样式，并设定批量上限、结果校验和兼容回退。
- **快速初始化**：新增 Bitwarden 预览／列表、验证器专用、分页管理、日常常用和极简密码六套预设，可先预览 Dock 再应用；支持保留当前布局。流程调整为欢迎、预设、Dock、页面微调、安全与自动填充、接入数据、完成；Dock 复用底栏设置卡片，完整显示已开启及隐藏页面，支持长按拖拽排序；列表、卡片和配色在后续步骤按需展开。返回前一步保留微调，设置入口始终可达，并补齐各语言文案。可直接前往其他应用或文件导入，返回后继续当前初始化步骤。
- **香港繁体中文**：补齐设置、数据库、备份、Passkey、自动填充等页面的繁体文案，统一使用「設定」「檔案」「數據庫」「電郵」等香港常用表达。
- **语言选择**：中文与其他语言保持一致的长卡片样式，最右侧箭头独立展开四种中文选项；选择后同步更新卡片名称和设置页摘要。「跟随系统」保留为独立选项。
- **切换与显示**：明确区分简体与繁体语言，兼容系统上报的特殊中文语言组合；缺失的繁体文案优先回退简体中文。弹窗适配深色模式和大字体，展开使用共用平滑动效。
- **自动填充标记**：标记“非自动填充”后立即撤下当前输入框的系统提示；旧解锁入口与密码建议在启动时重新检查标记，避免开启自动填充验证时反复显示解锁卡片。
- **填充兼容性**：系统与无障碍填充补充工号、学号及非标准中文字段识别，支持账号、密码分步登录；排除搜索框和验证码，避免混入其他窗口或网页的字段。网页优先按指定字段写入，减少异步粘贴错位；取消旧请求后停止回调，保留 Android Q 的免验证填充。
- **键盘填充**：连续填写前确认焦点已切换；App 消费“下一项”但未移动焦点、重建同一输入框连接或切换到其他 App 时停止，避免密码追加到账号中。

## English

### Summary

- Redesign deduplication with single- or multi-vault consolidation, conflict previews, eligible passkey deduplication, and complete field preservation.
- Improve rapid Dock switching and use Rust for large-list grouping and sorting.
- Quick setup adds six layouts, separate Dock controls, and app import.
- Add Traditional Chinese with Hong Kong terminology.
- Group Simplified Chinese, Traditional Chinese, Nya, and Classical Chinese in an expandable language card.
- Fix unlock prompts remaining after a field is marked as unsuitable for autofill.
- Improve filling for employee/student IDs, two-step sign-in, and web forms, reducing missed or incorrect fields.

### Details

- **Deduplication:** Configure sources, destination, and conflict policy in connected rounded rows. Search databases and preview details, filter additions, conflicts, and skipped items, and keep progress and primary actions accessible. Consolidate one or more sources into a separate local or MDBX destination while preserving sources and existing destination entries. Keep a separate copy when an existing account has different content. Matching preserves URL path and non-email username case, password whitespace, OTP parameters, custom fields, attachments, photos, and card faces, including after reopening MDBX. Same-name attachments with different content are retained, and repeat runs do not add duplicates. Cancelled or replaced scans cannot execute stale results. Passkeys are matched by credential identity and key data, preserving different keys for the same account. Nonzero counters, unavailable keys, and conflicts show explicit skip reasons. Batch moves save the destination before source cleanup, and deleting a copy preserves keys still referenced by other records.
- **Dock and list performance:** Reuse initialized security components between pages and reuse password groups when the complete input snapshot matches. Data or stack-setting changes still trigger recalculation. Rust batches grouping, scoring, and stable sorting for large lists, preserving card order and appearance with input limits, result validation, and a compatible fallback.
- **Quick setup:** Preview and apply six layouts: Bitwarden overview or list, Authenticator, Separate pages, Everyday, and Minimal. Keep your current layout if preferred. The flow now covers welcome, presets, Dock, page customization, security and autofill, data connections, and completion. Dock uses the existing settings cards, lists both visible and hidden pages, and supports drag-and-drop ordering in its own step; list, card, and color options expand separately in the next step. Going back preserves adjustments, Settings remains accessible, and all supported languages include the new text. Open app or file import directly, then return to the same setup step.
- **Hong Kong Traditional Chinese:** Add translations across settings, databases, backups, passkeys, autofill, and other screens, with consistent Hong Kong terminology.
- **Language selection:** Chinese uses the same full-width card style as other languages. A separate arrow expands its four variants; selecting one updates the card label and the language summary in Settings. Follow system remains a separate option.
- **Switching and display:** Distinguish Simplified and Traditional Chinese, including unusual locale combinations reported by some devices. Missing Traditional Chinese text falls back to Simplified Chinese first. The dialog supports dark mode, large text, and shared smooth expansion animations.
- **Autofill exclusions:** Marking a field as unsuitable for autofill dismisses its current system suggestions. Cached unlock entries and password suggestions recheck the exclusion before opening, preventing repeated unlock cards when autofill verification is enabled.
- **Filling compatibility:** System and accessibility filling recognize employee/student IDs and nonstandard Chinese fields, support separate username/password steps, exclude search and verification-code fields, and keep fields scoped to the active window or web origin. Web fields are addressed directly to avoid asynchronous paste targeting errors; cancelled requests stop delivering callbacks. Android Q filling without verification remains available.
- **Keyboard filling:** Verify that focus has moved before filling the next value. Stop when an app consumes Next without moving focus, restarts the same editor, or switches to another app, preventing passwords from being appended to usernames.
