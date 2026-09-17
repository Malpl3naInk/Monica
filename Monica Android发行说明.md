# Monica for Android 1.0.313

## 中文

### 简要
- 优化开启预览页面时的密码库首屏加载：密码条目就绪后立即显示概览，其他类型在后台完成解析并自动补全。


### 详细
- **密码库预览性能**：预览页面不再等待所有笔记、验证器、银行卡和证件等明细完成解析才显示。先使用已就绪的密码条目生成概览首屏，后台明细计算完成后自动更新完整统计与推荐内容；关闭预览时仍沿用原有列表计算流程。

## English

### Summary
- Improve vault startup with the overview enabled by rendering an initial snapshot as soon as password entries are ready, then filling in other item types in the background.

### Details

- **Vault overview performance:** The overview no longer waits for notes, authenticators, bank cards, documents, and other detail payloads before showing its first frame. It publishes a password-only snapshot immediately, then replaces it with complete counts and recommendations when background parsing finishes. The regular list path keeps its existing calculation flow.
