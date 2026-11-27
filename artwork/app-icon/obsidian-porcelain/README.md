# 白骨瓷 + 黑曜石字标 启动图标

Mod 构建（`assembleMod`，包名 `com.android.purebilibili.mod`）使用的启动图标。构图沿用原 BiliPai 图标：
电视外框 + 横穿外框的字标。

- **白骨瓷**：瓷砖底与电视外框、天线。暖象牙白，左上釉面高光，暖色投影做出浮雕感，底部一抹透光。
- **黑曜石字标**：重新设计的 `bilipai`，单线圆头字形、逐字轻微倾斜；近黑火山玻璃材质，上沿高光与很弱的冷色虹彩，i 的圆点为黑曜石珠。
- 单色层（Android 13+ 主题图标）：字标实色、外框半透明。

`generate.py` 是唯一的形状来源，同时输出：

| 输出 | 位置 |
| --- | --- |
| SVG 母版与分层 SVG | 本目录 `obsidian-porcelain-*.svg` |
| 自适应图标背景 / 前景 / 单色层（VectorDrawable） | `app/src/mod/res/drawable/ic_launcher_obsidian_porcelain_*.xml` |
| 覆盖默认入口的自适应图标（含深色模式） | `app/src/mod/res/mipmap-anydpi-v26/`、`mipmap-night-anydpi-v26/` |
| 启动页图标 | `app/src/mod/res/drawable/`、`drawable-night/` 的 `splash_icon_blue_snow_maid.xml` |

修改后在仓库根目录重新生成：

```sh
python3 artwork/app-icon/obsidian-porcelain/generate.py
rsvg-convert -w 512 -h 512 artwork/app-icon/obsidian-porcelain/obsidian-porcelain-full.svg \
  -o artwork/app-icon/obsidian-porcelain/obsidian-porcelain-512.png
```

资源只放在 `mod` 构建类型的资源目录里，正式版（`release`）与 `dev` 的图标不受影响。
