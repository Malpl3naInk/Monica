"""Build editable M3E Canvas state boards. Only writes files in this directory."""
from pathlib import Path
import base64
import json
import zlib
from compact_controls import compact_document

OUT = Path(__file__).resolve().parent


class Board:
    def __init__(self, key, title):
        self.key, self.i, self.x = key, 0, 0
        self.doc = dict(
            title=title, brief="Monica 统一项目 · 设计稿，未实现。虚构演示数据；联动规则见 interactions.md。",
            frame="phone", platform="android", paletteKey="purple",
            theme=dict(dark=True, bothModes=True, contrast="standard", shape="rounded", font="system", motion="expressive"),
            frames=[], groups=[]
        )

    def item(self, kind, x, y, **kw):
        self.i += 1
        identity = f"{self.key}-{self.i}"
        item = dict(id=identity, kind=kind, variant="tonal", icon=None, label="")
        item.update(kw)
        self.doc["groups"].append(dict(id=identity, x=self.x+x, y=y, axis="y", items=[item]))

    def text(self, x, y, label, size=16, bold=False):
        self.item("text", x, y, label=label, size=size, bold=bold)

    def box(self, x, y, w, h, fill="surfaceContainerHigh", top=24, bottom=24, **kw):
        self.item("box", x, y, size=w, size2=h, fill=fill, radiusTop=top, radiusBottom=bottom, **kw)

    def icon(self, x, y, name, variant="text", size=48):
        self.item("iconButton", x, y, icon=name, variant=variant, size=size)

    def button(self, x, y, label, icon=None, w=176, variant="tonal"):
        self.item("button", x, y, label=label, icon=icon, size=w, variant=variant)

    def row(self, y, title, sub="", icon="folder", trail="chevron_right", top=24, bottom=24, fill="surfaceContainerLow", h=72):
        self.box(12, y, 388, h, fill, top, bottom)
        if icon:
            self.icon(20, y+(h-48)/2, icon)
        left = 76 if icon else 28
        self.text(left, y+13 if sub else y+23, title, 16, True)
        if sub:
            self.text(left, y+39, sub, 12)
        if trail:
            self.icon(344, y+(h-48)/2, trail)

    def field(self, y, label, value, top=8, bottom=8, secret=False, menu=False, h=64):
        self.box(12, y, 388, h, "surfaceContainerHigh", top, bottom)
        self.text(28, y+9, label, 12)
        self.text(28, y+31, value, 16)
        if secret:
            self.icon(300 if menu else 344, y+8, "visibility_off")
        if menu:
            self.icon(344, y+8, "more_vert")

    def frame(self, label, note):
        self.x = len(self.doc["frames"])*460
        self.doc["frames"].append(dict(id=f"f{len(self.doc['frames'])}", name=label, x=self.x, y=0, bg="surface", note=note))

    def header(self, title, subtitle="", compact=False, star=False, right="more_vert", template=None):
        self.text(24, 12, "9:41", 12, True)
        self.text(326, 12, "5G  ▰", 12)
        self.icon(12, 34, "arrow_back")
        if star:
            self.icon(292, 34, "star_border")
        if right:
            self.icon(348, 34, right)
        if compact:
            self.text(72, 46, title, 21, True)
            if subtitle:
                self.text(20, 98, subtitle, 13)
        else:
            self.text(20, 94 if template else 102, title, 30, True)
            if subtitle:
                self.text(22, 148, subtitle, 13)
            if template:
                self.box(12, 142, 388, 48, "secondaryContainer", 24, 24)
                self.icon(20, 142, "dashboard_customize")
                self.text(76, 156, f"模板 · {template}", 14, True)
                self.icon(344, 142, "expand_more")

    def save(self, label="保存", icon="save"):
        self.button(264, 796, label, icon, 136, "filled")

    def target(self, y=180):
        self.row(y, "工作库", "开发 · MDBX", "database", h=64)

    def pair(self, y, left, right, icons=("add", "apps")):
        for x, label, icon, corners in [
            (12, left, icons[0], dict(tl=26, tr=8, bl=26, br=8)),
            (208, right, icons[1], dict(tl=8, tr=26, bl=8, br=26))
        ]:
            self.box(x, y, 192, 52, "secondaryContainer", corners=corners)
            self.icon(x+8, y+2, icon)
            self.text(x+65, y+17, label, 14, True)

    def section(self, y, title, more=False):
        self.text(20, y, title, 17, True)
        if more:
            self.icon(344, y-13, "more_vert")

    def qr_placeholder(self, x, y):
        # Visual placeholder only, deliberately not a credential or usable QR payload.
        matrix = [[(row*17+col*31+row*col) % 13 < 6 for col in range(21)] for row in range(21)]
        for ox, oy in [(0, 0), (14, 0), (0, 14)]:
            for row in range(max(0, oy-1), min(21, oy+8)):
                for col in range(max(0, ox-1), min(21, ox+8)):
                    matrix[row][col] = False
            for row in range(7):
                for col in range(7):
                    matrix[oy+row][ox+col] = row in (0, 6) or col in (0, 6) or (2 <= row <= 4 and 2 <= col <= 4)
        for row in range(21):
            for col in range(21):
                if matrix[row][col]:
                    self.box(x+col*9, y+row*9, 9, 9, "onSurface", 0, 0)

    def finish(self):
        self.doc = compact_document(self.doc)
        raw = json.dumps(self.doc, ensure_ascii=False, separators=(",", ":"))
        (OUT/f"{self.key}.json").write_text(raw, encoding="utf-8")
        compressed = zlib.compress(raw.encode("utf-8"), wbits=-15)
        url = "https://lnkiai.github.io/m3e-canvas/#docz="+base64.urlsafe_b64encode(compressed).decode().rstrip("=")
        notes = "\n".join(f"- **{f['name']}**：{f['note']}" for f in self.doc["frames"])
        (OUT/f"{self.key}.md").write_text(
            f"# {self.doc['title']}\n\n[在 M3E Canvas 编辑]({url})\n\n"
            f"[JSON 备份]({self.key}.json) · [交互说明](../interactions.md)\n\n"
            f"{notes}\n\n![M3E Canvas 实际渲染]({self.key}.png)\n", encoding="utf-8"
        )
        return dict(key=self.key, title=self.doc["title"], url=url, frames=len(self.doc["frames"]))


