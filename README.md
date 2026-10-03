# N-Reader 📖

> **A native, distraction-free e-book reader crafted specifically for Android TV & smart displays (16:9 landscape).**
>
> 专为安卓电视与大屏设备打造的原生双页沉浸式电子书阅读器。

<p align="center">
  <img src="docs/screenshots/bookshelf.png" alt="N-Reader Bookshelf" width="85%" />
</p>
<p align="center">
  <img src="docs/screenshots/reading_dual_page.png" alt="N-Reader Dual Page Reading" width="85%" />
</p>

---

## 🌐 English

### ✨ Key Features

- **📺 Crafted for 16:9 Big Screen Displays**
  - **Dual-Page Spread**: Naturally expands wide 16:9 TV screens into a side-by-side open book format with a subtle center spine.
  - **Zero-Clipping Line Engine (`BookPageView`)**: High-performance canvas clipping and translation based on single-pass `StaticLayout` line indices. Eliminates text truncation, double-typesetting inflation, and missing words between pages.
  - **Full-Bleed PDF Dual-Column Rendering**: Maximizes screen height and width for PDF documents with hardware-accelerated rasterization and center-spine alignment, removing ugly black bars.

- **📚 Multi-Format Support**
  - Seamlessly reads **EPUB**, **PDF**, **MOBI**, **AZW**, **AZW3**, and **TXT** files.
  - Automatic cover extraction, reading percentage tracking, and reading position persistence.

- **📑 Rich Hierarchical Table of Contents (TOC)**
  - Full multi-level chapter and section tree extraction (e.g. Chapter 1 ➔ 1.1 ➔ 1.2).
  - Accurate anchor and chapter jumping in both dual-page and single-scroll modes.
  - **D-Pad Left/Right Fast Page-Flipping**: Jump 7-8 chapters per click for ultra-fast navigation across long book catalogues.

- **🚀 3 TV-Friendly Book Import Methods**
  1. **LAN SMB / NAS Import**: Automated 32-thread LAN scanner discovering SMB servers (port 445) in ~2 seconds, automatic share detection, and batch folder import.
  2. **Wi-Fi Web Transfer**: Scan a QR code or visit a local IP address from any phone, tablet, or PC browser to effortlessly upload books to the TV.
  3. **Local Storage & USB Flash Drive**: Browse external USB drives or local storage with batch folder import and multi-file selection.

- **🎨 Typography & Personalization**
  - **8 Hand-Crafted Themes**: Teal, Gray, White, Beige, Light Gray, Dark Gray, Pure Black, and Brown.
  - **4 Typography Styles**: System Sans, Classical Serif, Monospace, and Clean Medium.
  - Adjustable font size, line spacing, and auto-indent paragraph formatting.
  - Subnet/format filtering and 5 sorting modes (Recent Read, Title, Progress, Date Added, Size).

- **🎮 100% D-Pad Remote Optimized**
  - Intuitive remote control: single-click `OK` reveals menu directly focused on Table of Contents; double-click `OK` opens TOC instantly; Left/Right/Up/Down navigation; Return focus auto-anchoring to the last read book.

---

### 📥 Download & Installation

Grab the latest compiled APK from the [Releases](https://github.com/noby338/N-Reader/releases) page, or build it locally using Gradle.

```bash
# Clone the repository
git clone https://github.com/noby338/N-Reader.git
cd N-Reader

# Build debug APK
./gradlew assembleDebug

# Deploy directly to ~/Downloads (macOS convenience script)
./gradlew deployToDownloads
```

---

### 🛠️ Tech Stack & Architecture

- **Platform**: Android TV / Android 5.0+ (API 21~34)
- **Language**: Pure Native Java (zero cross-platform runtime overhead, ultra-low memory footprint on constrained TV hardware)
- **PDF Engine**: Android Native `PdfRenderer`
- **Networking**: `smbj` (SMB2/SMB3 protocol support) & Embedded NanoHTTPD Wi-Fi server
- **QR Code Generation**: ZXing Core

---

## 🇨🇳 中文说明

### ✨ 核心亮点

- **📺 专为 16:9 大屏电视量身定制**
  - **仿实体书双页并排**：在电视横屏上舒展展开为左右双页排版，中缝自带典雅微阴影，还原沉浸纸质阅读感。
  - **自研零漏字渲染引擎（`BookPageView`）**：废弃切片重排方案，整章只生成唯一权威 `StaticLayout`，按完整行号区间动态裁剪平移绘制。彻底杜绝底端半行截断、两页交界漏字与排版高度虚胖问题。
  - **PDF 满屏双列阅读**：利用 Android 硬件级 `PdfRenderer` 并发光栅化，左右双页贴心中缝对齐，消除空洞黑边，大开本阅读清晰锐利。

- **📚 全主流格式支持**
  - 完美支持 **EPUB**、**PDF**、**MOBI**、**AZW**、**AZW3** 与 **TXT** 格式。
  - 自动提取内嵌封面、计算全书精确阅读百分比、断点记忆断章即开即读。

- **📑 完整树形多级目录与遥控器翻页**
  - 完整保留 NCX / Nav 多级层级结构（例如 `第1章 ➔ 1.1 ➔ 1.2`），避免子节标题冲刷章节名。
  - 支持直接点击具体小节并**精准定位跳转到对应跨页**。
  - **遥控器左右键跨页翻动**：针对几百章的长目录，按遥控器左右键单次飞跃 8 项，长目录找书秒级触达。

- **🚀 3 种电视专属极简传书方式**
  1. **局域网 SMB / NAS 共享传书**：32 线程并发嗅探局域网 445 端口（约 2 秒扫描完毕），自动列出电脑与 NAS 设备，自动探测共享文件夹，支持一键将整个目录图书全量下载到电视。
  2. **网页 Wi-Fi 隔空传书**：电视屏幕直接显示二维码和网址，手机或电脑扫码/访问网址即可秒传图书到电视。
  3. **U 盘与本地存储导入**：即插即用，支持多选导入与一键全量扫描导入当前文件夹。

- **🎨 丰富排版与电视个性化**
  - **8 款精调护眼主题**：青绿、中性灰、明亮白、柔和米色、浅灰、深灰、纯黑与复古棕。
  - **4 种系统预设字体**：系统黑体、典雅宋体、经典等宽、清晰适中。
  - 支持首行缩进、行距与字号微调；书架左上角直选 5 种排序（最近阅读、书名、进度、时间、大小）与格式分类过滤。

- **🎮 遥控器极致操控**
  - 单击 `OK` 键唤起菜单并默认直接聚焦【目录】；
  - 退出阅读器回到主页时，焦点自动停留在刚刚阅读的第一本书，操作行云流水。

---

### 📄 开源许可 (License)

本项目采用 [Apache License 2.0](LICENSE) 开源协议。
