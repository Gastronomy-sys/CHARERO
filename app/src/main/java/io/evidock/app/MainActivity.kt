package io.evidock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * CHARERO Premium UI 2.2: full-height responsive Pleos landscape presentation.
 * Scales typography for the wide vehicle display; preserves scrolling on short screens.
 * No navigation arrival is treated as an automatically finished real-world task.
 * A destination arrival is information, never evidence that a hand-off was completed.
 * The manual stop acknowledgment is not a vehicle parking sensor.
 */
class MainActivity : Activity() {
    private var uxAccess = UxAvailability.UNKNOWN
    private var uxMessage = "차량 상태 확인 중"
    private var doorMessage = "차량 상태 조회 중"
    private var uxMonitor: PleosUxRestrictionMonitor? = null
    private var doorProbe: PleosDoorProbe? = null
    private var naviMonitor: ChareroNaviMonitor? = null
    private var parked: Boolean? = null
    private var checks: MutableList<Evidence> = MutableList(3) { Evidence.UNKNOWN }
    private lateinit var store: TripMissionStore
    private var naviStatus: String = "내비 연결 확인 중"
    private var naviArrivalSignal = false
    private var leftScroller: ScrollView? = null
    private var rightScroller: ScrollView? = null

    private data class Palette(
        val page: Int, val surface: Int, val ink: Int, val secondary: Int, val border: Int,
        val navy: Int, val blue: Int, val bluePale: Int, val neutral: Int,
        val red: Int, val redPale: Int, val white: Int, val onDark: Int
    )

    private fun colors(): Palette {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) Palette(
            Color.rgb(17, 25, 38), Color.rgb(29, 41, 57), Color.rgb(241, 246, 252),
            Color.rgb(177, 193, 214), Color.rgb(57, 73, 94), Color.rgb(17, 40, 66),
            Color.rgb(117, 187, 255), Color.rgb(36, 64, 91), Color.rgb(44, 61, 81),
            Color.rgb(255, 174, 175), Color.rgb(83, 48, 57), Color.WHITE, Color.rgb(206, 221, 237)
        ) else Palette(
            Color.rgb(244, 247, 251), Color.WHITE, Color.rgb(25, 43, 65),
            Color.rgb(103, 119, 139), Color.rgb(221, 229, 238), Color.rgb(24, 48, 76),
            Color.rgb(26, 101, 187), Color.rgb(231, 241, 254), Color.rgb(240, 244, 249),
            Color.rgb(178, 63, 70), Color.rgb(255, 239, 240), Color.WHITE, Color.rgb(200, 222, 246)
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    /** Only enlarge typography in the large landscape app area. Portrait keeps natural scrolling. */
    private fun fontScale(): Float {
        val screen = resources.configuration
        return when {
            screen.orientation == Configuration.ORIENTATION_LANDSCAPE && screen.screenWidthDp >= 780 -> 1.25f
            screen.orientation == Configuration.ORIENTATION_LANDSCAPE -> 1.12f
            else -> 1.06f
        }
    }

    private fun rounded(fill: Int, radius: Int = 15, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun ripple(fill: Int, radius: Int, stroke: Int? = null): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(43, 38, 115, 203)),
            rounded(fill, radius, stroke), null)