boards = []
b = Board("01-create", "01 · 模板与按需组装")
b.frame("A1 · 模板入口", "仅全局新建或主动更换模板时显示。从密码/笔记列表新建直接采用对应模板；没有新增必经向导。")
b.header("从模板开始", "之后还可以继续添加内容")
for i, (name, sub, icon) in enumerate([
    ("账号", "密码、网站与验证码", "password"),
    ("笔记", "正文、清单与附件", "description"),
    ("验证码", "扫描或输入密钥", "shield"),
    ("开发凭据", "SSH、API Token 与说明", "terminal"),
    ("卡片与证件", "银行卡、证件与地址", "credit_card"),
    ("更多模板", "二维码、Wi-Fi、空白项目", "dashboard_customize")
]):
    b.row(208+i*76, name, sub, icon, top=28 if i == 0 else 8, bottom=28 if i == 5 else 8)
b.button(12, 728, "批量新建", "library_add", 388, "outlined")
b.text(26, 800, "批量模式会创建多个独立项目", 13)

b.frame("A2 · 大标题与表单内模板", "顶栏只保留返回、收藏、更多。模板选择在新建密码大标题下，随表单滚动；下滑后的标题状态见 A3。模板不是存储类型限制。")
b.header("新建密码", star=True, template="密码")
b.target(202)
b.field(278, "项目名称", "工作账号", top=24, bottom=24)
b.section(364, "登录 · 1", True)
b.field(398, "用户名", "dev@example.org", top=24)
b.field(466, "密码", "••••••••••••", bottom=24, secret=True)
b.button(12, 538, "添加验证码", "shield", 184, "text")
b.field(598, "网站", "https://example.org", top=24, bottom=24, menu=True)
b.pair(670, "添加 URL", "绑定应用")
b.button(12, 740, "添加内容", "add", 176)
b.save()

