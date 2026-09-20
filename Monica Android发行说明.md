# Monica for Android 1.0.313

## 中文

### 简要
- 收紧验证码卡片和密码分组／组内条目的留白，提高列表信息密度；保留收藏与封面按钮原尺寸（#139）。
- 修复 KeePass WebDAV 浏览器及云备份操作按钮在长文案换行时未居中的问题（#140）。
- 优化开启预览页面时的密码库首屏加载：密码条目就绪后立即显示概览，其他类型在后台完成解析并自动补全。
- 预览概览达到中等规模时使用 Rust 批量聚合，保留校验失败后的兼容回退。


### 详细
- **密码库预览性能**：预览页面不再等待所有笔记、验证器、银行卡和证件等明细完成解析才显示。先使用已就绪的密码条目生成概览首屏，后台明细计算完成后自动更新完整统计与推荐内容；关闭预览时仍沿用原有列表计算流程。
- **Rust 概览聚合**：概览条目达到 256 条后使用 Rust 批量计算统计、收藏和推荐索引，降低中大型密码库的加载开销；JNI 结果异常时自动回退 Kotlin 实现。

## English

### Summary
- Reduce padding and spacing in TOTP cards and grouped password cards while preserving favorite/cover button sizes (#139).
- Center wrapped action labels in the KeePass WebDAV browser and cloud backup screens (#140).
- Improve vault startup with the overview enabled by rendering an initial snapshot as soon as password entries are ready, then filling in other item types in the background.
- Use Rust batch aggregation for medium and large overview snapshots, with validated Kotlin fallback.

### Details

- **Vault overview performance:** The overview no longer waits for notes, authenticators, bank cards, documents, and other detail payloads before showing its first frame. It publishes a password-only snapshot immediately, then replaces it with complete counts and recommendations when background parsing finishes. The regular list path keeps its existing calculation flow.
- **Rust overview aggregation:** Snapshots with 256 or more entries use Rust for batched counts, favorites, and recommendation indices, while malformed native results fall back to Kotlin.
