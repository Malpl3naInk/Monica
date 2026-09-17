# Monica for Android 1.0.312

## 中文

### 简要

- 快速初始化新增六套布局预设，Dock 与页面微调分步设置。
- 新增繁體中文，采用香港常用书面用语。
- 中文语言选项收纳为可展开的长卡片，简体、繁体、猫语和文言文集中选择。
- 修复标记“非自动填充”后仍出现解锁提示的问题。

### 详细

- **快速初始化**：新增 Bitwarden 预览／列表、验证器专用、分页管理、日常常用和极简密码六套预设，可先预览 Dock 再应用；支持保留当前布局。流程调整为欢迎、预设、Dock、页面微调、安全与自动填充、接入数据、完成；Dock 复用底栏设置卡片，完整显示已开启及隐藏页面，支持长按拖拽排序；列表、卡片和配色在后续步骤按需展开。返回前一步保留微调，设置入口始终可达，并补齐各语言文案。
- **香港繁体中文**：补齐设置、数据库、备份、Passkey、自动填充等页面的繁体文案，统一使用「設定」「檔案」「數據庫」「電郵」等香港常用表达。
- **语言选择**：中文与其他语言保持一致的长卡片样式，最右侧箭头独立展开四种中文选项；选择后同步更新卡片名称和设置页摘要。「跟随系统」保留为独立选项。
- **切换与显示**：明确区分简体与繁体语言，兼容系统上报的特殊中文语言组合；缺失的繁体文案优先回退简体中文。弹窗适配深色模式和大字体，展开使用共用平滑动效。
- **自动填充标记**：标记“非自动填充”后立即撤下当前输入框的系统提示；旧解锁入口与密码建议在启动时重新检查标记，避免开启自动填充验证时反复显示解锁卡片。

## English

### Summary

- Add six quick-setup layouts, with separate Dock and page-customization steps.
- Add Traditional Chinese with Hong Kong terminology.
- Group Simplified Chinese, Traditional Chinese, Nya, and Classical Chinese in an expandable language card.
- Fix unlock prompts remaining after a field is marked as unsuitable for autofill.

### Details

- **Quick setup:** Preview and apply six layouts: Bitwarden overview or list, Authenticator, Separate pages, Everyday, and Minimal. Keep your current layout if preferred. The flow now covers welcome, presets, Dock, page customization, security and autofill, data connections, and completion. Dock uses the existing settings cards, lists both visible and hidden pages, and supports drag-and-drop ordering in its own step; list, card, and color options expand separately in the next step. Going back preserves adjustments, Settings remains accessible, and all supported languages include the new text.
- **Hong Kong Traditional Chinese:** Add translations across settings, databases, backups, passkeys, autofill, and other screens, with consistent Hong Kong terminology.
- **Language selection:** Chinese uses the same full-width card style as other languages. A separate arrow expands its four variants; selecting one updates the card label and the language summary in Settings. Follow system remains a separate option.
- **Switching and display:** Distinguish Simplified and Traditional Chinese, including unusual locale combinations reported by some devices. Missing Traditional Chinese text falls back to Simplified Chinese first. The dialog supports dark mode, large text, and shared smooth expansion animations.
- **Autofill exclusions:** Marking a field as unsuitable for autofill dismisses its current system suggestions. Cached unlock entries and password suggestions recheck the exclusion before opening, preventing repeated unlock cards when autofill verification is enabled.
