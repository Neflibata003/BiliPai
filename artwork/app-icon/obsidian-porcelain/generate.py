#!/usr/bin/env python3
"""白骨瓷 × 黑曜石字标 启动图标生成器。

一份形状定义，同时输出：
  * SVG 母版（预览、导出 PNG 用）
  * Android VectorDrawable（自适应图标的背景 / 前景 / 单色图层）

坐标系为自适应图标的 108×108 画布：中心 66dp 圆是任何遮罩下都可见的安全区。

用法（仓库根目录）：
    python3 artwork/app-icon/obsidian-porcelain/generate.py
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Union

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
MOD_RES = REPO / "app" / "src" / "mod" / "res"


# ---------------------------------------------------------------------------
# 极简场景描述
# ---------------------------------------------------------------------------
@dataclass
class Linear:
    x1: float
    y1: float
    x2: float
    y2: float
    stops: list  # [(offset, "#AARRGGBB")]


@dataclass
class Radial:
    cx: float
    cy: float
    r: float
    stops: list


Paint = Union[str, Linear, Radial, None]


@dataclass
class Path_:
    d: str
    fill: Paint = None
    stroke: Paint = None
    stroke_width: float = 0.0
    cap: str = "butt"
    join: str = "miter"
    even_odd: bool = False


@dataclass
class Group:
    children: list
    translate: tuple = (0.0, 0.0)
    rotate: float = 0.0
    scale: tuple = (1.0, 1.0)
    clip: str | None = None


@dataclass
class Layer:
    name: str
    children: list = field(default_factory=list)


def num(x: float) -> str:
    """紧凑的数字格式，避免 5.4000000000000004 这类浮点尾巴进入资源文件。"""
    text = f"{x:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def rr(x: float, y: float, w: float, h: float, r: float) -> str:
    """圆角矩形路径（顺时针）。"""
    return (
        f"M{x + r:.2f},{y:.2f} H{x + w - r:.2f} A{r:.2f},{r:.2f} 0 0 1 {x + w:.2f},{y + r:.2f} "
        f"V{y + h - r:.2f} A{r:.2f},{r:.2f} 0 0 1 {x + w - r:.2f},{y + h:.2f} "
        f"H{x + r:.2f} A{r:.2f},{r:.2f} 0 0 1 {x:.2f},{y + h - r:.2f} "
        f"V{y + r:.2f} A{r:.2f},{r:.2f} 0 0 1 {x + r:.2f},{y:.2f} Z"
    )


def circle(cx: float, cy: float, r: float) -> str:
    return (
        f"M{cx - r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1 1 {cx + r:.2f},{cy:.2f} "
        f"A{r:.2f},{r:.2f} 0 1 1 {cx - r:.2f},{cy:.2f} Z"
    )


def arc(cx: float, cy: float, r: float, a0: float, a1: float) -> str:
    """以角度（度）描述的开放圆弧。"""
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    large = 1 if abs(a1 - a0) > 180 else 0
    sweep = 1 if a1 > a0 else 0
    return f"M{x0:.2f},{y0:.2f} A{r:.2f},{r:.2f} 0 {large} {sweep} {x1:.2f},{y1:.2f}"


# ---------------------------------------------------------------------------
# 设计：白骨瓷圆角瓷砖 + 骨瓷电视外框，黑曜石字标 "bilipai"
# 构图沿用原 BiliPai 图标（电视外框 + 横穿外框的字标），材质与字标重新设计。
# ---------------------------------------------------------------------------
# 骨瓷：暖象牙白，釉面高光，阴影用暖灰而不是冷灰
PORCELAIN_RING = Linear(27, 38, 81, 76, [
    (0.00, "#FFFFFFFF"),
    (0.45, "#FFF5F0E7"),
    (1.00, "#FFDCD0BC"),
])
PORCELAIN_SHADOW = "#2E5A4630"      # 暖棕 12%：瓷面上的投影
OBSIDIAN = Linear(0, 44, 0, 76, [   # 黑曜石：近黑火山玻璃，上沿稍透冷光
    (0.00, "#FF3B3E4B"),
    (0.40, "#FF111218"),
    (1.00, "#FF020203"),
])
OBSIDIAN_SHEEN = Linear(22, 44, 86, 76, [
    (0.00, "#00000000"),
    (0.40, "#1A3FA9F5"),
    (0.55, "#189B6CF0"),
    (0.70, "#1446D3C1"),
    (1.00, "#00000000"),
])
GLOSS = Linear(0, 44, 0, 64, [(0.0, "#66FFFFFF"), (0.45, "#1AFFFFFF"), (1.0, "#00FFFFFF")])

TV = (27.0, 38.0, 54.0, 38.0, 12.0)          # 外框中心线：x, y, w, h, r
RING_W = 5.2                                 # 外框粗细
ANTENNAS = ["M45,38 L39.6,29.8", "M63,38 L68.4,29.8"]

# 字标参数（中心线）：单线圆头字形，字高 / 字宽统一，逐字轻微倾斜形成手写的跳动感
S = 4.6            # 笔画粗细
R = 4.6            # 字碗半径（中心线）
XH_TOP = 54.6      # x-height 顶
BASE = XH_TOP + 2 * R   # 基线
BOWL_Y = XH_TOP + R
ASC = BASE - 18.2  # 升部顶
DESC = BASE + 8.6  # 降部底
GAP = 1.2
# (字形, 旋转角度, 上下浮动)
WORD = [("b", -6, 0.0), ("i", 5, -1.0), ("l", -3, 0.4), ("i", 6, -0.8),
        ("p", -5, 0.5), ("a", 4, -0.6), ("i", -5, 0.7)]


def _glyph_width(ch: str) -> float:
    return 2 * R + S if ch in "bpa" else S


def _glyph(ch: str, x: float) -> tuple[list[str], list[tuple[float, float, float]], float]:
    """返回 (笔画路径, 圆点 [(cx, cy, r)], 字形中心 x)。x 为字形外沿左边界。"""
    half = S / 2
    if ch == "b":
        stem = x + half
        return [f"M{num(stem)},{num(ASC)} V{num(BASE)}", circle(stem + R, BOWL_Y, R)], [], stem + R / 2
    if ch == "p":
        stem = x + half
        return [f"M{num(stem)},{num(XH_TOP)} V{num(DESC)}", circle(stem + R, BOWL_Y, R)], [], stem + R / 2
    if ch == "a":
        cx = x + half + R
        stem = cx + R
        return [circle(cx, BOWL_Y, R), f"M{num(stem)},{num(XH_TOP)} V{num(BASE)}"], [], cx + R / 2
    if ch == "l":
        c = x + half
        return [f"M{num(c)},{num(ASC)} V{num(BASE)}"], [], c
    # i：竖画 + 圆点
    c = x + half
    return [f"M{num(c)},{num(XH_TOP)} V{num(BASE)}"], [(c, XH_TOP - 5.4, 2.55)], c


def _wordmark_glyphs():
    total = sum(_glyph_width(ch) for ch, _, _ in WORD) + GAP * (len(WORD) - 1)
    x = 54 - total / 2
    out = []
    for ch, angle, dy in WORD:
        strokes, dots, cx = _glyph(ch, x)
        out.append((strokes, dots, cx, angle, dy))
        x += _glyph_width(ch) + GAP
    return out


def _tilted(cx: float, angle: float, dy: float, children: list) -> Group:
    """绕字形中心旋转，并整体上下浮动 dy。"""
    return Group(translate=(cx, BOWL_Y + dy), rotate=angle,
                 children=[Group(translate=(-cx, -BOWL_Y), children=children)])


def wordmark(mono: bool = False) -> list:
    nodes = []
    for strokes, dots, cx, angle, dy in _wordmark_glyphs():
        g = []
        if mono:
            g += [Path_(d, stroke="#FFFFFFFF", stroke_width=S, cap="round", join="round") for d in strokes]
            g += [Path_(circle(x, y, r), fill="#FFFFFFFF") for x, y, r in dots]
            nodes.append(_tilted(cx, angle, dy, g))
            continue
        # 落在瓷面上的投影：两层略粗、略偏移的暖色描边叠出柔边
        for (ox, oy, extra, color) in [(0.9, 1.6, 1.8, "#145A4630"), (0.5, 0.9, 0.8, "#1F5A4630")]:
            g.append(Group(translate=(ox, oy), children=
                [Path_(d, stroke=color, stroke_width=S + extra, cap="round", join="round") for d in strokes] +
                [Path_(circle(x, y, r + extra / 2), fill=color) for x, y, r in dots]))
        # 黑曜石本体 + 虹彩
        g += [Path_(d, stroke=OBSIDIAN, stroke_width=S, cap="round", join="round") for d in strokes]
        g += [Path_(circle(x, y, r), fill=Radial(x - 0.8, y - 0.9, r * 1.6, [
            (0.0, "#FF3B3E4B"), (0.6, "#FF0E0F14"), (1.0, "#FF020203")])) for x, y, r in dots]
        g += [Path_(d, stroke=OBSIDIAN_SHEEN, stroke_width=S, cap="round", join="round") for d in strokes]
        # 玻璃质的上沿高光：中心线偏左上一点的细线，始终落在笔画内部
        g.append(Group(translate=(-0.55, -0.75), children=
            [Path_(d, stroke=GLOSS, stroke_width=0.9, cap="round", join="round") for d in strokes]))
        g += [Path_(circle(x - 0.8, y - 0.9, 0.75), fill="#B3FFFFFF") for x, y, r in dots]
        nodes.append(_tilted(cx, angle, dy, g))
    return nodes


def _tv_strokes(color, width: float) -> list:
    x, y, w, h, r = TV
    return [Path_(rr(x, y, w, h, r), stroke=color, stroke_width=width, join="round")] + [
        Path_(d, stroke=color, stroke_width=width - 0.6, cap="round") for d in ANTENNAS]


def background() -> Layer:
    return Layer("background", [
        # 骨瓷底：中心偏左上最亮，向边缘过渡到暖象牙色，让白色图标在白色桌面上仍有轮廓
        Path_("M0,0 H108 V108 H0 Z", fill=Radial(38, 30, 112, [
            (0.00, "#FFFCFAF6"),
            (0.40, "#FFF4EFE6"),
            (0.75, "#FFE8E0D2"),
            (1.00, "#FFD9CDBA"),
        ])),
        # 釉面大高光
        Group([Path_(circle(0, 0, 1), fill=Radial(0, 0, 1, [
            (0.00, "#B3FFFFFF"), (0.6, "#33FFFFFF"), (1.00, "#00FFFFFF")]))],
            translate=(34, 26), rotate=-32, scale=(34, 13)),
        # 骨瓷透光：底部一抹暖光
        Group([Path_(circle(0, 0, 1), fill=Radial(0, 0, 1, [
            (0.00, "#40F2DABA"), (1.00, "#00F2DABA")]))],
            translate=(60, 92), scale=(40, 12)),
    ])


def foreground() -> Layer:
    x, y, w, h, r = TV
    inner = rr(x + RING_W / 2, y + RING_W / 2, w - RING_W, h - RING_W, r - RING_W / 2)
    k = 0.9
    return Layer("foreground", [Group(translate=(54 * (1 - k), 55 * (1 - k)), scale=(k, k), children=[
        # 屏幕区域：微微下凹，上沿一道暖灰内阴影
        Group([Path_(f"M0,{y} H108 V{y + 9} H0 Z", fill=Linear(0, y + RING_W / 2, 0, y + 9, [
            (0.0, "#335A4630"), (1.0, "#005A4630")]))], clip=inner),
        # 瓷框投影
        Group(translate=(1.1, 2.0), children=_tv_strokes("#1F5A4630", RING_W + 2.2)),
        Group(translate=(0.4, 0.8), children=_tv_strokes(PORCELAIN_SHADOW, RING_W + 0.6)),
        # 骨瓷电视外框与天线
        *_tv_strokes(PORCELAIN_RING, RING_W),
        # 左上受光的釉面细线 / 右下背光的暖灰细线（都落在外框笔画内部）
        Group(translate=(-0.7, -0.9), children=_tv_strokes(Linear(27, 30, 81, 76, [
            (0.0, "#F2FFFFFF"), (0.55, "#40FFFFFF"), (1.0, "#00FFFFFF")]), 1.0)),
        Group(translate=(0.8, 1.0), children=_tv_strokes(Linear(27, 30, 81, 76, [
            (0.0, "#26A08A6C"), (0.5, "#4DA08A6C"), (1.0, "#8CA08A6C")]), 0.9)),
        # 黑曜石字标
        *wordmark(),
    ])])


def monochrome() -> Layer:
    k = 0.9
    return Layer("monochrome", [Group(translate=(54 * (1 - k), 55 * (1 - k)), scale=(k, k), children=[
        *_tv_strokes("#73FFFFFF", RING_W),
        *wordmark(mono=True),
    ])])


# ---------------------------------------------------------------------------
# 输出：SVG
# ---------------------------------------------------------------------------
def _svg_color(c: str) -> tuple[str, float]:
    a = int(c[1:3], 16) / 255
    return "#" + c[3:], a


class SvgWriter:
    def __init__(self) -> None:
        self.defs: list[str] = []
        self.n = 0

    def paint(self, p: Paint) -> tuple[str, float]:
        if p is None:
            return "none", 1.0
        if isinstance(p, str):
            return _svg_color(p)
        self.n += 1
        gid = f"g{self.n}"
        stops = "".join(
            f'<stop offset="{o}" stop-color="{_svg_color(c)[0]}" stop-opacity="{_svg_color(c)[1]:.3f}"/>'
            for o, c in p.stops
        )
        if isinstance(p, Linear):
            self.defs.append(
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                f'x1="{p.x1}" y1="{p.y1}" x2="{p.x2}" y2="{p.y2}">{stops}</linearGradient>'
            )
        else:
            self.defs.append(
                f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                f'cx="{p.cx}" cy="{p.cy}" r="{p.r}">{stops}</radialGradient>'
            )
        return f"url(#{gid})", 1.0

    def node(self, n) -> str:
        if isinstance(n, Group):
            tx, ty = n.translate
            sx, sy = n.scale
            clip_attr = ""
            if n.clip:
                self.n += 1
                cid = f"c{self.n}"
                self.defs.append(f'<clipPath id="{cid}"><path d="{n.clip}"/></clipPath>')
                clip_attr = f' clip-path="url(#{cid})"'
            inner = "".join(self.node(c) for c in n.children)
            return (f'<g transform="translate({num(tx)},{num(ty)}) rotate({num(n.rotate)}) scale({num(sx)},{num(sy)})"{clip_attr}>'
                    f"{inner}</g>")
        fill, fo = self.paint(n.fill)
        stroke, so = self.paint(n.stroke)
        attrs = [f'd="{n.d}"', f'fill="{fill}"', f'fill-opacity="{fo:.3f}"']
        if n.even_odd:
            attrs.append('fill-rule="evenodd"')
        if n.stroke is not None:
            attrs += [f'stroke="{stroke}"', f'stroke-opacity="{so:.3f}"', f'stroke-width="{n.stroke_width}"',
                      f'stroke-linecap="{n.cap}"', f'stroke-linejoin="{n.join}"']
        return f"<path {' '.join(attrs)}/>"


def to_svg(layers: list[Layer], size: int = 108) -> str:
    w = SvgWriter()
    body = "".join(w.node(c) for layer in layers for c in layer.children)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">'
            f"<defs>{''.join(w.defs)}</defs>{body}</svg>\n")


# ---------------------------------------------------------------------------
# 输出：Android VectorDrawable
# ---------------------------------------------------------------------------
def _vd_gradient(p: Paint, attr: str, indent: str) -> str:
    items = "\n".join(
        f'{indent}            <item android:offset="{o}" android:color="{c}" />' for o, c in p.stops
    )
    if isinstance(p, Linear):
        head = (f'<gradient android:type="linear" android:startX="{num(p.x1)}" android:startY="{num(p.y1)}" '
                f'android:endX="{num(p.x2)}" android:endY="{num(p.y2)}">')
    else:
        head = (f'<gradient android:type="radial" android:centerX="{num(p.cx)}" android:centerY="{num(p.cy)}" '
                f'android:gradientRadius="{num(p.r)}">')
    return (f'{indent}    <aapt:attr name="android:{attr}">\n'
            f"{indent}        {head}\n{items}\n{indent}        </gradient>\n"
            f"{indent}    </aapt:attr>\n")


def _vd_node(n, indent: str = "    ") -> str:
    if isinstance(n, Group):
        tx, ty = n.translate
        sx, sy = n.scale
        attrs = []
        if (tx, ty) != (0, 0):
            attrs += [f'android:translateX="{num(tx)}"', f'android:translateY="{num(ty)}"']
        if n.rotate:
            attrs.append(f'android:rotation="{num(n.rotate)}"')
        if (sx, sy) != (1, 1):
            attrs += [f'android:scaleX="{num(sx)}"', f'android:scaleY="{num(sy)}"']
        out = f"{indent}<group {' '.join(attrs)}>\n" if attrs else f"{indent}<group>\n"
        if n.clip:
            out += f'{indent}    <clip-path android:pathData="{n.clip}" />\n'
        out += "".join(_vd_node(c, indent + "    ") for c in n.children)
        return out + f"{indent}</group>\n"
    attrs = [f'android:pathData="{n.d}"']
    inner = ""
    if isinstance(n.fill, str):
        attrs.append(f'android:fillColor="{n.fill}"')
    elif n.fill is not None:
        inner += _vd_gradient(n.fill, "fillColor", indent)
    if n.even_odd:
        attrs.append('android:fillType="evenOdd"')
    if n.stroke is not None:
        if isinstance(n.stroke, str):
            attrs.append(f'android:strokeColor="{n.stroke}"')
        else:
            inner += _vd_gradient(n.stroke, "strokeColor", indent)
        attrs.append(f'android:strokeWidth="{num(n.stroke_width)}"')
        if n.cap != "butt":
            attrs.append(f'android:strokeLineCap="{n.cap}"')
        if n.join != "miter":
            attrs.append(f'android:strokeLineJoin="{n.join}"')
    sep = f"\n{indent}    "
    if inner:
        return f"{indent}<path{sep}{sep.join(attrs)}>\n{inner}{indent}</path>\n"
    return f"{indent}<path{sep}{sep.join(attrs)} />\n"


def to_vector(layer: Layer) -> str:
    body = "".join(_vd_node(c) for c in layer.children)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- 由 artwork/app-icon/obsidian-porcelain/generate.py 生成，请勿手改。 -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        f"{body}</vector>\n"
    )


ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Mod 构建：白骨瓷 + 黑曜石字标启动图标，覆盖默认的「蓝雪女仆」入口，便于与官方版区分。 -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_obsidian_porcelain_background" />
    <foreground android:drawable="@drawable/ic_launcher_obsidian_porcelain_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_obsidian_porcelain_monochrome" />
</adaptive-icon>
"""

