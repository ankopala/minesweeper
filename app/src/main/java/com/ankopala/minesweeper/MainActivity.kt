package com.ankopala.minesweeper

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankopala.minesweeper.ui.theme.MinesweeperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinesweeperTheme {
                MinesweeperScreen()
            }
        }
    }
}

// 数字 1~8 的经典配色
private val numberColors = listOf(
    Color(0xFF1976D2), // 1 蓝
    Color(0xFF388E3C), // 2 绿
    Color(0xFFD32F2F), // 3 红
    Color(0xFF303F9F), // 4 深蓝
    Color(0xFF7B1FA2), // 5 紫
    Color(0xFF00838F), // 6 青
    Color(0xFF212121), // 7 黑
    Color(0xFF757575), // 8 灰
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinesweeperScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("minesweeper_settings", 0) }

    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    // 从持久化读取插旗模式，默认长按
    var flagMode by remember {
        mutableStateOf(
            FlagMode.valueOf(prefs.getString("flag_mode", FlagMode.LONG_PRESS.name)!!)
        )
    }
    // 模式切换按钮玩法下的「当前动作」：true=插旗，false=翻开
    var flagging by remember { mutableStateOf(false) }
    // 设置对话框开关
    var showSettings by remember { mutableStateOf(false) }
    // 计时开关（持久化）
    var timerEnabled by remember {
        mutableStateOf(prefs.getBoolean("timer_enabled", false))
    }
    // 屏幕方向模式（持久化）
    var orientationMode by remember {
        mutableStateOf(
            OrientationMode.valueOf(prefs.getString("orientation_mode", OrientationMode.PORTRAIT.name)!!)
        )
    }
    // 胜利结算弹窗
    var showResult by remember { mutableStateOf(false) }
    // 本次胜利的用时 & 是否刷新了最佳纪录（供弹窗展示）
    var lastTime by remember { mutableStateOf(0L) }
    var newBest by remember { mutableStateOf(false) }

    val game = remember { MinesweeperGame(Difficulty.EASY) }

    // 检测屏幕方向，同步到游戏逻辑（高级盘横竖屏行列互换）
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape) {
        game.setOrientation(isLandscape)
    }

    // 应用屏幕方向设置到 Activity
    val activity = context as? android.app.Activity
    LaunchedEffect(orientationMode) {
        activity?.requestedOrientation = when (orientationMode) {
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun newGame() {
        game.newGame()
        flagging = false
        showResult = false
    }

    fun setFlagMode(mode: FlagMode) {
        flagMode = mode
        prefs.edit().putString("flag_mode", mode.name).apply()
        flagging = false
    }

    fun setTimerEnabled(enabled: Boolean) {
        timerEnabled = enabled
        prefs.edit().putBoolean("timer_enabled", enabled).apply()
    }

    fun setOrientationMode(mode: OrientationMode) {
        orientationMode = mode
        prefs.edit().putString("orientation_mode", mode.name).apply()
    }

    // 读取某难度的历史最佳（秒）。没有记录返回 null
    fun bestTime(d: Difficulty): Long? {
        val v = prefs.getLong("best_${d.name}", -1L)
        return if (v < 0) null else v
    }

    // 胜利后调用：若计时开启，保存最佳成绩并弹出结算
    fun handleWin() {
        if (!timerEnabled) return
        val t = game.elapsedSeconds
        lastTime = t
        val prev = bestTime(difficulty)
        newBest = prev == null || t < prev
        if (newBest) {
            prefs.edit().putLong("best_${difficulty.name}", t).apply()
        }
        showResult = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫雷 Minesweeper") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 难度选择
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                Difficulty.entries.forEachIndexed { index, d ->
                    SegmentedButton(
                        selected = difficulty == d,
                        onClick = {
                            difficulty = d
                            game.changeDifficulty(d)
                            flagging = false
                            showResult = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Difficulty.entries.size
                        )
                    ) {
                        Text(d.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 状态栏：剩余雷数 + 状态 + 重新开始
            val remaining = game.mineCount - game.flagsUsed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💣 $remaining",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = when (game.status) {
                        GameStatus.PLAYING -> "进行中"
                        GameStatus.WON -> "🎉 赢了！"
                        GameStatus.LOST -> "💥 踩雷了"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (game.status) {
                        GameStatus.WON -> Color(0xFF388E3C)
                        GameStatus.LOST -> Color(0xFFD32F2F)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = { newGame() }) {
                    Text("重新开始")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 模式切换按钮玩法下，显示「插旗/翻开」切换按钮
            if (flagMode == FlagMode.TOGGLE_BUTTON) {
                Button(
                    onClick = { flagging = !flagging },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (flagging) "🚩 当前：插旗（点此切回翻开）" else "⛏️ 当前：翻开（点此切到插旗）")
                }
                Spacer(Modifier.height(8.dp))
            }

            // 操作提示（随模式动态变化）
            Text(
                text = when (flagMode) {
                    FlagMode.LONG_PRESS ->
                        "点击翻开 · 长按插旗 🚩 · 点击数字格快速展开"
                    FlagMode.TOGGLE_BUTTON ->
                        "单击按上方按钮执行 · 点击数字格快速展开"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // 棋盘
            Board(
                game = game,
                onReveal = { r, c ->
                    if (game.status != GameStatus.PLAYING) return@Board
                    val cell = game.cell(r, c)
                    if (cell.state == CellState.REVEALED) {
                        // 已翻开的数字格 → 快速翻开（chord）
                        game.chordReveal(r, c)
                    } else if (flagMode == FlagMode.TOGGLE_BUTTON && flagging) {
                        // 切换模式下且当前是「插旗」→ 插旗
                        game.toggleFlag(r, c)
                    } else {
                        game.reveal(r, c)
                    }
                    if (game.status == GameStatus.WON) handleWin()
                },
                onFlag = { r, c ->
                    if (game.status == GameStatus.PLAYING) {
                        game.toggleFlag(r, c)
                    }
                }
            )
        }
    }

    // 设置对话框
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("插旗方式", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    FlagMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { setFlagMode(mode) })
                        ) {
                            RadioButton(
                                selected = flagMode == mode,
                                onClick = { setFlagMode(mode) }
                            )
                            Text(mode.label)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // 计时开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { setTimerEnabled(!timerEnabled) })
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("计时与最佳记录")
                            Text(
                                "胜利后记录用时，并保存该难度历史最佳",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = timerEnabled,
                            onCheckedChange = { setTimerEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // 屏幕方向
                    Text("屏幕方向", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    OrientationMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { setOrientationMode(mode) })
                        ) {
                            RadioButton(
                                selected = orientationMode == mode,
                                onClick = { setOrientationMode(mode) }
                            )
                            Text(mode.label)
                        }
                    }
                    Text(
                        "横屏时高级盘会切换为 16 行 × 30 列的经典布局",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("完成")
                }
            }
        )
    }

    // 胜利结算弹窗（仅计时开启时）
    if (showResult) {
        val best = bestTime(difficulty)
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text("🎉 恭喜通关！") },
            text = {
                Column {
                    Text("难度：${difficulty.label}")
                    Spacer(Modifier.height(8.dp))
                    Text("本次用时：${formatTime(lastTime)}")
                    if (newBest) {
                        Spacer(Modifier.height(4.dp))
                        Text("🏆 新纪录！", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    } else if (best != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "历史最佳：${formatTime(best)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResult = false }) {
                    Text("好的")
                }
            }
        )
    }
}

// 把秒数格式化为 mm:ss 或 ss
private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}分${s}秒" else "${s}秒"
}

