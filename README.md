# 抄表助手 (Meter Reader Assistant)

一款专为家庭和小型物业设计的水电气表计管理与费用计算 Android 应用，支持预付费/后付费双模式、阶梯计价、批量抄表、数据备份等功能。

## 功能特性

### 表计管理
- 支持电表、水表、气表、其他四种表计类型
- 预付费模式（按余额计费）和后付费模式（按读数计费）
- 阶梯分段计价，支持自定义多档价格
- 长按删除表计（含级联删除所有记录和分段）
- 拖拽排序表计卡片，顺序自动持久化
- 支持表计编号、单位等自定义信息

### 抄表记录
- 单条抄表：输入余额/读数，自动计算用量和费用
- 批量抄表：一页显示所有表计，快速连续录入
- 充值记录：预付费模式支持充值，后付费支持缴费
- 补录功能：支持自定义抄表时间（DatePicker/TimePicker）
- 用量模式：当期用量直接录入 / 累计读数自动差值计算
- 费用计算：预付费按余额差计算，后付费按读数差×阶梯单价计算
- 点击记录查看详情，长按编辑或删除

### 统计图表
- 按日/按月切换统计维度
- 用量趋势折线图（国网风格，青绿色填充区域）
- 费用趋势折线图（橙红色填充区域）
- 年份筛选，查看历史年度数据
- 详细数据表格同步展示

### 数据管理
- **CSV 导出**：三段式格式（表计信息 + 分段计价 + 记录数据），Excel 兼容
- **CSV 导入**：自动创建不存在的表计，按名称匹配，完整恢复分段计价
- **数据库备份**：一键备份全部数据，支持分享发送
- **数据库恢复**：从备份文件完整恢复所有数据
- **数据概览**：表计数量、记录总数、数据库大小、版本号

### 界面设计
- 全中文界面，Material Design 风格
- 自定义仪表盘图标（适配所有屏幕密度）
- 国网风格统计图表（填充区域折线图 + 圆点标记）
- 卡片式表计列表，余额/读数一目了然
- 底部导航快捷入口（批量抄表、数据管理）

## 计费逻辑

### 预付费模式（按余额）
- **费用** = 上次余额 - 本次余额（充值后自动更新余额，费用不受充值影响）
- **用量**：手工录入当期用量，或从费用按阶梯单价反推
- **充值**：余额增加，不产生费用

### 后付费模式（按读数）
- **用量** = 本次读数 - 上次读数
- **费用** = 按阶梯分段累进计算（跨档位累加）
- **缴费**：仅记录，不影响读数和费用

### 阶梯计价示例
| 阶梯 | 范围 (kWh) | 单价 (元) |
|------|------------|-----------|
| 第一档 | 0 ~ 180 | 0.56 |
| 第二档 | 181 ~ 350 | 0.61 |
| 第三档 | 350+ | 0.86 |

## 技术栈

- **语言**：Java (Android 原生)
- **数据库**：SQLite (SQLiteOpenHelper, DB v6)
- **图表库**：MPAndroidChart v3.1.0 (JitPack)
- **UI 组件**：Material Design, RecyclerView, CardView, FloatingActionButton
- **拖拽排序**：ItemTouchHelper
- **文件分享**：FileProvider
- **最低版本**：Android 7.0 (API 24)
- **目标版本**：Android 14 (API 34)
- **构建工具**：Gradle 8.2 + JDK 17

## 项目结构

`

app/src/main/java/com/example/datarecorder/
├── Meter.java              # 表计模型（计费模式、阶梯定价、费用计算）
├── Record.java             # 记录模型（用量/费用差值计算）
├── DatabaseHelper.java     # SQLite数据库 (v6, 含脏标记重算系统)
├── MainActivity.java       # 主页（表计列表、拖拽排序、删除）
├── AddMeterActivity.java   # 添加/编辑表计（计费模式、阶梯设置）
├── MeterDetailActivity.java# 表计详情（记录列表、添加记录、导出）
├── BatchRecordActivity.java# 批量抄表（所有表计一页录入）
├── RecordDetailActivity.java# 记录详情（查看、编辑、删除）
├── StatsActivity.java      # 统计图表（按日/按月、双折线图）
├── DataManageActivity.java # 数据管理（备份/恢复/CSV导入导出）
├── MeterAdapter.java       # 表计列表适配器
└── RecordAdapter.java      # 记录列表适配器

``

## 版本历史

| 版本 | 主要更新 |
|------|---------|
| v4.9 | 修复预付费充值后费用双重计算bug |
| v4.8 | CSV导出/导入包含分段计价信息 |
| v4.7 | 表计卡片拖拽排序 |
| v4.6 | 预付费费用改为余额差计算 |
| v4.5 | 长按删除表计 |
| v4.4 | 编辑表计时Spinner分段值修复 |
| v4.3 | 首个完整版本 |

## 下载安装

1. 从 [Releases](https://github.com/cnjmj/chaobiao-assistant/releases) 页面下载最新 APK
2. 在手机上允许安装未知来源应用
3. 点击安装即可使用

## 开发环境搭建

``bash
# 需要 JDK 17 和 Android SDK (API 34)
git clone https://github.com/cnjmj/chaobiao-assistant.git
cd chaobiao-assistant
./gradlew assembleDebug
``

APK 输出路径：pp/build/outputs/apk/debug/

## License

MIT License