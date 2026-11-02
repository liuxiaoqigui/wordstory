"""把 gptwords.json（GPT 单词本）和 ecdict.csv 合并成 App 用的 words.db。

用法: python3 -I build_db.py gptwords.json ecdict.csv ../app/src/main/assets/words.db
"""
import csv, json, os, re, sqlite3, sys

# 标题关键字 -> 统一的小节名。按顺序匹配："单词发展历史和文化背景"先命中"历史"，"词根词缀分析"先命中"词根"。
KEYWORDS = [("故事", "小故事"), ("记忆", "记忆辅助"), ("历史", "发展历史和文化背景"), ("文化", "发展历史和文化背景"),
            ("例句", "列举例句"), ("举例", "列举例句"), ("例子", "列举例句"), ("示例", "列举例句"),
            ("变形", "单词变形"), ("形变", "单词变形"), ("搭配", "单词变形"), ("组词", "单词变形"),
            ("词根", "词根分析"), ("词缀", "词缀分析"),
            ("词义", "分析词义"), ("词意", "分析词义"), ("释义", "分析词义"), ("单词分析", "分析词义"), ("单词解析", "分析词义")]
SECTION_ORDER = ["分析词义", "列举例句", "词根分析", "词缀分析", "发展历史和文化背景", "单词变形", "记忆辅助", "小故事"]
# 没有 #/**/【 标记的普通行，只有以这些词结尾才算标题，免得"故事联想：""例如："这类正文被当成标题
PLAIN_END = ("分析", "例句", "举例", "例子", "示例", "背景", "变形", "形变", "搭配", "组词", "辅助", "故事",
             "词义", "释义", "解析", "列举", "展示", "词缀")
NUM = r"(?:(?:\d+|[一二三四五六七八九十]+)\s*[.、．]\s*)?"
MARKED = re.compile(r"^\s*(?:#+|\*\*|【)\s*" + NUM + r"(?:\*\*)?\s*([^*#【】:：]{1,14}?)\s*(?:\*\*|】)?\s*"
                    r"(?:[:：]\s*(?:\*\*)?\s*(.*))?$")
PLAIN = re.compile(r"^\s*" + NUM + r"([^*#【】:：\s]{2,10})\s*(?:[:：]\s*(.*))?$")
MD_ESCAPE = re.compile(r"\\([\\`*_{}\[\]()#+\-.!])")  # "2\. 列举例句" -> "2. 列举例句"
CJK = re.compile(r"[　-〿㐀-鿿＀-￯]")  # 汉字和全角标点
NON_CJK_RUN = re.compile(r"[^　-〿㐀-鿿＀-￯]+")
LIST_ITEM = re.compile(r"^\s*(?:\d+[.)、]|•)\s*")
STRIP = " \t-—–:：/|\"'“”‘’()（）[]【】"
# ECDICT 音标用西里尔字母 ә 和 ASCII 重音/长音符号，换成标准 IPA
IPA = str.maketrans({"ә": "ə", "'": "ˈ", ",": "ˌ", ":": "ː"})
LEVEL_TAGS = [("zk", 1), ("gk", 2), ("cet4", 3), ("cet6", 4)]  # 其他 = 5


def heading(line):
    """是标题就返回 (小节名, 同一行冒号后的正文)，否则 None。"""
    m = MARKED.match(line)
    if not m:
        m = PLAIN.match(line)
        if not m or not m.group(1).endswith(PLAIN_END):
            return None
    text = m.group(1).strip()
    if text.startswith("例如"):
        return None
    for k, title in KEYWORDS:
        if k in text:
            return title, (m.group(2) or "").strip()
    return None


