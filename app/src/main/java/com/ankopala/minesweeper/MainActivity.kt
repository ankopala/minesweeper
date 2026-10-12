package com.ankopala.minesweeper

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankopala.minesweeper.ui.theme.MinesweeperTheme
import kotlinx.coroutines.delay

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

@Composable
fun MinesweeperScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("minesweeper_settings", 0) }

    var difficulty by remember { mutableStateOf(Difficulty.STANDARD) }
    // 插旗模式：默认「模式切换按钮」（经典玩法），可切回长按
    var flagMode by remember {
        mutableStateOf(
            FlagMode.valueOf(prefs.getString("flag_mode", FlagMode.TOGGLE_BUTTON.name)!!)
        )
    }
    // 模式切换玩法下的「当前动作」：true=插旗，false=翻开
    var flagging by remember { mutableStateOf(false) }
    // 设置对话框开关
    var showSettings by remember { mutableStateOf(false) }
    // 计时器显示开关（独立于记录开关，持久化）
    var timerVisible by remember {
        mutableStateOf(prefs.getBoolean("timer_visible", false))
    }
    // 计时与最佳记录开关（持久化）
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
    var lastTime by remember { mutableStateOf(0L) }
    var newBest by remember { mutableStateOf(false) }
    // 「重开确认」弹窗：仅在游戏进行中点击笑脸时弹出
    var showRestartConfirm by remember { mutableStateOf(false) }

    val game = remember { MinesweeperGame(Difficulty.STANDARD) }

    // 一次性迁移：老版本第三档存档 key 从 best_HARD 改成 best_EXPERT
    if (prefs.contains("best_HARD")) {
        val v = prefs.getLong("best_HARD", -1L)
        prefs.edit().remove("best_HARD").putLong("best_EXPERT", v).apply()
    }

    // 实时计时跳动的驱动：每 500ms 刷新一次
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(game.status, timerVisible) {
        if (timerVisible && game.status == GameStatus.PLAYING) {
            while (true) {
                tick = game.elapsedNow
                delay(500)
            }
        }
    }

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
        showRestartConfirm = false
    }

    // 点击笑脸时的分流逻辑：
    // - 游戏已结束（踩雷/排完）→ 直接开新局；
    // - 游戏进行中 → 弹确认框，避免点旗时误触笑脸导致前功尽弃。
    fun onSmileyClick() {
        if (game.status == GameStatus.PLAYING) {
            showRestartConfirm = true
        } else {
            newGame()
        }
    }

    fun setFlagMode(mode: FlagMode) {
        flagMode = mode
        prefs.edit().putString("flag_mode", mode.name).apply()
        flagging = false
    }

    fun setTimerVisible(visible: Boolean) {
        timerVisible = visible
        prefs.edit().putBoolean("timer_visible", visible).apply()
    }

    fun setTimerEnabled(enabled: Boolean) {
        timerEnabled = enabled
        prefs.edit().putBoolean("timer_enabled", enabled).apply()
    }

    fun setOrientationMode(mode: OrientationMode) {
        orientationMode = mode
        prefs.edit().putString("orientation_mode", mode.name).apply()
    }

    fun bestTime(d: Difficulty): Long? {
        val v = prefs.getLong("best_${d.name}", -1L)
        return if (v < 0) null else v
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(4.dp)
    ) {
        // ===== 顶栏：极简一横排 =====
        // [💣雷数] [⚙️] [🙂] [🚩/⛏️] [⏱]
        StatusBar(
            game = game,
            timerVisible = timerVisible,
            tick = tick,
            flagMode = flagMode,
            flagging = flagging,
            onToggleMode = { flagging = !flagging },
            onRestart = { onSmileyClick() },
            onSettings = { showSettings = true }
        )

        Spacer(Modifier.height(4.dp))

        // ===== 棋盘：填满剩余全部空间 =====
        Board(
            game = game,
            modifier = Modifier.weight(1f),
            onReveal = { r, c ->
                if (game.status != GameStatus.PLAYING) return@Board
                val cell = game.cell(r, c)
                if (cell.state == CellState.REVEALED) {
                    game.chordReveal(r, c)
                } else if (flagMode == FlagMode.TOGGLE_BUTTON && flagging) {
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

    // ===== 设置对话框 =====
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // 难度（从主界面挪到这里）
                    Text("难度", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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

                    // 插旗方式
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
                    Spacer(Modifier.height(12.dp))

                    // 计时器显示开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { setTimerVisible(!timerVisible) })
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示计时器")
                            Text(
                                "在顶栏显示实时用时",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = timerVisible,
                            onCheckedChange = { setTimerVisible(it) }
                        )
                    }
                    // 计时与最佳记录开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { setTimerEnabled(!timerEnabled) })
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("记录最佳成绩")
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
                    Spacer(Modifier.height(12.dp))

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
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("完成")
                }
            }
        )
    }

    // 重开确认弹窗：游戏进行中点击笑脸时弹出，避免误触
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("重新开始") },
            text = { Text("当前游戏尚未结束，确定要放弃并开启新游戏吗？") },
            confirmButton = {
                TextButton(onClick = { newGame() }) {
                    Text("重新开始")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 胜利结算弹窗（仅记录开关开启时）
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

// ===== 顶栏状态条 =====
@Composable
fun StatusBar(
    game: MinesweeperGame,
    timerVisible: Boolean,
    tick: Long,   // 计时秒数快照，驱动重组
    flagMode: FlagMode,
    flagging: Boolean,
    onToggleMode: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit
) {
    val remaining = game.mineCount - game.flagsUsed
    // 关键：seconds 直接取 tick（上层每 500ms 更新的快照），确保秒数实时跳动
    val seconds = if (timerVisible) tick else 0L

    val panelModifier = Modifier
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        .border(
            1.dp,
            MaterialTheme.colorScheme.outline,
            RoundedCornerShape(10.dp)
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(panelModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // 左区：雷数（+ 旗帜切换按钮）
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValueChip("💣 $remaining")
            if (flagMode == FlagMode.TOGGLE_BUTTON) {
                Spacer(Modifier.width(6.dp))
                BarButton(if (flagging) "🚩" else "⛏️", onClick = onToggleMode)
            }
        }

        // 中间：笑脸 —— 显式居中，绝对在屏幕水平中线
        BarButton(
            smiley(game.status),
            onClick = onRestart,
            modifier = Modifier.align(Alignment.Center)
        )

        // 右区：计时（+ 设置）
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timerVisible) {
                ValueChip(formatTime(seconds))
                Spacer(Modifier.width(6.dp))
            }
            BarButton("⚙️", onClick = onSettings)
        }
    }
}