b.frame("A3 · 多笔记与内容选择", "同一项目草稿内的追加区。笔记可反复添加，点其中一篇进入 C4；下面的内容选择区内联展开，不是新数据库或绑定面板。")
b.header("新建密码", "工作账号 · 未保存", compact=True, star=True)
b.row(136, "登录账号", "dev@example.org", "password", "expand_more")
b.section(230, "笔记 · 2")
b.row(264, "部署说明", "部署前检查与命令说明", "description", "more_vert", top=24, bottom=8, h=64)
b.row(332, "恢复步骤", "备用邮箱与恢复流程", "description", "more_vert", top=8, bottom=24, h=64)
b.button(12, 404, "添加更多", "add", 164, "text")
b.section(484, "添加其他内容")
for y, left, right, icons in [
    (524, "SSH 密钥", "API Token", ("terminal", "token")),
    (588, "GPG 密钥", "二维码", ("key", "qr_code_2")),
    (652, "支付资料", "通行密钥", ("credit_card", "key"))
]:
    b.pair(y, left, right, icons)
b.button(12, 716, "更多类型", "expand_more", 176, "text")
b.save()

b.frame("A4 · 一个项目，六份内容", "最终组件目录共六份：登录、OTP、两篇笔记、SSH、API Token。每份有稳定 ID；只有一个原始项目。三点菜单只操作对应内容。")
b.header("工作账号", "新建项目", star=True)
b.target(184)
b.section(274, "内容 · 6")
for i, (title, sub, icon) in enumerate([
    ("登录账号", "dev@example.org", "password"),
    ("验证码", "工作账号 · 30 秒", "shield"),
    ("部署说明", "笔记", "description"),
    ("恢复步骤", "笔记", "description"),
    ("构建服务器", "SSH · Ed25519", "terminal"),
    ("GitHub API", "API Token", "token")
]):
    b.row(308+i*68, title, sub, icon, "more_vert", top=24 if i == 0 else 8, bottom=24 if i == 5 else 8, h=64)
b.button(12, 732, "添加内容", "add", 176)
b.save()
boards.append(b.finish())

b = Board("02-linked-details", "02 · 共用详情与笔记联动")
b.frame("B1 · 共用项目详情", "密码/Passkey/密钥等从同一个详情骨架定位所选组件。这里是登录入口；秘密显示时局部高度动画，OTP 不驱动整页重组。")
b.header("工作账号", "6 份内容", star=True)
b.target(184)
b.section(278, "登录账号", True)
b.field(314, "用户名", "dev@example.org", top=24, menu=True)
b.field(382, "密码", "••••••••••••", bottom=24, secret=True, menu=True)
b.section(472, "验证码")
b.box(12, 506, 388, 108, "secondaryContainer", 28, 28)
b.text(28, 522, "工作账号", 13)
b.text(28, 548, "397 821", 34, True)
b.icon(340, 538, "content_copy")
b.text(272, 584, "18 秒", 12)
b.row(630, "笔记 · 2", "部署说明、恢复步骤", "description", "chevron_right", h=64)
b.row(706, "密钥与令牌", "构建服务器、GitHub API", "key", "chevron_right", h=64)
b.save("编辑", "edit")

b.frame("B2 · 两篇笔记，两个入口", "保留笔记列表的预览风格，只补来源。工作账号的两篇笔记同 itemId、不同 componentId；第三篇是原本独立的笔记。")
b.header("笔记", "工作库", right="search")
for y, title, preview, source in [
    (214, "部署说明", "部署前检查与命令说明…", "工作账号"),
    (356, "恢复步骤", "备用邮箱与恢复流程…", "工作账号"),
    (498, "旅行清单", "证件、充电器、随身物品…", "独立项目")
]:
    b.box(12, y, 388, 126, "surfaceContainerLow", 24, 24)
    b.text(28, y+18, title, 20, True)
    b.icon(344, y+6, "more_vert")
    b.text(28, y+54, preview, 15)
    b.text(28, y+94, source, 12)