SPLASH = """<?xml version="1.0" encoding="utf-8"?>
<!-- Mod 构建的启动页图标：与桌面图标同一套白骨瓷 + 黑曜石字标图层。 -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_launcher_obsidian_porcelain_background" />
    <item android:drawable="@drawable/ic_launcher_obsidian_porcelain_foreground" />
</layer-list>
"""


def main() -> None:
    bg, fg, mono = background(), foreground(), monochrome()
    (HERE / "obsidian-porcelain-full.svg").write_text(to_svg([bg, fg]), encoding="utf-8")
    (HERE / "obsidian-porcelain-background.svg").write_text(to_svg([bg]), encoding="utf-8")
    (HERE / "obsidian-porcelain-foreground.svg").write_text(to_svg([fg]), encoding="utf-8")
    (HERE / "obsidian-porcelain-monochrome.svg").write_text(to_svg([mono]), encoding="utf-8")

    drawable = MOD_RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_obsidian_porcelain_background.xml").write_text(to_vector(bg), encoding="utf-8")
    (drawable / "ic_launcher_obsidian_porcelain_foreground.xml").write_text(to_vector(fg), encoding="utf-8")
    (drawable / "ic_launcher_obsidian_porcelain_monochrome.xml").write_text(to_vector(mono), encoding="utf-8")

    # 默认入口与深色模式入口都要覆盖：main 里的 night 资源限定符比 mod 的默认资源更具体。
    for folder in ("mipmap-anydpi-v26", "mipmap-night-anydpi-v26"):
        out = MOD_RES / folder
        out.mkdir(parents=True, exist_ok=True)
        for name in ("ic_launcher_blue_snow_maid.xml", "ic_launcher_blue_snow_maid_round.xml"):
            (out / name).write_text(ADAPTIVE, encoding="utf-8")
    for folder in ("drawable", "drawable-night"):
        out = MOD_RES / folder
        out.mkdir(parents=True, exist_ok=True)
        (out / "splash_icon_blue_snow_maid.xml").write_text(SPLASH, encoding="utf-8")
    print("generated:", HERE, MOD_RES)


if __name__ == "__main__":
    main()