// 现代风格的圆角数值容器（雷数/计时器），带统一描边
@Composable
private fun ValueChip(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun smiley(status: GameStatus): String = when (status) {
    GameStatus.PLAYING -> "🙂"
    GameStatus.WON -> "😎"
    GameStatus.LOST -> "😵"
}

// 顶栏小按钮
@Composable
fun BarButton(
    symbol: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = symbol,
        fontSize = 20.sp,
        modifier = modifier
            .background(
                if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// 把秒数格式化为 mm:ss 或 ss
private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "%d".format(s)
}

// ===== 棋盘 =====
// STANDARD 档：格子等比拉伸，精确铺满整个可用空间（边缘零留白、不可滑动）。
// 其余三档（初级/中级/高级）：固定 40.dp 格子，棋盘固定大小居中显示，超屏部分不可滚动、直接裁掉。
@Composable
fun Board(
    game: MinesweeperGame,
    modifier: Modifier = Modifier,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit
) {
    // 现代风格保留 2dp 间距
    val gap = 2.dp
    val targetCell = 40.dp
    val isStandard = game.difficulty == Difficulty.STANDARD

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current

        if (isStandard) {
            // standard 档的动态行列数：maxWidth/maxHeight 是「父 Column 已扣掉安全区 inset + padding 后」的真实可用尺寸。
            // n 个格子占 n*target + (n-1)*gap 宽，故 n = floor((W + gap) / (target + gap))。
            val dynamicCols = ((maxWidth + gap) / (targetCell + gap)).toInt().coerceAtLeast(1)
            val dynamicRows = ((maxHeight + gap) / (targetCell + gap)).toInt().coerceAtLeast(1)
            // 组合完成后回传；key 为行列值，仅在尺寸变化时触发（避免组合期间改状态 + 重复布雷）
            LaunchedEffect(dynamicCols, dynamicRows) {
                game.setStandardBoard(dynamicCols, dynamicRows)
            }
        }

        // 渲染以逻辑层真实值为准（避免首次组合时动态值 > board.size 导致越界）
        val cols = game.cols
        val rows = game.rows

        if (isStandard) {
            // 像素级精确铺满：先换算成整数物理像素再除，余量（必然 < 列数/行数 个像素）
            // 分摊给前面若干列/行，每列/行至多多 1px——任何相邻格子尺寸差至多 1 物理像素，
            // 肉眼不可见，彻底消除「最后一列/行被明显拉伸」的取整累积误差。
            val boardWpx = with(density) { maxWidth.toPx() }.toInt()
            val boardHpx = with(density) { maxHeight.toPx() }.toInt()
            val gapPx = with(density) { gap.toPx() }.toInt()
            val cellWpx = (boardWpx - gapPx * (cols - 1)) / cols
            val remW = (boardWpx - gapPx * (cols - 1)) - cellWpx * cols
            val cellHpx = (boardHpx - gapPx * rows) / rows
            val remH = (boardHpx - gapPx * rows) - cellHpx * rows

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until rows) {
                    // 本行高度：前 remH 行各多摊 1px，其余用基准值
                    val rowHpx = cellHpx + if (row < remH) 1 else 0
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until cols) {
                            // 本列宽度：前 remW 列各多摊 1px，其余用基准值
                            val colWpx = cellWpx + if (col < remW) 1 else 0
                            CellView(
                                cell = game.cell(row, col),
                                cellWidth = with(density) { colWpx.toDp() },
                                cellHeight = with(density) { rowHpx.toDp() },
                                onClick = { onReveal(row, col) },
                                onLongClick = { onFlag(row, col) }
                            )
                        }
                    }
                    Spacer(Modifier.height(gap))
                }
            }
        } else {
            // 固定档（初级/中级/高级）：格子数固定，整体等比缩放——格子保持正方形，一旦超出屏幕就整体缩小到刚好塞进屏幕。
            // cellSize = min(可用宽/列, 可用高/行)：宽、高各算一个能塞下的格子边长，取较小者，
            // 保证两个方向都不溢出；顶部对齐（Top）向上贴紧状态栏，横向居中。
            val cellSize = minOf(
                (maxWidth - gap * (cols - 1)) / cols,
                (maxHeight - gap * rows) / rows
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until cols) {
                            CellView(
                                cell = game.cell(row, col),
                                cellWidth = cellSize,
                                cellHeight = cellSize,
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
}

@Composable
fun CellView(
    cell: Cell,
    cellWidth: Dp,
    cellHeight: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 图标/字体基准尺寸取宽高较小者，保证图标不超出格子
    val base = minOf(cellWidth, cellHeight)
    val contentFontSize = (base.value * 0.55f).sp

    val shape = RoundedCornerShape(3.dp)
    val revealed = cell.state == CellState.REVEALED

    val background = when {
        revealed && cell.isMine -> Color(0xFFD32F2F)
        revealed -> Color(0xFFE0E0E0)
        else -> Color(0xFF90A4AE)
    }
    val baseModifier = Modifier
        .size(width = cellWidth, height = cellHeight)
        .background(background, shape)

    Box(
        modifier = baseModifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        contentAlignment = Alignment.Center
    ) {
        when {
            cell.state == CellState.FLAGGED -> Text("🚩", fontSize = contentFontSize)
            revealed && cell.isMine -> Text("💣", fontSize = contentFontSize)
            revealed && cell.adjacentMines > 0 -> Text(
                text = cell.adjacentMines.toString(),
                fontSize = contentFontSize,
                fontWeight = FontWeight.Bold,
                color = numberColors[cell.adjacentMines - 1]
            )
        }
    }
}