b.button(236, 796, "新建笔记", "add", 164, "filled")

b.frame("B3 · 笔记专用详情", "正文优先，保留阅读/Markdown 风格。来自工作账号是实际可点的原项目入口；返回恢复原笔记列表或原项目位置。")
b.header("部署说明", "今天更新", right="more_vert")
b.row(190, "来自 工作账号", "工作库", "folder_open", "arrow_forward", h=64)
b.text(20, 300, "部署前检查", 24, True)
b.text(20, 350, "确认环境与当前版本。", 17)
b.text(20, 386, "备份配置后，再执行发布。", 17)
b.text(20, 454, "检查清单", 20, True)
b.icon(12, 494, "check_box")
b.text(68, 508, "配置已备份", 16)
b.icon(12, 546, "check_box_outline_blank")
b.text(68, 560, "检查服务运行状态", 16)
b.row(646, "部署清单.pdf", "附件 · 128 KB", "attach_file", h=72)
b.save("编辑", "edit")

b.frame("B4 · 单独验证码项目", "同一个详情 Host，只有 OTP 组件就只显示验证码和必要信息。没有空密码区；HOTP 预览不消费计数。")
b.header("工作邮箱", "验证码", star=True)
b.target(184)
b.box(12, 282, 388, 142, "secondaryContainer", 28, 28)
b.text(28, 304, "mail@example.org", 14)
b.text(28, 344, "482 906", 40, True)
b.icon(340, 346, "content_copy")
b.text(298, 396, "22 秒", 12)
b.row(454, "30 秒 · 6 位", "TOTP · SHA-1", "schedule", "tune", top=24, bottom=8)
b.row(530, "密钥", "点击查看", "key", "visibility_off", top=8, bottom=24)
b.button(12, 644, "添加内容", "add", 176)
b.save("编辑", "edit")
boards.append(b.finish())

b = Board("03-content-focus", "03 · 专用内容与同一草稿")
b.frame("C1 · 共用详情继续向下", "B1 的后续滚动位置。SSH/API 仍属于工作账号。令牌正文按当前项目按需读取，不等远端上传，也不重扫整个库。")
b.header("工作账号", "密钥与令牌", compact=True, star=True)
b.row(152, "构建服务器", "SSH · Ed25519", "terminal", "chevron_right")
b.section(254, "GitHub API", True)
b.field(290, "提供商", "GitHub", top=24)
b.field(358, "Token", "••••••••••••••••", secret=True, menu=True)
b.field(426, "API 地址", "https://api.github.com/", bottom=24)
b.button(12, 506, "复制令牌", "content_copy", 184, "tonal")
b.section(594, "附件")
b.button(12, 628, "添加更多", "add", 164, "text")
b.button(12, 712, "添加内容", "add", 176)
b.save("编辑", "edit")

b.frame("C2 · SSH 内容定位", "继续用共用详情 Host。只展开当前密钥的正文；生成/导出使用既有密钥校验，不改变原材料。图中摘要是虚构示例。")
b.header("工作账号", "构建服务器 · SSH 密钥", compact=True, star=True)
b.row(154, "Ed25519", "SHA256:8Fq…k2V", "fingerprint", "content_copy")
b.section(260, "密钥材料")
b.row(298, "公钥", "ssh-ed25519 AAAAC3…", "key", "expand_more", top=24, bottom=8)
b.row(374, "私钥", "已隐藏", "lock", "visibility_off", top=8, bottom=24)
b.pair(478, "复制公钥", "导出密钥", ("content_copy", "file_download"))
b.section(568, "备注")
b.box(12, 606, 388, 102, "surfaceContainerLow", 24, 24)
b.text(28, 628, "构建服务使用的访问密钥。", 16)
b.text(28, 660, "更新后同步检查服务器配置。", 16)
b.save("编辑", "edit")

