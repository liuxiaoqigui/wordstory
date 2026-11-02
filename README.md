# 单词故事 WordStory

一个完全离线的 Android 背单词 App。像多邻国一样一关一关地学，每个单词都附带 GPT-4 写的“单词背后的故事”：词义、例句、词根词缀、发展历史和文化背景、单词变形、记忆技巧和一个英文小故事。

## 功能

- **闯关**：8027 个单词，其中 7954 个分成 798 关，每关 10 个词，按难度分为 入门 / 简单 / 困难 / 地狱 / 其他。每关先看学习卡片，再做测验（选出正确的意思、选词填空）。答错的题会放回队尾，全部答对才过关。
- **复习**：用盒子法安排间隔重复。答对升一格，复习间隔依次为 1、2、4、7、15 天，再答对就算掌握；答错回到第一格。
- **查词**：输入前缀搜索，查看完整的单词故事。
- **点词查词**：例句、填空题和故事里的英文单词下面有虚线，点一下弹出原形、音标和释义（spiders → spider，went → go），书里有的词还能接着看故事。
- **激励**：连续打卡天数、每日 20 XP 目标。
- **完全离线**：不申请任何权限，包括网络。学习进度只存在手机上。

### 等级说明

等级来自 ECDICT 的考试标签。一个词同时属于多个考试时，取最低的一级。

| App 里的等级 | 对应考试 |
|---|---|
| 入门 | 中考 |
| 简单 | 高考 |
| 困难 | 大学英语四级 |
| 地狱 | 大学英语六级 |
| 其他 | 不在以上四类里 |

同一级里按词频排序，常用词在前。每一级的第一关直接开放，之后要先学完上一关。

## 构建和安装

需要 Android Studio（使用它自带的 JDK）和 Android SDK 36。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开项目运行。手机需要在“开发者选项”里打开 USB 调试。

运行单元测试：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

说明：

- `settings.gradle.kts` 在 `google()` 前面加了阿里云镜像，因为本地访问 dl.google.com 不稳定。能直连 Google 的话可以删掉那两个 `maven {}` 块。
- `app/build.gradle.kts` 固定了 `buildToolsVersion = "36.1.0"`，本机没有这个版本时改成已安装的版本。
- release 包也用 debug 签名，方便自己安装。

## 词库

`app/src/main/assets/words.db` 已经生成好，直接构建即可。想修改解析规则或重新生成时：

```bash
curl -LO https://raw.githubusercontent.com/Ceelog/DictionaryByGPT4/main/gptwords.json
curl -LO https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv
python3 -I tools/build_db.py gptwords.json ecdict.csv app/src/main/assets/words.db
```

脚本会先跑自检，再打印数据覆盖情况。当前数据：

| 指标 | 数值 |
|---|---|
| 单词数（去重后） | 8027 |
| 8 个小节都识别出来 | 94.7% |
| 有中文释义 | 99.0% |
| 有音标 | 97.8% |
| 至少有一个可挖空的例句 | 98.6% |
| 点词查询表 | 27291 个词形 |

`words.db` 里有两张表：

- `words`：单词、音标、释义、词性、等级、关卡、故事小节（JSON）、可挖空的例句（JSON）。`lesson = 0` 的是大小写重复的词条（如 About / about），只供查词，不进关卡。
- `lookup`：点词查询表。App 里所有英文中出现过的词形，都对应到原形、音标、释义，以及单词书里可以看故事的词条。

学习进度存在手机上单独的 `user.db` 里，按单词记录，重新生成词库不会丢进度。

## 项目结构

| 路径 | 内容 |
|---|---|
| `tools/build_db.py` | 把 GPT 单词本和 ECDICT 合并成 `words.db` |
| `app/src/main/java/com/wordstory/app/Data.kt` | 只读词库和学习进度数据库 |
| `.../Learn.kt` | 复习间隔、连续打卡、出题、答题队列 |
| `.../MainActivity.kt` | 入口和页面切换 |
| `.../HomeScreen.kt` | 首页：闯关路径、复习、查词 |
| `.../StudyScreens.kt` | 单词详情、学习卡片、测验、结算、复习 |
| `.../WordLookup.kt` | 点词查词：带虚线的文字和底部弹窗 |
| `.../Theme.kt` | 配色、字体、按钮等界面组件 |
| `app/src/test/.../LearnTest.kt` | 复习算法和答题队列的单元测试 |

## 数据来源与许可

源代码以 [MIT 许可](LICENSE) 发布。MIT 只覆盖代码，词库和字体各有自己的许可：

- 单词故事来自 [Ceelog/DictionaryByGPT4](https://github.com/Ceelog/DictionaryByGPT4)（威威的 GPT 单词本），许可为 [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)。`words.db` 由它重新整理而来（切分小节、抽取例句、合并音标和释义），因此 `words.db` 同样以 CC BY-SA 4.0 发布。
- 音标、释义、考试标签和词形变化来自 [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)，MIT 许可，见 [licenses/ECDICT-MIT.txt](licenses/ECDICT-MIT.txt)。
- 字体为 [Nunito](https://github.com/googlefonts/nunito)，SIL Open Font License 1.1，见 [licenses/OFL-Nunito.txt](licenses/OFL-Nunito.txt)。

单词故事由 GPT-4 生成，可能有错。例如 AD 只解释了“广告”，没有提到“公元”。