    private fun t(s: String, sp: Float, fg: Int, bold: Boolean = false, lines: Int = 1): TextView =
        TextView(this).apply {
            text = s
            textSize = sp * fontScale()
            setTextColor(fg)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun put(parent: LinearLayout, view: View, top: Int = 0, bottom: Int = 0,
                    left: Int = 0, right: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        })
    }

    private fun card(c: Palette, inner: Int = 19): LinearLayout = col().apply {
        // When the parent assigns extra height, center content instead of leaving a dead zone.
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(c.surface, 19, c.border)
        setPadding(dp(inner), dp(inner), dp(inner), dp(inner))
    }

    private fun icon(name: String, color: Int, size: Int = 25): View =
        ChareroGlyph(this, name, color).apply {
            minimumWidth = dp(size)
            minimumHeight = dp(size)
        }

    private fun addIcon(parent: LinearLayout, name: String, color: Int, size: Int = 25) {
        parent.addView(icon(name, color, size), LinearLayout.LayoutParams(dp(size), dp(size)))
    }

    private fun pill(label: String, fg: Int, bg: Int): TextView = t(label, 12.5f, fg, true).apply {
        gravity = Gravity.CENTER
        background = rounded(bg, 24)
        setPadding(dp(13), dp(8), dp(13), dp(8))
    }

    private fun actionButton(label: String, c: Palette, enabled: Boolean,
                             primary: Boolean = true, onTap: () -> Unit): TextView =
        t(label, 16f, if (enabled && primary) c.white else if (enabled) c.ink else c.secondary, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(60)
            background = if (enabled) ripple(
                if (primary) c.blue else c.neutral, 13)
            else rounded(c.neutral, 13)
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            contentDescription = label
            if (enabled) setOnClickListener { onTap() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripMissionStore(this)
        naviMonitor = ChareroNaviMonitor(this) { message, arrived ->
            runOnUiThread {
                naviStatus = message
                naviArrivalSignal = arrived
                drawScreen(false)
            }
        }
        window.statusBarColor = colors().page
        window.navigationBarColor = colors().navy
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (savedInstanceState != null) {
            parked = when (savedInstanceState.getInt("demoParked", -1)) {
                1 -> true
                0 -> false
                else -> null
            }
            val list = savedInstanceState.getIntArray("manualEvidence")
            if (list != null && list.size == 3) {
                checks = list.map { Evidence.values().getOrElse(it) { Evidence.UNKNOWN } }.toMutableList()
            }
        }
        uxMonitor = PleosUxRestrictionMonitor(this) { next, description ->
            runOnUiThread {
                if (next != UxAvailability.CLEAR) {
                    parked = null
                    checks = MutableList(3) { Evidence.UNKNOWN }
                }
                uxAccess = next
                uxMessage = description
                drawScreen(false)
            }
        }
        doorProbe = PleosDoorProbe(this) { description ->
            runOnUiThread {
                doorMessage = description
                // Display is intentionally compact; preserve probe without a full screen rebuild.
            }
        }
        drawScreen(true)
    }

    override fun onStart() {
        super.onStart()
        uxAccess = UxAvailability.UNKNOWN
        parked = null
        checks = MutableList(3) { Evidence.UNKNOWN }
        uxMonitor?.start()
        doorProbe?.start()
        naviMonitor?.start()
    }

    override fun onStop() {
        naviMonitor?.stop()
        uxMonitor?.stop()
        doorProbe?.stop()
        uxAccess = UxAvailability.UNKNOWN
        parked = null
        checks = MutableList(3) { Evidence.UNKNOWN }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("demoParked", when (parked) { true -> 1; false -> 0; null -> -1 })
        outState.putIntArray("manualEvidence", checks.map { it.ordinal }.toIntArray())
        super.onSaveInstanceState(outState)
    }

    private fun safeToConfigure() = uxAccess == UxAvailability.CLEAR
    private fun safeToRecord() = UxAccessGate.mayEdit(uxAccess, parked)

    /** The platform can permit interaction without proving that the vehicle is parked. */
    private fun confirmStopped(then: () -> Unit) {
        if (!safeToConfigure()) return
        if (parked == true) {
            then()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("정차 상태 직접 확인")
            .setMessage("차량이 안전하게 정차한 것을 직접 확인했나요?\n이 선택은 차량의 정차 상태를 자동으로 판정하지 않습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("정차 확인") { _, _ ->
                if (safeToConfigure()) {
                    parked = true
                    then()
                }
            }
            .show()
    }

    private fun switchPlan(index: Int) {
        if (!safeToConfigure()) return
        confirmStopped {
            if (!safeToRecord()) return@confirmStopped
            val previous = store.read()
            if (index == previous.planIndex) {
                drawScreen(false)
            } else {
                val commit = {
                    if (safeToRecord()) {
                        store.choose(index)
                        checks = MutableList(3) { Evidence.UNKNOWN }
                        // Keep the explicit stop acknowledgement: selecting a plan must not
                        // immediately lock the other plan choices again.
                        drawScreen(true)
                    }
                }
                if (previous.stopIndex > 0 && !previous.finished) {
                    AlertDialog.Builder(this)
                        .setTitle("다른 루틴으로 변경")
                        .setMessage("진행 중인 루틴의 경유 순서가 초기화됩니다. 변경할까요?")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("변경") { _, _ -> commit() }
                        .show()
                } else commit()
            }
        }
    }

    private fun editCurrentStop() {
        if (!safeToConfigure()) return
        confirmStopped {
            if (!safeToRecord()) return@confirmStopped
            val visit = store.currentStop()
            val editor = col().apply { setPadding(dp(20), dp(9), dp(20), dp(6)) }
            val placeField = EditText(this).apply {
                setSingleLine(true)
                hint = "경유지 이름"
                setText(visit.place)
                filters = arrayOf(InputFilter.LengthFilter(40))
            }
            val taskField = EditText(this).apply {
                hint = "이 장소에서 할 일"
                setText(visit.action)
                filters = arrayOf(InputFilter.LengthFilter(120))
            }
            put(editor, placeField)
            put(editor, taskField, top = 8)
            AlertDialog.Builder(this)
                .setTitle("경유지 수정")
                .setView(editor)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장") { _, _ ->
                    if (safeToRecord()) {
                        val place = placeField.text.toString().trim()
                        val action = taskField.text.toString().trim()
                        if (place.isNotEmpty() && action.isNotEmpty()) {
                            store.updateCurrent(place, action)
                            drawScreen(false)
                        } else {
                            Toast.makeText(this, "장소와 할 일을 모두 입력하세요", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
        }
    }

    private fun moveToNextStop() {
        if (!safeToRecord()) return
        val state = store.read()
        if (state.finished) {
            store.restart()
        } else if (TripMissionCore.canComplete(
                uxAccess == UxAvailability.CLEAR, parked == true,
                checks.map { it == Evidence.CONFIRMED_BY_USER })) {
            store.advance()
        } else return
        checks = MutableList(3) { Evidence.UNKNOWN }
        parked = null  // Require fresh manual stop confirmation at each visit.
        drawScreen(true)
    }

    private fun toggleStopAcknowledgment() {
        if (!safeToConfigure()) return
        if (parked == true) {
            parked = null
            checks = MutableList(3) { Evidence.UNKNOWN }
            drawScreen(false)
        } else confirmStopped { drawScreen(false) }
    }

    private fun header(c: Palette): View {
        val bar = row().apply {
            setBackgroundColor(c.surface)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            minimumHeight = dp(75)
        }
        val logo = row().apply {
            gravity = Gravity.CENTER
            background = rounded(c.navy, 13)
        }
        addIcon(logo, "route", Color.WHITE, 25)
        bar.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val copy = col()
        put(copy, t("차례로", 22f, c.ink, true))
        put(copy, t("경유지에서 할 일까지", 11.5f, c.secondary), top = 4)
        bar.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(12)
        })
        val tag = when (uxAccess) {
            UxAvailability.CLEAR -> "조작 가능"
            UxAvailability.RESTRICTED -> "주행 중 잠금"
            UxAvailability.UNKNOWN -> "상태 확인 중"
        }
        bar.addView(pill(tag, if (uxAccess == UxAvailability.CLEAR) c.blue else c.secondary,
            c.bluePale))
        return bar
    }

    private fun routinePanel(c: Palette): LinearLayout {
        val position = store.read()
        val panel = card(c)
        val heading = row()
        heading.addView(t("반복 루틴", 18f, c.ink, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(t("선택", 12f, c.secondary, true))
        put(panel, heading)
        val glyphs = arrayOf("clock", "users", "briefcase")
        val tiles = row()
        TripMissionCore.plans.forEachIndexed { index, plan ->
            val selected = position.planIndex == index
            val enabled = safeToConfigure()
            val bg = if (selected) c.blue else c.neutral
            val fg = if (selected) c.white else if (enabled) c.ink else c.secondary
            val tile = col().apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(90)
                background = if (enabled) ripple(bg, 13, if (selected) null else c.border)
                             else rounded(bg, 13, c.border)
                isEnabled = enabled
                isClickable = enabled
                isFocusable = enabled
                isSelected = selected
                contentDescription = "반복 루틴 ${plan.title}${if (selected) ", 선택됨" else ""}"
                if (enabled) setOnClickListener { switchPlan(index) }
            }
            addIcon(tile, glyphs.getOrElse(index) { "route" }, fg, 23)
            val name = t(plan.title, 13f, fg, true).apply { gravity = Gravity.CENTER }
            put(tile, name, top = 7)
            tiles.addView(tile, LinearLayout.LayoutParams(0, dp(90), 1f).apply {
                if (index > 0) leftMargin = dp(7)
            })
        }
        put(panel, tiles, top = 12)
        if (!safeToConfigure()) {
            put(panel, t("주행 제한 해제 후 루틴 선택 가능", 12f, c.secondary), top = 9)
        }
        return panel
    }

    private fun currentStopPanel(c: Palette): LinearLayout {
        val position = store.read()
        val plan = TripMissionCore.currentPlan(position)
        val stop = store.currentStop()
        val panel = card(c, 19)
        val eyebrow = row()
        addIcon(eyebrow, "pin", c.blue, 18)
        eyebrow.addView(t(if (position.finished) "운행 완료" else "현재 경유지", 13f, c.blue, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(7) })
        eyebrow.addView(pill("${position.stopIndex + 1} / ${plan.stops.size}", c.ink, c.neutral))
        put(panel, eyebrow)
        put(panel, t(if (position.finished) "모든 방문 완료" else stop.place, 26f, c.ink, true), top = 16)
        put(panel, t(if (position.finished) "같은 루틴을 다음 운행에 재사용하세요" else stop.action,
            14f, c.secondary, lines = 2), top = 8)
        val progress = row()
        for (index in plan.stops.indices) {
            val active = if (position.finished) true else index <= position.stopIndex
            progress.addView(View(this).apply {
                background = rounded(if (active) c.blue else c.border, 5)
            }, LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                if (index > 0) leftMargin = dp(6)
            })
        }
        put(panel, progress, top = 14)
        val edit = row().apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(42)
            isEnabled = safeToConfigure() && !position.finished
            isClickable = isEnabled
            isFocusable = isEnabled
            background = if (isEnabled) ripple(c.bluePale, 11) else rounded(c.neutral, 11)
            contentDescription = "현재 경유지 이름과 할 일 편집"
            if (isEnabled) setOnClickListener { editCurrentStop() }
        }
        addIcon(edit, "edit", if (edit.isEnabled) c.blue else c.secondary, 17)
        edit.addView(t("장소 · 할 일 수정", 13f, if (edit.isEnabled) c.blue else c.secondary, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(7)
            })
        put(panel, edit, top = 14)
        return panel
    }

    private fun infoPanel(c: Palette): LinearLayout {
        val panel = card(c, 18)
        val nav = row()
        val statusColor = if (naviStatus.contains("연결됨") || naviStatus.contains("수신")) c.blue else c.secondary
        addIcon(nav, "navigation", statusColor, 21)
        nav.addView(t("Pleos 내비", 15f, c.ink, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(9) })
        nav.addView(pill(if (naviArrivalSignal) "도착 알림" else "경로 조회", c.blue, c.bluePale))
        put(panel, nav)
        put(panel, t(naviStatus, 12.5f, c.secondary, lines = 2), top = 8)
        return panel
    }

    private fun stopPanel(c: Palette): LinearLayout {
        val panel = card(c, 18)
        val title = row()
        addIcon(title, "shield", c.blue, 20)
        title.addView(t("정차 직접 확인", 15f, c.ink, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(9)
            })
        title.addView(pill(if (parked == true) "수동 확인" else "대기", c.secondary, c.neutral))
        put(panel, title)
        val label = when {
            !safeToConfigure() -> "주행 UX 제한 · 입력 불가"
            parked == true -> "정차 확인 취소"
            else -> "정차 확인하고 시작"
        }
        put(panel, actionButton(label, c, safeToConfigure(),
            primary = parked != true) { toggleStopAcknowledgment() }, top = 12)
        return panel
    }

    private fun historyPanel(c: Palette): LinearLayout? {
        val recent = store.recentVisits()
        if (recent.isEmpty()) return null
        val panel = card(c, 14)
        val row = row()
        addIcon(row, "history", c.secondary, 19)
        row.addView(t("최근 완료", 14f, c.ink, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            })
        put(panel, row)
        recent.take(2).forEach { put(panel, t(it, 12f, c.secondary), top = 7) }
        return panel
    }

    private fun checklist(c: Palette, stretch: Boolean = false): LinearLayout {
        val panel = col()
        val complete = checks.count { it == Evidence.CONFIRMED_BY_USER }
        val heading = row().apply { setPadding(dp(2), dp(2), dp(2), dp(9)) }
        val headingLeft = col()
        put(headingLeft, t("현장 점검", 24f, c.ink, true))
        put(headingLeft, t("완료는 직접 확인한 경우에만 기록됩니다", 12f, c.secondary), top = 4)
        heading.addView(headingLeft, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(pill("$complete / 3", c.blue, c.bluePale))
        put(panel, heading)

        val title = arrayOf("정차 가능 여부", "승하차 이동 동선", "장소별 할 일")
        val detail = arrayOf("표지 · 정차 조건 확인", "차량 주변 이동 경로 확인", store.currentStop().action)
        val icons = arrayOf("car", "users", "clipboard")
        for (i in 0..2) {
            val item = evidenceRow(c, i, title[i], detail[i], icons[i])
            if (stretch) {
                // WRAP_CONTENT + weight fills spare height, but retains natural size on short screens.
                panel.addView(item, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { topMargin = dp(if (i == 0) 0 else 11) })
            } else {
                put(panel, item, top = if (i == 0) 0 else 11)
            }
        }
        return panel
    }

    private fun evidenceRow(c: Palette, index: Int, label: String, detail: String,
                            glyph: String): LinearLayout {
        val panel = card(c, 18)
        val state = checks[index]
        val available = safeToRecord()
        val top = row()
        val block = row().apply {
            gravity = Gravity.CENTER
            background = rounded(c.bluePale, 11)
        }
        addIcon(block, glyph, c.blue, 27)
        top.addView(block, LinearLayout.LayoutParams(dp(50), dp(50)))
        val copy = col()
        put(copy, t(label, 18.5f, c.ink, true))
        put(copy, t(detail, 13.5f, c.secondary), top = 6)
        top.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(11)
        })
        val status = when (state) {
            Evidence.UNKNOWN -> "미확인"
            Evidence.CONFIRMED_BY_USER -> "완료"
            Evidence.NEGATIVE_BY_USER -> "문제"
        }
        val statusFg = when (state) {
            Evidence.UNKNOWN -> c.secondary
            Evidence.CONFIRMED_BY_USER -> c.blue
            Evidence.NEGATIVE_BY_USER -> c.red
        }
        val statusBg = when (state) {
            Evidence.UNKNOWN -> c.neutral
            Evidence.CONFIRMED_BY_USER -> c.bluePale
            Evidence.NEGATIVE_BY_USER -> c.redPale
        }
        top.addView(pill(status, statusFg, statusBg))
        put(panel, top)

        val options = arrayOf(
            Evidence.UNKNOWN to "미확인",
            Evidence.CONFIRMED_BY_USER to "✓  완료",
            Evidence.NEGATIVE_BY_USER to "!  문제"
        )
        val choices = row()
        options.forEachIndexed { optionIndex, (value, word) ->
            val selected = state == value
            val selectedFill = when (value) {
                Evidence.UNKNOWN -> c.neutral
                Evidence.CONFIRMED_BY_USER -> c.blue
                Evidence.NEGATIVE_BY_USER -> c.red
            }
            val fill = if (available && selected) selectedFill else c.neutral
            val fg = if (available && selected && value != Evidence.UNKNOWN) c.white else
                if (!available) c.secondary else c.ink
            val control = t(word, 15.5f, fg, true).apply {
                gravity = Gravity.CENTER
                minHeight = dp(60)
                background = if (available) ripple(fill, 12) else rounded(fill, 12)
                isClickable = available
                isFocusable = available
                isEnabled = available
                isSelected = selected
                contentDescription = "$label - $word${if (selected) " 선택됨" else ""}"
                if (available) setOnClickListener {
                    if (safeToRecord()) {
                        checks[index] = value
                        drawScreen(false)
                    }
                }
            }
            choices.addView(control, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                if (optionIndex > 0) leftMargin = dp(8)
            })
        }
        put(panel, choices, top = 14)
        return panel
    }

    private fun bottomDock(c: Palette): LinearLayout {
        val state = store.read()
        val done = checks.count { it == Evidence.CONFIRMED_BY_USER }
        val problem = checks.any { it == Evidence.NEGATIVE_BY_USER }
        val canComplete = !state.finished && TripMissionCore.canComplete(
            uxAccess == UxAvailability.CLEAR, parked == true,
            checks.map { it == Evidence.CONFIRMED_BY_USER })
        val active = safeToConfigure() && (state.finished && parked == true || canComplete)
        val message = when {
            !safeToConfigure() -> "주행 중 입력 잠금"
            state.finished -> "이번 루틴을 마쳤어요"
            parked != true -> "정차를 확인하고 시작하세요"
            problem -> "문제 항목을 다시 확인하세요"
            canComplete -> "방문 준비가 완료됐어요"
            else -> "현장 확인 $done / 3"
        }
        val btn = when {
            state.finished -> "다시 시작  ↻"
            canComplete -> "방문 완료  →"
            else -> "완료 대기"
        }
        val dock = row().apply {
            background = rounded(c.navy, 18)
            setPadding(dp(23), dp(17), dp(23), dp(17))
            minimumHeight = dp(90)
        }
        val label = col()
        put(label, t("다음 행동", 11f, c.onDark, true))
        put(label, t(message, 18f, c.white, true), top = 6)
        dock.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val action = t(btn, 14f, if (active) c.navy else c.onDark, true).apply {
            gravity = Gravity.CENTER
            background = if (active) ripple(Color.WHITE, 12) else rounded(Color.rgb(58, 86, 114), 12)
            minHeight = dp(60)
            setPadding(dp(17), dp(3), dp(17), dp(3))
            isEnabled = active
            isClickable = active
            isFocusable = active
            if (active) setOnClickListener { moveToNextStop() }
        }
        dock.addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(60)).apply {
            leftMargin = dp(10)
        })
        return dock
    }

    private fun drawScreen(resetScroll: Boolean = false) {
        if (!::store.isInitialized) return
        val c = colors()
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 780
        val leftY = if (resetScroll) 0 else leftScroller?.scrollY ?: 0
        val rightY = if (resetScroll) 0 else rightScroller?.scrollY ?: 0
        val root = col().apply { setBackgroundColor(c.page) }
        setContentView(root)
        root.addView(header(c), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this).apply { setBackgroundColor(c.border) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        if (wide) {
            val body = row().apply {
                gravity = Gravity.TOP
                setPadding(dp(14), dp(13), dp(14), dp(13))
            }
            root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            // Fill the left column naturally: all four panels share unused vertical space.
            // With WRAP_CONTENT weights, panels scroll instead of clipping on shorter displays.
            val leftScroll = ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
            leftScroller = leftScroll
            body.addView(leftScroll, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.40f).apply { rightMargin = dp(13) })
            val left = col().apply { setPadding(dp(1), dp(1), dp(1), dp(1)) }
            leftScroll.addView(left, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            fun expandLeft(view: View, weight: Float, top: Int = 0) {
                left.addView(view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    weight).apply { topMargin = dp(top) })
            }
            expandLeft(routinePanel(c), 1.08f)
            expandLeft(currentStopPanel(c), 1.28f, 11)
            expandLeft(infoPanel(c), 0.70f, 11)
            expandLeft(stopPanel(c), 0.85f, 11)
            historyPanel(c)?.let { expandLeft(it, 0.60f, 11) }
            leftScroll.post { leftScroll.scrollTo(0, leftY) }

            // In contrast with v2.1, the three checklist cards use ALL usable height.
            // Keep the next-action dock fixed below the scrollable checklist.
            val right = col()
            body.addView(right, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.60f))
            val rightScroll = ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
            rightScroller = rightScroll
            right.addView(rightScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val scrollContent = col().apply { setPadding(dp(1), dp(1), dp(1), dp(2)) }
            rightScroll.addView(scrollContent, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            scrollContent.addView(checklist(c, stretch = true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rightScroll.post { rightScroll.scrollTo(0, rightY) }
            put(right, bottomDock(c), top = 10)
        } else {
            val scroller = ScrollView(this).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
            }
            leftScroller = scroller
            rightScroller = null
            root.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val page = col().apply { setPadding(dp(16), dp(14), dp(16), dp(24)) }
            scroller.addView(page)
            put(page, routinePanel(c))
            put(page, currentStopPanel(c), top = 12)
            put(page, infoPanel(c), top = 12)
            put(page, stopPanel(c), top = 12)
            put(page, checklist(c), top = 18)
            put(page, bottomDock(c), top = 14)
            historyPanel(c)?.let { put(page, it, top = 12) }
            scroller.post { scroller.scrollTo(0, leftY) }
        }
    }
}

/** Lightweight original vector icons: no emoji fonts or third-party artwork. */
private class ChareroGlyph(context: Context, private val glyph: String, private val tint: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 2.05f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = minOf(width, height).toFloat() / 24f
        canvas.save()
        canvas.translate((width - 24f * unit) / 2f, (height - 24f * unit) / 2f)
        canvas.scale(unit, unit)
        when (glyph) {
            "clock", "history" -> {
                canvas.drawCircle(12f, 12f, 8.5f, p)
                canvas.drawLine(12f, 7f, 12f, 12f, p)
                canvas.drawLine(12f, 12f, 16f, 14f, p)
            }
            "users" -> {
                canvas.drawCircle(10f, 8f, 3f, p)
                canvas.drawPath(path(4f, 19f, 4f, 16f, 7f, 13.5f, 13f, 13.5f, 16f, 16f, 16f, 19f), p)
                canvas.drawPath(path(17f, 5.5f, 19f, 6.5f, 19f, 9.5f, 17f, 10.5f), p)
                canvas.drawPath(path(19f, 14f, 21f, 16f, 21f, 19f), p)
            }
            "briefcase" -> {
                canvas.drawRoundRect(3.5f, 7f, 20.5f, 19f, 2f, 2f, p)
                canvas.drawPath(path(9f, 7f, 9f, 4.5f, 15f, 4.5f, 15f, 7f), p)
                canvas.drawLine(3.5f, 12f, 20.5f, 12f, p)
                canvas.drawLine(11f, 11.5f, 13f, 11.5f, p)
            }
            "pin" -> {
                canvas.drawPath(path(12f, 21f, 5.5f, 12f, 5f, 9f, 7f, 5f, 12f, 3f,
                    17f, 5f, 19f, 9f, 18.5f, 12f, 12f, 21f), p)
                canvas.drawCircle(12f, 10f, 2.5f, p)
            }
            "navigation" -> {
                canvas.drawPath(path(3f, 11f, 21f, 3f, 13f, 21f, 11f, 13f, 3f, 11f), p)
            }
            "route" -> {
                canvas.drawCircle(5f, 6f, 2.3f, p)
                canvas.drawCircle(19f, 18f, 2.3f, p)
                canvas.drawPath(path(6f, 18f, 11f, 18f, 13f, 15f, 11f, 12f, 9f, 12f,
                    8f, 8.5f, 12f, 6f, 16f, 6f), p)
            }
            "edit" -> {
                canvas.drawPath(path(4f, 19f, 8f, 18f, 19f, 7f, 16f, 4f, 5f, 15f, 4f, 19f), p)
                canvas.drawLine(14f, 6f, 17f, 9f, p)
            }
            "shield" -> {
                canvas.drawPath(path(12f, 2.5f, 19f, 5.5f, 19f, 12f, 17f, 17f,
                    12f, 21f, 7f, 17f, 5f, 12f, 5f, 5.5f, 12f, 2.5f), p)
                canvas.drawPath(path(8.5f, 12f, 11f, 14.5f, 15.5f, 10f), p)
            }
            "car" -> {
                canvas.drawPath(path(3f, 11f, 5f, 6f, 19f, 6f, 21f, 11f, 21f, 17f,
                    3f, 17f, 3f, 11f), p)
                canvas.drawLine(3f, 11f, 21f, 11f, p)
                canvas.drawCircle(7f, 17.5f, 1.6f, p)
                canvas.drawCircle(17f, 17.5f, 1.6f, p)
            }
            "clipboard" -> {
                canvas.drawRoundRect(5f, 4f, 19f, 21f, 2f, 2f, p)
                canvas.drawRoundRect(9f, 2.5f, 15f, 6.5f, 1f, 1f, p)
                canvas.drawPath(path(8.5f, 14f, 11f, 16.5f, 16f, 11f), p)
            }
            else -> canvas.drawCircle(12f, 12f, 7f, p)
        }
        canvas.restore()
    }

    private fun path(vararg xy: Float): Path = Path().apply {
        if (xy.size >= 2) moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
    }
}