def clean(text):
    """去掉空白行和 Markdown 的行尾双空格，小标题转成加粗，列表符号统一。"""
    out = []
    for line in text.split("\n"):
        line = line.rstrip()
        if not line.strip():
            if out and out[-1] != "":
                out.append("")
            continue
        m = re.match(r"^\s*#+\s*(.+)$", line)
        if m:
            line = "**" + m.group(1).strip("* ") + "**"
        line = re.sub(r"^(\s*)[*-]\s+", r"\1• ", line)       # 无序列表
        line = re.sub(r"^(\s*\d+\.)\s+", r"\1 ", line)        # "1.  xx" -> "1. xx"
        out.append(line)
    return "\n".join(out).strip()


def split_sections(content):
    """按标题切成 [{t: 小节名, b: 正文}]，按固定顺序排；标题前的内容小节名为空。同名小节合并。"""
    parts, title, buf = [], "", []
    for line in MD_ESCAPE.sub(r"\1", content).split("\n"):
        h = heading(line)
        if h and not (title == h[0] and buf):
            parts.append((title, "\n".join(buf)))
            title, buf = h[0], ([h[1]] if h[1] else [])
        else:
            buf.append(line)
    parts.append((title, "\n".join(buf)))
    merged = {}
    for t, b in parts:
        b = clean(b)
        if b:
            merged[t] = (merged[t] + "\n\n" + b) if t in merged else b
    order = {t: i for i, t in enumerate([""] + SECTION_ORDER)}
    return [{"t": t, "b": merged[t]} for t in sorted(merged, key=order.get)]


def parse_examples(body, forms, list_only=False):
    """抽 {en, zh, blank}：每条里找含目标词（或变形）的最长英文片段，同一条里的中文当翻译。
    blank 是挖空后的英文句子。list_only=True 时只看列表项（用于没有例句小节、从开头段落兜底）。"""
    items, cur, cur_is_list = [], [], False
    for line in body.split("\n"):
        is_item = bool(LIST_ITEM.match(line))
        if (is_item or not line.strip()) and cur:
            items.append((cur_is_list, " ".join(cur))); cur = []
        if line.strip():
            if not cur:
                cur_is_list = is_item
            cur.append(line.strip())
    if cur:
        items.append((cur_is_list, " ".join(cur)))

    pat = re.compile(r"(?<![A-Za-z])(" + "|".join(re.escape(f) for f in sorted(forms, key=len, reverse=True)) +
                     r")(?![A-Za-z])", re.IGNORECASE)
    out, seen = [], set()
    for is_list, it in items:
        if list_only and not is_list:
            continue
        it = LIST_ITEM.sub("", it).replace("**", "")
        best = None
        for m in NON_CJK_RUN.finditer(it):
            seg = m.group(0)
            if pat.search(seg) and len(re.findall(r"[A-Za-z]+", seg)) >= 3 and (not best or len(seg) > len(best.group(0))):
                best = m
        if not best:
            continue
        en = best.group(0).strip(STRIP)
        after, before = it[best.end():], it[:best.start()]
        zh = (after if CJK.search(after) else before).strip(STRIP)
        zh = re.sub(r"^(?:中文|翻译|中文翻译|译文|意思)[：:]\s*", "", zh).strip(STRIP)
        hit = pat.search(en)
        if not hit or not CJK.search(zh) or en in seen or (list_only and not re.search(r"[.!?]$", en)):
            continue
        seen.add(en)
        out.append({"en": en, "zh": zh, "blank": en[:hit.start()] + "_____" + en[hit.end():]})
    return out


def short_meaning(translation):
    """ECDICT 的 translation 是多行，取前两种词性、每种前 3 个义项，拼成选项文字。"""
    lines = [l.strip() for l in re.split(r"\\r|\\n|\r|\n", translation) if l.strip()]   # ECDICT 里混着字面的 \r \n
    lines = [l for l in lines if not re.match(r"^(?:[a-z]+\.\s*)?\[", l)][:2]   # 去掉 [网络]、art. [计] 这类领域义项
    lines = [", ".join(re.split(r"\s*[,，;；]\s*", l)[:3]) for l in lines]
    return "；".join(lines) or None


