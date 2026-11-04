package com.wordstory.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var speech: WordSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = WordSpeech(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            CompositionLocalProvider(LocalWordSpeech provides speech) {
                DuoTheme { App() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        speech.resumePrefetch()
    }

    override fun onStop() {
        speech.stop()
        super.onStop()
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }
}

class Repo(context: Context) {
    val words = WordDb(context)
    val user = UserDb(context)
    val lessons by lazy { words.lessons() }
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

sealed interface Screen {
    data object Home : Screen
    data class Detail(val word: String) : Screen
    data class Lesson(val info: LessonInfo) : Screen
    data object Review : Screen
}

/** 首页的状态放在外面，从详情页返回时标签页、搜索词、列表位置都还在 */
class HomeState(private val prefs: SharedPreferences) {
    var tab by mutableIntStateOf(0)
    var query by mutableStateOf("")
    val lessonList = LazyListState()
    /** 正在学的难度；0 = 还没选过，首次打开时让用户选 */
    var level by mutableIntStateOf(prefs.getInt("level", 0)); private set

    fun selectLevel(value: Int) {
        level = value
        prefs.edit().putInt("level", value).apply()
    }
}

@Composable
fun App() {
    val context = LocalContext.current.applicationContext
    // 第一次启动要把 22MB 词库从 APK 拷出来，放到 IO 线程
    val repo by produceState<Repo?>(null) { value = withContext(Dispatchers.IO) { Repo(context) } }
    val r = repo
    if (r == null) {
        Box(Modifier.fillMaxSize().background(DuoWhite), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DuoGreen, strokeWidth = 4.dp)
        }
        return
    }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val home = remember { HomeState(r.prefs) }
    if (home.level == 0) {   // 第一次打开：先选从哪个难度开始
        LevelPicker(r) { home.selectLevel(it) }
        return
    }
    var refresh by remember { mutableIntStateOf(0) }   // 学完/复习完回首页时让首页重新读进度
    // removeAt 而不是 removeLast：后者在 Android 14 及以下会因 JDK 21 的同名方法崩溃
    val pop = { stack.removeAt(stack.lastIndex); Unit }
    BackHandler(stack.size > 1) { pop() }

    // 句子里点到的词。弹窗盖在当前页面上，不进页面栈，闯关做到一半查词也不会丢进度
    var lookupWord by remember { mutableStateOf<String?>(null) }
    val onWord = remember { { word: String -> lookupWord = word } }

    CompositionLocalProvider(LocalWordLookup provides onWord) {
        when (val s = stack.last()) {
            Screen.Home -> HomeScreen(r, home, refresh, open = { stack += it })
            is Screen.Detail -> DetailScreen(r.words, s.word, onBack = pop)
            is Screen.Lesson -> LessonScreen(r, s.info, onExit = { refresh++; pop() })
            Screen.Review -> ReviewScreen(r, onExit = { refresh++; pop() })
        }
        lookupWord?.let { WordSheet(r.words, it) { lookupWord = null } }
    }
}
