package com.ankopala.minesweeper

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil

/**
 * 扫雷核心逻辑 —— 只依赖 Compose 的状态类型，不依赖任何 UI 组件。
 *
 * 把「游戏规则」和「怎么画出来」彻底分开，是学习阶段一个非常关键的习惯：
 * 这样逻辑可以单独测试，UI 随便换（Compose / 命令行 / 网页）都不用改这一层。
 *
 * 注意：这里用 Compose 的 mutableStateListOf / mutableStateOf 来存棋盘和状态，
 * 是因为这些是「可观察状态」—— 任何一格变化，Compose 都能自动感知并重绘对应 UI。
 * 如果用普通的 Array，Compose 感知不到内部元素被修改，界面就不会更新。
 */

// 难度配置
enum class Difficulty(
    val rows: Int,
    val cols: Int,
    val mines: Int,
    @StringRes val label: Int
) {
    STANDARD(10, 22, 33, R.string.difficulty_standard),
    EASY(9, 9, 10, R.string.difficulty_easy),
    MEDIUM(16, 16, 40, R.string.difficulty_medium),
    EXPERT(16, 30, 99, R.string.difficulty_expert)
}

// 一个格子的三种状态
enum class CellState {
    HIDDEN,      // 未翻开
    REVEALED,    // 已翻开
    FLAGGED      // 插了旗
}

// 一个格子的数据
data class Cell(
    val isMine: Boolean = false,
    val adjacentMines: Int = 0,
    val state: CellState = CellState.HIDDEN
)

// 游戏整体状态
enum class GameStatus {
    PLAYING,       // 进行中
    WON,           // 已胜利
    LOST,          // 已判负
    PENDING_LOSS   // 踩雷但尚未确认（等待玩家选择「撤回」或「认输」）
}

// 插旗方式
enum class FlagMode(@StringRes val label: Int) {
    LONG_PRESS(R.string.flag_long_press),      // 长按格子插旗
    TOGGLE_BUTTON(R.string.flag_toggle_button) // 顶部切换「插旗/翻开」，单击格子执行当前动作
}

// 屏幕方向模式
enum class OrientationMode(@StringRes val label: Int) {
    PORTRAIT(R.string.orientation_portrait),  // 锁定竖屏
    AUTO(R.string.orientation_auto)           // 跟随系统方向旋转
}

class MinesweeperGame(initialDifficulty: Difficulty) {

    // 难度可变：切换难度时 rows/cols/mineCount 会随之更新。
    // 必须是 Compose 可观察状态（mutableStateOf），否则切难度后 UI 不会重组、棋盘尺寸/雷数不更新。
    var difficulty by mutableStateOf(initialDifficulty)
        private set

    // 当前是否为横屏。影响高级盘的「有效行列」：横屏用 16×30，竖屏交换成 30×16
    // 同样必须是可观察状态，否则旋转屏幕后棋盘不刷新。
    var isLandscape by mutableStateOf(false)
        private set

    // standard 档的动态行列数（由 UI 层根据「扣除安全区后的可用尺寸」计算后回传）。
    // 默认取枚举常量，保证首次进入即有合法尺寸；必须是可观察状态，否则回传后 UI 不重组。
    var standardRows by mutableStateOf(Difficulty.STANDARD.rows)
        private set
    var standardCols by mutableStateOf(Difficulty.STANDARD.cols)
        private set

    // 有效行列：standard 档返回动态值；高级盘在竖屏时行列互换；其余难度用枚举常量
    val rows get() =
        if (difficulty == Difficulty.STANDARD) standardRows else effectiveRows(difficulty, isLandscape)
    val cols get() =
        if (difficulty == Difficulty.STANDARD) standardCols else effectiveCols(difficulty, isLandscape)

    // 雷数：standard 档按格子总数 × 15% 动态算（钳制到「留出 first-click 安全区」的最大值）；其余用枚举常量
    val mineCount: Int
        get() {
            if (difficulty != Difficulty.STANDARD) return difficulty.mines
            val total = standardRows * standardCols
            val target = ceil(total * 0.15).toInt()
            // first-click safe 需为「安全格 + 周围一圈」预留最多 9 格之外的布雷空间，
            // 否则 placeMines 的 while 循环永远放不满 -> 死循环/ANR
            return target.coerceAtMost(maxOf(0, total - 9))
        }