def pos_of(meaning):
    m = re.match(r"^([a-z]+)\.", meaning or "")
    if not m:
        return None
    p = m.group(1)
    return {"vt": "v", "vi": "v", "a": "adj", "ad": "adv"}.get(p, p)


TOKEN = re.compile(r"[A-Za-z]+(?:['’][A-Za-z]+)*")   # 和 App 里点词用的正则保持一致


def tokens_of(rows):
    """App 里会显示的英文（各小节正文 + 例句）中出现的所有词，小写；he's 也收 he。"""
    out = set()
    for r in rows:
        for text in [s["b"] for s in r["sections"]] + [e["en"] for e in r["examples"]]:
            for m in TOKEN.finditer(text):
                tok = m.group(0).replace("’", "'").lower()
                out.add(tok)
                if "'" in tok:
                    out.add(tok.split("'")[0])
    return out


def build_lookup(rows, lower):
    """点词查询表：token -> (显示的词, 音标, 释义, 单词书里的词条)。
    书里有的词用书里的数据；变形词（spiders、went）按 ECDICT 的原形查；两边都查不到的不收。"""
    book = {}
    for r in rows:   # 大小写重复时用小写那条：march 而不是 March
        k = r["word"].lower()
        if k not in book or r["word"] == k:
            book[k] = r
    out = {}
    for tok in tokens_of(rows) | set(book):
        ec = lower.get(tok)
        lemma = tok
        for part in ((ec or {}).get("exchange") or "").split("/"):
            k, _, v = part.partition(":")
            if k == "0" and v:
                lemma = v.lower()
        # 书里有这个词就用它本身（found 不要变成 find），否则用原形
        head = lemma if tok not in book and (lemma in book or lemma in lower) else tok
        if head in book:
            b = book[head]
            out[tok] = (b["word"], b["phonetic"], b["meaning"], b["word"])
        else:
            row = lower.get(head) or ec
            meaning = short_meaning(row.get("translation") or "") if row else None
            if meaning:
                out[tok] = (row["word"], (row.get("phonetic") or "").translate(IPA) or None, meaning, None)
    return out


def load_ecdict(path):
    exact, lower = {}, {}
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            w = row["word"]
            exact[w] = row
            lower.setdefault(w.lower(), row)
    return exact, lower


