"""Design-only v6: body template controls and add actions are compact and left aligned."""
from pathlib import Path
import base64
import json
import zlib
import sys

OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(OUT.parents[1]/"unified-items-v1"/"canvas"))
from compact_controls import compact_document
manifest = json.loads((OUT/"manifest.json").read_text(encoding="utf-8"))
updated = []
for board in manifest:
    file = OUT/f"{board['key']}.json"
    doc = json.loads(file.read_text(encoding="utf-8"))
    for frame in doc["frames"]:
        fx = frame["x"]
        groups = [g for g in doc["groups"] if fx <= g["x"] < fx+412]
        labels = [g for g in groups if g["x"] == fx+120 and g["y"] == 51 and g["items"][0]["kind"] == "text"]
        if not labels:
            continue
        type_name = labels[0]["items"][0]["label"]
        type_icon = next(g["items"][0]["icon"] for g in groups if g["x"] == fx+76 and g["y"] == 36)
        positions = {(72, 36), (220, 36), (76, 36), (120, 51), (238, 51)}
        removed_ids = {g["id"] for g in groups if (g["x"]-fx, g["y"]) in positions}
        doc["groups"] = [g for g in doc["groups"] if g["id"] not in removed_ids]
        headings = [g for g in groups if g["y"] == 102 and g["items"][0]["kind"] == "text" and g["items"][0].get("size", 0) >= 30]
        def add(suffix, kind, x, y, **kwargs):
            identity = f"{board['key']}-{frame['id']}-header-v5-{suffix}"
            item = dict(id=identity, kind=kind, label="", variant="tonal", icon=None)
            item.update(kwargs)
            doc["groups"].append(dict(id=identity, x=fx+x, y=y, axis="y", items=[item]))
        if headings:
            headings[0]["y"] = 94
            headings[0]["items"][0]["size"] = 30
            add("body", "box", 12, 142, size=388, size2=48, fill="secondaryContainer", radiusTop=24, radiusBottom=24)
            add("icon", "iconButton", 20, 142, icon=type_icon, size=48, variant="text")
            add("label", "text", 76, 156, label=f"类型 · {type_name}", size=14, bold=True)
            add("expand", "iconButton", 344, 142, icon="expand_more", size=48, variant="text")
            for group in groups:
                if group["y"] == 184 and group["items"][0]["kind"] == "listItem":
                    group["y"] = 196
        else:
            title = "新建密码" if type_name == "密码" else f"新建 {type_name}"
            add("title", "text", 70, 48, label=title, size=21, bold=True)
        if board["key"] == "07-type-switch":
            if frame["id"] == "f0":
                frame["name"] = "G1 · 类型移到标题下面"
                frame["note"] = "顶栏只有返回、收藏与更多。新建密码大标题下为完整类型选择行；整行可点，随表单滚动。"
            if frame["id"] == "f2":
                frame["note"] = "选择 SSH 后更新大标题与正文类型行。顶栏仍无切换控件；旧独立草稿保留规则仅属于上一版方案。"
            if frame["id"] == "f3":
                frame["name"] = "G4 · 下滑后标题进入顶栏"
                frame["note"] = "大标题随滚动连续收起到顶栏。类型选择行已随正文滚出，不固定、不重复显示；返回顶部再展开。"
    doc = compact_document(doc)
    raw = json.dumps(doc, ensure_ascii=False, separators=(",", ":"))
    file.write_text(raw, encoding="utf-8")
    board["url"] = "https://lnkiai.github.io/m3e-canvas/#docz="+base64.urlsafe_b64encode(zlib.compress(raw.encode(), wbits=-15)).decode().rstrip("=")
    if board["key"] == "07-type-switch":
        board["title"] = "07 · 正文类型选择与收起标题"
        doc["title"] = board["title"]
        raw = json.dumps(doc, ensure_ascii=False, separators=(",", ":"))
        file.write_text(raw, encoding="utf-8")
        board["url"] = "https://lnkiai.github.io/m3e-canvas/#docz="+base64.urlsafe_b64encode(zlib.compress(raw.encode(), wbits=-15)).decode().rstrip("=")
    notes = "\n".join(f"- **{f['name']}**：{f['note']}" for f in doc["frames"])
    (OUT/f"{board['key']}.md").write_text(
        f"# {board['title']}\n\n[在 M3E Canvas 编辑]({board['url']})\n\n"
        f"[JSON 备份]({board['key']}.json)\n\n{notes}\n\n"
        f"![Canvas 实际渲染]({board['key']}.png)\n", encoding="utf-8"
    )
    updated.append(board)
(OUT/"manifest.json").write_text(json.dumps(updated, ensure_ascii=False, indent=2), encoding="utf-8")
links = "\n\n".join(f"[{b['title']}]({b['key']}.md) · [直接编辑]({b['url']})" for b in updated)
(OUT/"index.md").write_text(
    "# M3E Canvas 设计稿\n\n28 个状态，7 张可编辑画布。v6：正文类型与追加按钮紧凑居左，标题滚动收进顶栏。\n\n"
    "[后续统一项目方案](../../unified-items-v1/README.md) · [标题与选择控件四个状态](07-type-switch.md)\n\n"
    f"{links}\n\n本目录保留旧类型/独立草稿的方案；统一项目的新数据语义请看后续方案。没有修改应用代码。\n", encoding="utf-8"
)
print("Updated 7 existing design boards; no application source was changed.")