    // 竖屏/横屏是否交换 rows 与 cols（短边横铺、格子更大）：
    // 仅 EXPERT(16×30) 竖屏时交换；STANDARD 已改为按方向动态计算行列，不再需要交换
    private fun shouldSwap(d: Difficulty, landscape: Boolean) = when (d) {
        Difficulty.EXPERT -> !landscape
        else -> false
    }

    private fun effectiveRows(d: Difficulty, landscape: Boolean) =
        if (shouldSwap(d, landscape)) d.cols else d.rows

    private fun effectiveCols(d: Difficulty, landscape: Boolean) =
        if (shouldSwap(d, landscape)) d.rows else d.cols

    // 棋盘：用可观察列表存（下标 = row * cols + col），Compose 能感知每一格的改动
    val board = mutableStateListOf<Cell>().apply {
        repeat(rows * cols) { add(Cell()) }
    }

    var status by mutableStateOf(GameStatus.PLAYING)
        private set

    var flagsUsed by mutableStateOf(0)
        private set

    private var revealedCount = 0

    // 计时：记录第一次翻开的时间戳（单调时钟）
    private var startTime: Long = 0

    // 本局用时（秒）。胜利后有效，未结束时为 0
    var elapsedSeconds: Long = 0
        private set

    // 实时用时（秒）：未开始为 0，进行中实时增长，结束停在定格值
    val elapsedNow: Long
        get() = when {
            startTime == 0L -> 0L
            status != GameStatus.PLAYING -> elapsedSeconds
            else -> (SystemClock.elapsedRealtime() - startTime) / 1000
        }

    init {
        newGame()
    }

    // 切换难度并重新开局（棋盘尺寸会按新难度重建）
    fun changeDifficulty(newDifficulty: Difficulty) {
        if (difficulty == newDifficulty) return
        difficulty = newDifficulty
        newGame()
    }

    // UI 层算好 standard 档动态行列后回传。仅在 difficulty==STANDARD 且行列确实变化时才重建棋盘。
    fun setStandardBoard(cols: Int, rows: Int) {
        if (difficulty != Difficulty.STANDARD) return
        val c = cols.coerceAtLeast(1)
        val r = rows.coerceAtLeast(1)
        if (standardCols == c && standardRows == r) return  // 去抖：避免每次重组重复布雷
        standardCols = c
        standardRows = r
        newGame()
    }

    // 更新屏幕方向。若有效行列数因此改变（高级盘横竖屏互换），则重建棋盘
    fun setOrientation(landscape: Boolean) {
        if (isLandscape == landscape) return
        val oldRows = rows
        val oldCols = cols
        isLandscape = landscape
        if (rows != oldRows || cols != oldCols) {
            newGame()
        }
    }

    // 重新开始：清空棋盘，重新布雷
    fun newGame() {
        board.clear()
        repeat(rows * cols) { board.add(Cell()) }
        status = GameStatus.PLAYING
        flagsUsed = 0
        revealedCount = 0
        minesPlaced = false
        startTime = 0
        elapsedSeconds = 0
        pendingMineRow = -1
        pendingMineCol = -1
    }

    private fun index(row: Int, col: Int) = row * cols + col

    private fun inBounds(row: Int, col: Int) =
        row in 0 until rows && col in 0 until cols