b.frame("C3 · 二维码出示页", "专用大码展示，保留返回原项目入口。此草图使用不可扫描的示意矩阵，不是真实二维码；实际实现由现有条码模块生成。")
b.header("会员卡", "生活资料", right="more_vert")
b.row(188, "来自 生活资料", "个人库", "folder_open", "arrow_forward", h=64)
b.box(52, 296, 308, 284, "surfaceContainerHigh", 28, 28)
b.qr_placeholder(112, 326)
b.text(114, 546, "2026 0920 001", 18, True)
b.button(98, 630, "提高亮度", "brightness_7", 216, "tonal")
b.save("编辑", "edit")

b.frame("C4 · 笔记编辑，回到草稿", "从 A3 进入。完成只更新同一个项目草稿；不会提前落独立 SecureItem。返回保留其它内容、光标与滚动位置。")
b.header("编辑笔记", compact=True, right=None)
b.button(284, 38, "完成", "check", 116, "filled")
b.row(118, "工作账号", "新建项目 · 尚未保存", "folder_open", None, h=64)
b.field(210, "笔记名称", "恢复步骤", top=24, bottom=24)
b.box(12, 294, 388, 362, "surfaceContainerLow", 24, 24)
b.text(28, 318, "恢复步骤", 22, True)
b.text(28, 372, "1. 使用备用邮箱接收验证。", 16)
b.text(28, 416, "2. 更新账号密码。", 16)
b.text(28, 460, "3. 检查已登录设备。", 16)
b.button(12, 684, "格式与预览", "edit_note", 204, "text")
b.button(12, 754, "添加附件", "attach_file", 184, "text")
boards.append(b.finish())

b = Board("04-batch", "04 · 多账号与批量新建")
b.frame("D1 · 两种创建语义", "主动打开批量入口时说明差异；普通新建没有此额外步骤。一个项目追加账号与批量创建独立项目不会混用。")
b.header("如何新建", "选择内容的组织方式")
b.row(222, "一个项目，多份内容", "账号、笔记与密钥一起管理", "layers", "radio_button_unchecked", top=28, bottom=8, h=104)
b.row(330, "批量新建", "每一项都是独立项目", "library_add", "radio_button_checked", top=8, bottom=28, fill="secondaryContainer", h=104)
b.section(482, "批量模式")
b.text(24, 524, "先设置公共信息，再逐项填写。", 16)
b.text(24, 562, "保存后各项目可以独立编辑。", 16)
b.button(236, 796, "继续", "arrow_forward", 164, "filled")

b.frame("D2 · 公共信息与项目清单", "公用设置是创建时默认值。密码默认逐项独立生成；不会默认复制 OTP/Passkey。清单保留所有项目，当前编辑范围始终明确。")
b.header("批量新建", "3 个独立项目")
b.row(208, "公共设置", "工作库 · example.org", "tune")
b.section(316, "项目 · 3")
for i, (title, sub) in enumerate([
    ("生产环境", "沿用公共设置"),
    ("测试环境", "2 处单独设置"),
    ("开发环境", "沿用公共设置")
]):
    b.row(354+i*80, title, sub, "password", "more_vert", top=24 if i==0 else 8, bottom=24 if i==2 else 8, h=76)
b.button(12, 610, "添加项目", "add", 184, "text")
b.text(24, 704, "保存前可查看每项的内容和位置", 14)
b.button(220, 796, "保存预览", "fact_check", 180, "filled")

