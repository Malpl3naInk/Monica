"""Shared drawing rules for the two editable Canvas proposals; no Android code."""
import math


def label_width(label):
    return sum(4 if char.isspace() else 7.5 if ord(char) < 128 else 14 for char in label)


def action_width(label):
    return max(112, math.ceil((label_width(label) + 64) / 4) * 4)


def part(identity, kind, x, y, **kwargs):
    item = dict(id=identity, kind=kind, label="", variant="tonal", icon=None)
    item.update(kwargs)
    return dict(id=identity, x=x, y=y, axis="y", items=[item])


def action_parts(identity, x, y, label, icon, width=None, corners=None, text_only=False):
    width = width or action_width(label)
    groups = []
    if not text_only:
        groups.append(part(identity+"-body", "box", x, y, size=width, size2=40,
                           fill="secondaryContainer", radiusTop=20, radiusBottom=20,
                           **(dict(corners=corners) if corners else {})))
    if icon:
        groups.append(part(identity+"-icon", "iconButton", x+8, y, icon=icon, size=40, variant="text"))
    text_x = x+48 if icon else x+(width-label_width(label))/2
    groups.append(part(identity+"-label", "text", text_x, y+10, label=label, size=14, bold=True))
    return groups


def compact_document(doc):
    """Convert only template selectors, connected actions and add buttons; idempotent."""
    for frame in doc["frames"]:
        fx = frame["x"]
        groups = [g for g in doc["groups"] if fx <= g["x"] < fx+412]
        remove = set()
        additions = []

        selectors = [g for g in groups if g["items"][0].get("label", "").startswith(("模板 · ", "类型 · "))]
        for selector in selectors:
            name = selector["items"][0]["label"].split(" · ", 1)[1]
            row_y = selector["y"]-14
            old_parts = [g for g in groups if row_y <= g["y"] <= row_y+14
                         and g["items"][0]["kind"] in ("box", "text", "iconButton")]
            icon = next((g["items"][0]["icon"] for g in old_parts
                         if g["items"][0]["kind"] == "iconButton" and g["x"] < fx+100), "dashboard_customize")
            remove.update(g["id"] for g in old_parts)
            identity = selector["id"]+"-compact"
            leading_width = max(100, math.ceil((label_width(name)+68)/4)*4)
            additions.extend(action_parts(identity, fx+12, row_y, name, icon, leading_width,
                                           dict(tl=20, tr=6, bl=20, br=6)))
            trailing_x = fx+12+leading_width+2
            additions.append(part(identity+"-trailing", "box", trailing_x, row_y,
                                  size=44, size2=40, fill="secondaryContainer",
                                  corners=dict(tl=6, tr=20, bl=6, br=20)))
            additions.append(part(identity+"-expand", "iconButton", trailing_x+2, row_y,
                                  icon="expand_more", size=40, variant="text"))

        # Existing connected action pairs use two 192dp surfaces across the whole row.
        for left in groups:
            item = left["items"][0]
            if item["kind"] != "box" or item.get("size") != 192 or left["x"] != fx+12:
                continue
            row_y = left["y"]
            right = next((g for g in groups if g["x"] == fx+208 and g["y"] == row_y
                          and g["items"][0]["kind"] == "box" and g["items"][0].get("size") == 192), None)
            if not right:
                continue
            row = [g for g in groups if row_y <= g["y"] < row_y+40
                   and g["items"][0]["kind"] in ("box", "text", "iconButton")]
            labels = sorted([g for g in row if g["items"][0]["kind"] == "text"], key=lambda g: g["x"])
            icons = sorted([g for g in row if g["items"][0]["kind"] == "iconButton"], key=lambda g: g["x"])
            if len(labels) != 2 or len(icons) != 2:
                continue
            remove.update(g["id"] for g in row)
            first_width = action_width(labels[0]["items"][0]["label"])
            for index, (label, icon) in enumerate(zip(labels, icons)):
                x = fx+12 if index == 0 else fx+12+first_width+4
                corners = dict(tl=20, tr=6, bl=20, br=6) if index == 0 else dict(tl=6, tr=20, bl=6, br=20)
                additions.extend(action_parts(label["id"]+"-compact", x, row_y,
                                               label["items"][0]["label"], icon["items"][0]["icon"],
                                               corners=corners))

        for group in groups:
            item = group["items"][0]
            label = item.get("label", "")
            if item["kind"] != "button" or not label.startswith("添加"):
                continue
            remove.add(group["id"])
            additions.extend(action_parts(group["id"]+"-compact", fx+12, group["y"], label, item.get("icon"),
                                           text_only=label == "添加验证码"))

        doc["groups"] = [g for g in doc["groups"] if g["id"] not in remove]+additions
        frame["note"] = frame.get("note", "").replace("完整类型选择行", "紧凑类型按钮组").replace(
            "整行可点", "点击名称或箭头打开选择").replace("正文类型行", "正文类型按钮组")
    return doc