    /**
     * 第一次点击时才布雷（first-click safe）：
     * 保证玩家点的第一格永远不会是雷，体验好很多。
     */
    private fun placeMines(safeRow: Int, safeCol: Int) {
        var placed = 0
        while (placed < mineCount) {
            val r = (0 until rows).random()
            val c = (0 until cols).random()
            val i = index(r, c)
            // 跳过安全格本身和它周围一圈
            if (r == safeRow && c == safeCol) continue
            if (kotlin.math.abs(r - safeRow) <= 1 && kotlin.math.abs(c - safeCol) <= 1) continue
            if (!board[i].isMine) {
                board[i] = board[i].copy(isMine = true)
                placed++
            }
        }
        // 计算每个非雷格子周围的雷数
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = index(r, c)
                if (!board[i].isMine) {
                    board[i] = board[i].copy(adjacentMines = countAdjacent(r, c))
                }
            }
        }
    }

    private fun countAdjacent(row: Int, col: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (inBounds(r, c) && board[index(r, c)].isMine) count++
            }
        }
        return count
    }

    // 是否已经布过雷（第一次翻开前都是 false）
    private var minesPlaced = false

    // 悔棋开关：开启时踩雷不立即判负，而是进入 PENDING_LOSS 等待玩家选择「撤回」或「认输」。
    var undoEnabled by mutableStateOf(true)

    // 待定踩雷的那一格（row, col）。仅在 status == PENDING_LOSS 时非空。
    private var pendingMineRow = -1
    private var pendingMineCol = -1

    /** 翻开一个格子。返回翻开后是否踩到雷（供 UI 决定后续处理）。 */
    fun reveal(row: Int, col: Int): Boolean {
        if (status != GameStatus.PLAYING) return false
        if (!inBounds(row, col)) return false
        val i = index(row, col)
        if (board[i].state == CellState.FLAGGED) return false
        if (board[i].state == CellState.REVEALED) return false

        if (!minesPlaced) {
            placeMines(row, col)
            minesPlaced = true
            // 第一次翻开 → 开始计时
            startTime = SystemClock.elapsedRealtime()
        }

        if (board[i].isMine) {
            // 踩雷：
            // - 悔棋开启 → 翻开这一格，进入 PENDING_LOSS 等待「撤回/认输」；
            // - 悔棋关闭 → 经典行为，翻开所有雷并判负。
            board[i] = board[i].copy(state = CellState.REVEALED)
            if (undoEnabled) {
                pendingMineRow = row
                pendingMineCol = col
                status = GameStatus.PENDING_LOSS
            } else {
                status = GameStatus.LOST
            }
            return true
        }

        // 空白格 → 洪泛展开；数字格 → 只翻自己
        floodFill(row, col)
        checkWin()
        return false
    }

    /** 广度优先展开：空白格会连锁翻开周围，直到碰到数字格。 */
    private fun floodFill(row: Int, col: Int) {
        val i = index(row, col)
        if (board[i].state == CellState.REVEALED) return

        board[i] = board[i].copy(state = CellState.REVEALED)
        revealedCount++

        // 数字格（>0）不继续扩散
        if (board[i].adjacentMines > 0) return

        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (inBounds(r, c)) {
                    val ni = index(r, c)
                    if (board[ni].state == CellState.HIDDEN) {
                        floodFill(r, c)
                    }
                }
            }
        }
    }

    /**
     * 快速翻开（chord）：经典扫雷的效率操作。
     * 当一个「已翻开的数字格」周围，插旗的数量恰好等于该数字时，
     * 点击它会自动翻开周围所有未插旗的格子。返回是否因此踩雷。
     */
    fun chordReveal(row: Int, col: Int): Boolean {
        if (status != GameStatus.PLAYING) return false
        if (!inBounds(row, col)) return false
        val i = index(row, col)
        val cell = board[i]
        // 只对「已翻开的数字格」生效
        if (cell.state != CellState.REVEALED) return false
        if (cell.adjacentMines == 0) return false

        // 统计周围插旗数量
        var flags = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (inBounds(r, c) && board[index(r, c)].state == CellState.FLAGGED) flags++
            }
        }
        // 旗数不等于数字，不触发
        if (flags != cell.adjacentMines) return false

        // 翻开周围所有未插旗、未翻开的格子；踩到雷则判负
        var hitMine = false
        var hitRow = -1
        var hitCol = -1
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (!inBounds(r, c)) continue
                val ni = index(r, c)
                if (board[ni].state == CellState.HIDDEN) {
                    if (board[ni].isMine) {
                        board[ni] = board[ni].copy(state = CellState.REVEALED)
                        if (!hitMine) {
                            hitMine = true
                            hitRow = r
                            hitCol = c
                        }
                    } else {
                        floodFill(r, c)
                    }
                }
            }
        }
        if (hitMine) {
            if (undoEnabled) {
                pendingMineRow = hitRow
                pendingMineCol = hitCol
                status = GameStatus.PENDING_LOSS
            } else {
                status = GameStatus.LOST
            }
        } else {
            checkWin()
        }
        return hitMine
    }

    /** 切换旗子状态（长按调用）。 */
    fun toggleFlag(row: Int, col: Int) {
        if (status != GameStatus.PLAYING) return
        if (!inBounds(row, col)) return
        val i = index(row, col)
        when (board[i].state) {
            CellState.HIDDEN -> {
                board[i] = board[i].copy(state = CellState.FLAGGED)
                flagsUsed++
            }
            CellState.FLAGGED -> {
                board[i] = board[i].copy(state = CellState.HIDDEN)
                flagsUsed--
            }
            CellState.REVEALED -> { /* 已翻开的格子不能插旗 */ }
        }
    }

    /**
     * 悔棋：把待定踩雷的那一格恢复成未翻开，游戏从 PENDING_LOSS 回到 PLAYING。
     * 仅当 status == PENDING_LOSS 时有效。
     */
    fun undoLastMine() {
        if (status != GameStatus.PENDING_LOSS) return
        if (!inBounds(pendingMineRow, pendingMineCol)) {
            status = GameStatus.LOST
            return
        }
        val i = index(pendingMineRow, pendingMineCol)
        board[i] = board[i].copy(state = CellState.HIDDEN)
        pendingMineRow = -1
        pendingMineCol = -1
        status = GameStatus.PLAYING
    }

    /**
     * 认输：确认踩雷，进入经典判负行为（翻开所有雷，置 LOST）。
     * 仅当 status == PENDING_LOSS 时有效。
     */
    fun confirmLoss() {
        if (status != GameStatus.PENDING_LOSS) return
        // 翻开所有未插旗的雷，还原经典「踩雷揭示全图」的效果
        for (i in board.indices) {
            if (board[i].isMine && board[i].state != CellState.FLAGGED) {
                board[i] = board[i].copy(state = CellState.REVEALED)
            }
        }
        pendingMineRow = -1
        pendingMineCol = -1
        status = GameStatus.LOST
    }

    private fun checkWin() {
        // 翻开的非雷格数量 == 总数 - 雷数，即胜利
        if (revealedCount == rows * cols - mineCount) {
            status = GameStatus.WON
            // 结算用时
            if (startTime > 0) {
                elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000
            }
            // 胜利时自动给剩余雷格插旗
            for (i in board.indices) {
                if (board[i].isMine && board[i].state != CellState.FLAGGED) {
                    board[i] = board[i].copy(state = CellState.FLAGGED)
                }
            }
            flagsUsed = mineCount
        }
    }

    fun cell(row: Int, col: Int): Cell = board[index(row, col)]

    /**
     * 把当前棋局序列化成一个字符串，供「切换语言导致 Activity 重建」时保存/恢复棋局。
     * 用「|」分隔字段，board 的每格用「,」分隔（isMine,adjacent,stateOrdinal）。
     */
    fun snapshot(): String {
        val sb = StringBuilder()
        sb.append(difficulty.name).append('|')
        sb.append(isLandscape).append('|')
        sb.append(standardRows).append('|')
        sb.append(standardCols).append('|')
        sb.append(status.name).append('|')
        sb.append(flagsUsed).append('|')
        sb.append(revealedCount).append('|')
        sb.append(startTime).append('|')
        sb.append(elapsedSeconds).append('|')
        sb.append(minesPlaced).append('|')
        sb.append(pendingMineRow).append('|')
        sb.append(pendingMineCol).append('|')
        sb.append(undoEnabled).append('|')
        sb.append(board.size).append('|')
        board.forEachIndexed { i, c ->
            if (i > 0) sb.append(',')
            sb.append(if (c.isMine) 1 else 0).append(';')
                .append(c.adjacentMines).append(';')
                .append(c.state.ordinal)
        }
        return sb.toString()
    }

    /**
     * 从 snapshot() 生成的字符串恢复棋局。解析失败则回退到 newGame()。
     */
    fun restore(s: String) {
        try {
            val parts = s.split('|')
            if (parts.size < 14) { newGame(); return }
            difficulty = Difficulty.valueOf(parts[0])
            isLandscape = parts[1].toBoolean()
            standardRows = parts[2].toInt()
            standardCols = parts[3].toInt()
            status = GameStatus.valueOf(parts[4])
            flagsUsed = parts[5].toInt()
            revealedCount = parts[6].toInt()
            startTime = parts[7].toLong()
            elapsedSeconds = parts[8].toLong()
            minesPlaced = parts[9].toBoolean()
            pendingMineRow = parts[10].toInt()
            pendingMineCol = parts[11].toInt()
            undoEnabled = parts[12].toBoolean()
            val size = parts[13].toInt()
            board.clear()
            val cells = parts[14].split(',')
            for (i in 0 until size) {
                val f = cells[i].split(';')
                board.add(
                    Cell(
                        isMine = f[0] == "1",
                        adjacentMines = f[1].toInt(),
                        state = CellState.entries[f[2].toInt()]
                    )
                )
            }
        } catch (e: Exception) {
            // 解析失败（版本不兼容等），安全回退到新局
            newGame()
        }
    }
}