b.frame("D3 · 只显示当前编辑范围", "底栏当前范围只有项目 2/3，不再同时显示公共凭据和独立凭据两套选中项。完成返回清单；覆盖可恢复为公共默认值。")
b.header("测试环境", "项目 2 / 3", right="more_vert")
b.row(190, "工作库", "来自公共设置", "database", h=64)
b.field(282, "用户名", "test@example.org", top=24)
b.field(350, "密码", "••••••••••••", bottom=24, secret=True)
b.field(444, "网站 · 单独设置", "https://test.example.org", top=24, bottom=24)
b.button(12, 518, "恢复公共设置", "undo", 220, "text")
b.section(596, "笔记 · 单独设置")
b.row(632, "测试流程", "仅保存在测试环境项目", "description", "more_vert")
b.box(12, 784, 388, 64, "secondaryContainer", 28, 28)
b.text(28, 806, "项目 2 / 3", 14, True)
b.icon(208, 792, "chevron_left")
b.icon(258, 792, "chevron_right")
b.button(312, 792, "完成", None, 80, "text")

b.frame("D4 · 分项结果与可重试进度", "3 项目 × 2 位置。已排队不等于服务器确认；失败只重试对应 operationId/目标，不复制已成功项目。")
b.header("保存结果", "3 个项目 · 2 个位置")
b.text(24, 206, "5 / 6 已处理", 24, True)
b.box(12, 250, 388, 8, "secondaryContainer", 4, 4)
b.box(12, 250, 322, 8, "primary", 4, 4)
b.row(292, "工作库", "3 项已保存", "check_circle", None, h=72)
b.section(400, "Bitwarden")
b.row(438, "生产环境", "等待同步", "cloud_upload", None, top=24, bottom=8, h=68)
b.row(510, "测试环境", "尚未保存 · 网络中断", "error_outline", "refresh", top=8, bottom=8, h=68)
b.row(582, "开发环境", "等待同步", "cloud_upload", None, top=8, bottom=24, h=68)
b.text(24, 692, "草稿已保留，可稍后继续", 14)
b.button(12, 796, "返回清单", "arrow_back", 168, "text")
b.button(192, 796, "重试失败项", "refresh", 208, "filled")
boards.append(b.finish())

b = Board("05-storage-lifecycle", "05 · 存储、移除与冲突")
b.frame("E1 · 同一项目的存储位置", "只选一个主位置；另存副本是明确的高级动作。数据库能力在保存前校验，不把不支持的组件悄悄写回本地。")
b.header("存储位置", "工作账号 · 6 份内容")
for i, (title, sub, icon, trail) in enumerate([
    ("个人库", "Monica 本地", "smartphone", "radio_button_unchecked"),
    ("工作保险库", "KeePass · KDBX", "folder", "radio_button_unchecked"),
    ("工作库", "MDBX · 当前选择", "database", "radio_button_checked"),
    ("Bitwarden", "附加内容由 Monica 读取", "cloud", "info")
]):
    b.row(212+i*80, title, sub, icon, trail, top=24 if i==0 else 8, bottom=24 if i==3 else 8, fill="secondaryContainer" if i==2 else "surfaceContainerLow", h=76)
b.row(568, "文件夹", "开发", "folder_open", h=64)
b.button(12, 670, "另存副本", "content_copy", 184, "text")
b.save("完成", "check")

b.frame("E2 · Bitwarden 显示范围", "在能力检查通过后的兼容说明示例。主账号走原生字段，扩展走验证过的加密载体。容量/权限不满足时保存前阻止并保留草稿，不承诺官方 UI 理解全部组件。")
b.header("兼容显示", "Bitwarden")
b.section(220, "其他客户端也能识别")
b.row(256, "主登录账号", "用户名、密码、网站与验证码", "password", "check_circle", h=88)
b.section(380, "由 Monica 展示的附加内容")
b.row(416, "笔记 · 2", "部署说明、恢复步骤", "description", None, top=24, bottom=8)
b.row(492, "SSH 与 API Token", "构建服务器、GitHub API", "key", None, top=8, bottom=24)
b.text(24, 606, "其他客户端可能只显示主要内容。", 15)
b.text(24, 642, "完整项目仍可在 Monica 中查看。", 15)
b.button(12, 728, "更换存储位置", "database", 232, "outlined")
b.save("返回", "arrow_back")