@Composable
fun Board(
    game: MinesweeperGame,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit
) {
    val cols = game.cols
    val rows = game.rows
    val gap = 2.dp
    // 用 BoxWithConstraints 拿到可用宽度，据此算出「横向铺满」的格子尺寸
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val availW = maxWidth
        // 每个格子是正方形：边长 = 可用宽 / 列数（横向铺满，纵向超出时滚动兜底）
        val cellSize = ((availW - gap * (cols - 1)) / cols).coerceAtLeast(24.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (col in 0 until cols) {
                        CellView(
                            cell = game.cell(row, col),
                            cellSize = cellSize,
                            onClick = { onReveal(row, col) },
                            onLongClick = { onFlag(row, col) }
                        )
                    }
                }
                Spacer(Modifier.height(gap))
            }
        }
    }
}

@Composable
fun CellView(
    cell: Cell,
    cellSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val background = when {
        cell.state == CellState.REVEALED && cell.isMine -> Color(0xFFD32F2F)
        cell.state == CellState.REVEALED -> Color(0xFFE0E0E0)
        else -> Color(0xFF90A4AE) // 未翻开 / 插旗
    }
    // 文字大小随格子尺寸缩放
    val contentFontSize = (cellSize.value * 0.55f).sp

    Box(
        modifier = Modifier
            .size(cellSize)
            .background(background, RoundedCornerShape(3.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            cell.state == CellState.FLAGGED -> {
                Text("🚩", fontSize = contentFontSize)
            }
            cell.state == CellState.REVEALED && cell.isMine -> {
                Text("💣", fontSize = contentFontSize)
            }
            cell.state == CellState.REVEALED && cell.adjacentMines > 0 -> {
                Text(
                    text = cell.adjacentMines.toString(),
                    fontSize = contentFontSize,
                    fontWeight = FontWeight.Bold,
                    color = numberColors[cell.adjacentMines - 1]
                )
            }
            // 空白格不显示任何内容
        }
    }
}
