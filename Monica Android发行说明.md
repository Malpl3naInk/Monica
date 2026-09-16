# Monica for Android 1.0.312

## 中文

- 新增文言文（华夏）、波兰语和猫语，补齐多语言文案；猫语缺失内容回退简体中文。
- 新增 Android 凭据交换（CXP/CXF），支持与兼容密码管理器迁移密码和 Passkey，按规范跳过无法迁移的凭据。
- ZIP/CSV 导入与文件导出支持选择本地、KeePass、MDBX 或 Bitwarden 数据库；加入进度显示、后台导出及 Rust 批量导入优化。
- 统一 MDBX/KeePass 管理、WebDAV/OneDrive 备份、导入导出、语言选择和多凭据编辑的 M3E 设计，常用操作始终可达。
- 优化概览磁贴对齐与长译文适配；新建和详情页加入平滑展开动效，卡包支持可选的循环堆叠。
- KeePass 新增本地与远端冲突比较、合并，支持逐项选择保留的修改。
- 完善 MDBX API 令牌的备注、自定义字段、收藏及跨库复制/移动，缩短详情内容加载等待。
- 修复 MDBX 仅查看令牌也显示未同步的问题，后台同步时可正常读取本地内容。
- 回收站跟随当前数据库，统一多选操作，并以悬浮按钮返回。
- 修复 Bitwarden/Vaultwarden 同步状态与回收站删除问题，以及密码库概览、验证器在部分场景下的闪退。

## English

- Add Classical Chinese (Huaxia), Polish, and Nya, and complete missing translations. Nya falls back to Simplified Chinese.
- Add Android Credential Exchange (CXP/CXF) to transfer passwords and passkeys with compatible password managers, skipping credentials that cannot be transferred under the specification.
- Choose a local, KeePass, MDBX, or Bitwarden database for ZIP/CSV imports and file exports. Add progress indicators, background exports, and Rust batch import improvements.
- Unify database management, WebDAV/OneDrive backups, import/export, language selection, and multiple-credential editing with M3E layouts and readily accessible actions.
- Align overview tiles across languages, add smooth expansion to editors and details, and offer optional looping card stacks.
- Compare and merge local and remote KeePass conflicts, with individual choices for overlapping changes.
- Expand MDBX API tokens with notes, custom fields, favorites, and copying/moving between databases; reduce waits for token details.
- Stop viewing MDBX tokens from incorrectly showing pending edits, and keep local content readable during background sync.
- Keep the recycle bin scoped to the current database, with consistent selection controls and a floating return button.
- Fix Bitwarden/Vaultwarden sync status and trash deletion, plus crashes in vault overview navigation and the authenticator on affected devices.