def main(gpt_path, ecdict_path, out_path):
    exact, lower = load_ecdict(ecdict_path)
    rows, seen = [], set()
    for line in open(gpt_path, encoding="utf-8"):
        d = json.loads(line)
        word = d["word"].strip()
        if not word or word in seen:   # 源数据里有 687 个词出现两次，保留第一条
            continue
        seen.add(word)
        ec = exact.get(word)
        # 全大写缩写（IT、US、PIN）不能借小写词的释义：IT ≠ it。带点的 A.M. -> a.m. 可以借。
        if ec is None and not (len(word) > 1 and word.isupper() and "." not in word):
            ec = lower.get(word.lower())
        ec = ec or {}
        forms = {word}
        for part in (ec.get("exchange") or "").split("/"):
            k, _, v = part.partition(":")
            if k in ("p", "d", "i", "3", "r", "t", "s") and v:
                forms.add(v)
        secs = split_sections(d["content"])
        by_title = {s["t"]: s["b"] for s in secs}
        examples = parse_examples(by_title.get("列举例句", ""), forms) or \
            parse_examples(by_title.get("", ""), forms, list_only=True)
        meaning = short_meaning(ec.get("translation") or "")
        tags = (ec.get("tag") or "").split()
        level = min([lv for t, lv in LEVEL_TAGS if t in tags] or [5])
        frq = int(ec.get("frq") or 0) or 10 ** 9   # 0 = 没有词频，排最后
        rows.append(dict(word=word, phonetic=(ec.get("phonetic") or "").translate(IPA) or None, meaning=meaning,
                         pos=pos_of(meaning), level=level, frq=frq, sections=secs, examples=examples))

    rows.sort(key=lambda r: (r["level"], r["frq"], r["word"].lower()))
    # 原书把句首大写形式也收成了单独词条（About/about、In/in、IT/it）。小写形式存在时，大写的只留给查词，
    # 不进关卡：lesson=0。March（三月）这种大写另有含义的也照此处理，故事在查词里照样能看到。
    lower_words = {r["word"] for r in rows if r["word"] == r["word"].lower()}
    for r in rows:
        r["quiz"] = r["word"] == r["word"].lower() or r["word"].lower() not in lower_words
    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    db = sqlite3.connect(out_path)
    db.execute("PRAGMA page_size=16384")  # 每行约 2.4KB，默认 4KB 页只能放一行，浪费近一半
    db.execute("""CREATE TABLE words(
        id INTEGER PRIMARY KEY, word TEXT NOT NULL UNIQUE, phonetic TEXT, meaning TEXT, pos TEXT,
        level INTEGER NOT NULL, lesson INTEGER NOT NULL, sections TEXT NOT NULL, examples TEXT NOT NULL)""")
    lesson, prev_level, n_in_lesson = 0, None, 0
    for i, r in enumerate(rows, 1):
        if r["quiz"]:
            if r["level"] != prev_level or n_in_lesson == 10:   # 每关 10 词，换等级就换关
                lesson += 1; n_in_lesson = 0; prev_level = r["level"]
            n_in_lesson += 1
        db.execute("INSERT INTO words VALUES(?,?,?,?,?,?,?,?,?)",
                   (i, r["word"], r["phonetic"], r["meaning"], r["pos"], r["level"], lesson if r["quiz"] else 0,
                    json.dumps(r["sections"], ensure_ascii=False), json.dumps(r["examples"], ensure_ascii=False)))
    db.execute("CREATE INDEX words_lesson ON words(lesson)")
    lookup = build_lookup(rows, lower)
    db.execute("""CREATE TABLE lookup(token TEXT PRIMARY KEY, head TEXT NOT NULL, phonetic TEXT, meaning TEXT,
        story TEXT) WITHOUT ROWID""")
    db.executemany("INSERT INTO lookup VALUES(?,?,?,?,?)", [(t, *v) for t, v in lookup.items()])
    print(f"lookup tokens={len(lookup)}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    report(rows, lesson, out_path)


def report(rows, lessons, out_path):
    n = len(rows)
    full = sum(1 for r in rows if {s["t"] for s in r["sections"]} >= set(SECTION_ORDER))
    pct = lambda k: f"{k / n:.1%}"
    print(f"words={n} in_lessons={sum(r['quiz'] for r in rows)} lessons={lessons} "
          f"size={os.path.getsize(out_path) / 1e6:.1f}MB")
    print("all 8 sections:", pct(full))
    print("has meaning:", pct(sum(1 for r in rows if r["meaning"])),
          " phonetic:", pct(sum(1 for r in rows if r["phonetic"])),
          " >=1 cloze example:", pct(sum(1 for r in rows if r["examples"])))
    print("levels:", {lv: sum(1 for r in rows if r["level"] == lv) for lv in range(1, 6)})


def self_check():
    s = split_sections("### 分析词义\n\n  \n\nhello 是问候。\n\n**例句：**\n\n1.  \"Hello there, Tom.\" - 你好，汤姆。\n"
                       "2.  He said hello to me.（他向我问好。）\n\n词缀分析：无词缀\n\n### 小故事\n\nTom said hello.\n汤姆说你好。")
    assert [x["t"] for x in s] == ["分析词义", "列举例句", "词缀分析", "小故事"], s
    assert s[2]["b"] == "无词缀", s[2]
    ex = parse_examples(s[1]["b"], {"hello"})
    assert ex[0] == {"en": "Hello there, Tom.", "zh": "你好，汤姆。", "blank": "_____ there, Tom."}, ex
    assert ex[1]["zh"] == "他向我问好。" and ex[1]["blank"] == "He said _____ to me.", ex
    # 正文里的"故事联想：""例如："不是标题；编号写在加粗里、点号被转义的标题要认出来
    assert [x["t"] for x in split_sections("### 记忆辅助\n故事联想：a\n例如：b\n### 小故事\nc")] == ["记忆辅助", "小故事"]
    s = split_sections("**1\\. 分析词义**  \n放弃\n\n**举例**\n\n*   He abandoned it. (他放弃了。)\n\n**6.单词变形和固定搭配**  \n无")
    assert [x["t"] for x in s] == ["分析词义", "列举例句", "单词变形"], s
    # 变形能挖空；词出现在别的词里面不算；中文在前英文在后、// 分隔、带"动词形式:"标签都能抽
    assert parse_examples(s[1]["b"], {"abandon", "abandoned"})[0]["blank"] == "He _____ it."
    assert parse_examples("1. Hello Tom. 你好汤姆。", {"hello"}) == []          # 只有 2 个英文词，太短
    assert parse_examples("1. They marched to the castle. 他们向城堡行进。", {"march", "marched"})[0]["blank"] \
        == "They _____ to the castle."
    assert parse_examples("1. A marching band played loudly. 乐队演奏。", {"march"}) == []
    assert parse_examples("1. 他在2001年成立了这家公司。(He **founded** the company in 2001.)", {"found", "founded"})[0] \
        == {"en": "He founded the company in 2001.", "zh": "他在2001年成立了这家公司。", "blank": "He _____ the company in 2001."}
    assert parse_examples('1. 动词形式:\n    "He punched the wall hard." // "他猛击墙壁。"', {"punch", "punched"})[0]["zh"] == "他猛击墙壁。"
    assert parse_examples('1. "I wake up at 7:00 A.M. daily." - 我每天早上7点起床。', {"A.M."})[0]["blank"] \
        == "I wake up at 7:00 _____ daily."
    # 开头段落兜底只看列表项，不抽解释性段落
    assert parse_examples('例如 "He had a strict upbringing" 时，意思是……', {"upbringing"}, list_only=True) == []
    assert short_meaning("n. 广告\\nabbr. 公元\\n[网络] 广告") == "n. 广告；abbr. 公元"
    assert short_meaning("第一个字母 A, 一个\\r\\nart. [计] 累加器\\r\\nn. 字母") == "第一个字母 A, 一个；n. 字母"
    assert short_meaning("n. 三月, 进行, 行军, 步伐\\nvi. 进军, 前进") == "n. 三月, 进行, 行军；vi. 进军, 前进"
    assert pos_of("vt. 放弃") == "v"
    # 点词查询：变形词查原形；书里有的词用书里的数据和词条；he's 也收 he；查不到的不收
    spider = dict(word="spider", phonetic="ˈspaidə", meaning="n. 蜘蛛", sections=[{"t": "", "b": "He's afraid of spiders. xyzzy"}],
                  examples=[{"en": "I went home.", "zh": "", "blank": ""}])
    lower = {"spiders": {"word": "spiders", "exchange": "0:spider/1:s", "translation": "n. 蜘蛛（spider复数）"},
             "spider": {"word": "spider", "translation": "n. 蜘蛛, 设圈套者"},
             "went": {"word": "went", "exchange": "0:go/1:p", "translation": "go的过去式"},
             "go": {"word": "go", "phonetic": "gəu", "translation": "v. 去"},
             "he": {"word": "he", "phonetic": "hi:", "translation": "pron. 他"}}
    lk = build_lookup([spider], lower)
    assert lk["spiders"] == ("spider", "ˈspaidə", "n. 蜘蛛", "spider"), lk["spiders"]
    assert lk["went"] == ("go", "gəu", "v. 去", None), lk["went"]
    assert lk["he"] == ("he", "hiː", "pron. 他", None) and "he's" not in lk and "xyzzy" not in lk, lk
    assert "ә'bænd:".translate(IPA) == "əˈbændː"


if __name__ == "__main__":
    self_check()
    main(*sys.argv[1:4])