b.frame("E3 · 移除一篇笔记", "组件删除确认与项目删除分开。移除 note-a 不删除同项目密码和 note-b；菜单另有移出为独立项目。保存前可撤销。")
b.header("工作账号", "内容 · 6", star=True)
b.row(198, "部署说明", "笔记", "description", "more_vert")
b.box(28, 298, 356, 304, "surfaceContainerHigh", 28, 28)
b.icon(182, 320, "delete_outline")
b.text(52, 388, "移除这篇笔记？", 24, True)
b.text(52, 438, "将从工作账号移除部署说明。", 15)
b.text(52, 474, "密码和其他内容会保留。", 15)
b.button(138, 532, "取消", None, 104, "text")
b.button(250, 532, "移除", "remove", 112, "filled")
b.button(12, 678, "移出为独立项目", "open_in_new", 256, "text")

b.frame("E4 · 同一组件发生冲突", "展示 note-b 的两种修改。其他不同组件修改在有共同基准时合并；合并后仍需通过远端版本校验，不能用 updatedAt 粗暴覆盖。")
b.header("需要合并", "工作账号 · 恢复步骤")
b.box(12, 220, 388, 168, "secondaryContainer", 24, 24)
b.text(28, 240, "本机更改", 18, True)
b.icon(342, 226, "radio_button_checked")
b.text(28, 284, "使用备用邮箱接收验证码。", 16)
b.text(28, 322, "完成后检查登录设备。", 16)
b.text(28, 358, "此手机", 12)
b.box(12, 404, 388, 168, "surfaceContainerLow", 24, 24)
b.text(28, 424, "远端更改", 18, True)
b.icon(342, 410, "radio_button_unchecked")
b.text(28, 468, "先联系管理员恢复访问。", 16)
b.text(28, 506, "随后更新备用邮箱。", 16)
b.text(28, 542, "另一台设备", 12)
b.row(608, "其他内容已合并", "账号与部署说明均已保留", "check_circle", None, h=76)
b.button(192, 796, "确认合并", "merge", 208, "filled")
boards.append(b.finish())

(OUT/"manifest.json").write_text(json.dumps(boards, ensure_ascii=False, indent=2), encoding="utf-8")
index = [
    "# 统一项目 · M3E Canvas",
    "",
    "2026-09-20 · 5 张可编辑画布，20 个页面/状态。设计稿，尚未实现。",
    "",
    "[方案总览](../README.md) · [交互契约](../interactions.md) · [存储兼容](../architecture.md)",
    "",
    "每张页面内都有「在 M3E Canvas 编辑」链接及 JSON 备份；以下预览来自 Canvas 实际渲染。",
    "",
]
for board in boards:
    index += [f"- [{board['title']}]({board['key']}.md)"]
index += [
    "",
    "## 主要联动",
    "",
    "A2 新建 → A3 添加两篇笔记 → C4 编辑第二篇 → A4 保存一个项目。",
    "",
    "B2 笔记列表 → B3 笔记阅读 → B1 同一原项目。B1 的笔记区也能直接打开 B3，不绕到全局笔记列表。",
    "",
    "B1 向下滚动 → C1 密钥与令牌 → C2 密钥内容；单独 OTP 见 B4，二维码出示见 C3。",
    "",
    "D1 明确创建语义 → D2 批量清单 → D3 单项覆盖 → D4 分目标结果与重试。",
    "",
    "E1 选择归属 → E2 了解 Bitwarden 展示范围；E3 移除单份内容，E4 处理同组件冲突。",
    "",
    "![新建与组装](01-create.png)",
    "",
    "![共用详情与笔记](02-linked-details.png)",
    "",
    "![批量新建](04-batch.png)",
    "",
    "画布是静态状态设计：没有实现数据库操作、拖拽或系统凭据调用。原生验收见方案文档。",
]
(OUT/"index.md").write_text("\n".join(index)+"\n", encoding="utf-8")
print(f"Generated {len(boards)} Canvas boards / {sum(x['frames'] for x in boards)} states.")
