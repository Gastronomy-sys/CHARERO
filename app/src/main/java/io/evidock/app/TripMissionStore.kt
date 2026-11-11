package io.evidock.app

import android.content.Context

/** Stores only preset index, step and completion count on the local device. */
class TripMissionStore(context: Context) {
    private val prefs = context.getSharedPreferences("charero_trip_v1", Context.MODE_PRIVATE)

    fun read(): MissionPosition = TripMissionCore.normalize(MissionPosition(
        planIndex = prefs.getInt("plan", 0),
        stopIndex = prefs.getInt("stop", 0),
        finished = prefs.getBoolean("finished", false),
        runsCompleted = prefs.getInt("runs", 0)
    ))

    private fun save(state: MissionPosition) {
        val s = TripMissionCore.normalize(state)
        prefs.edit().putInt("plan", s.planIndex).putInt("stop", s.stopIndex)
            .putBoolean("finished", s.finished).putInt("runs", s.runsCompleted).apply()
    }

    fun choose(index: Int) { save(TripMissionCore.choose(read(), index)) }
    /** Preserve up to the last 12 completed visits; no coordinates or personal data. */
    /** Local customization for a predefined visit slot; no location service or server. */
    fun currentStop(): MissionStop {
        val s = read()
        val base = TripMissionCore.currentStop(s)
        val key = "${s.planIndex}_${s.stopIndex}"
        val label = prefs.getString("place_$key", base.place).orEmpty().ifBlank { base.place }
        val action = prefs.getString("action_$key", base.action).orEmpty().ifBlank { base.action }
        return base.copy(place = label, action = action, checkTitle = "장소별 할 일 확인")
    }

    fun updateCurrent(place: String, action: String) {
        val label = place.trim()
        val detail = action.trim()
        require(label.isNotEmpty() && label.length <= 40) { "Invalid place name" }
        require(detail.isNotEmpty() && detail.length <= 120) { "Invalid task" }
        val s = read()
        val key = "${s.planIndex}_${s.stopIndex}"
        prefs.edit().putString("place_$key", label).putString("action_$key", detail).apply()
    }

    fun recentVisits(): List<String> = prefs.getString("visits", "").orEmpty()
        .split(';').filter { it.isNotBlank() }.take(4).mapNotNull { record ->
            val fields = record.split(':')
            if (fields.size != 3) return@mapNotNull null
            val plan = fields[0].toIntOrNull() ?: return@mapNotNull null
            val stop = fields[1].toIntOrNull() ?: return@mapNotNull null
            if (plan !in TripMissionCore.plans.indices) return@mapNotNull null
            val visit = TripMissionCore.plans[plan].stops.getOrNull(stop) ?: return@mapNotNull null
            val label = prefs.getString("place_${plan}_${stop}", visit.place).orEmpty().ifBlank { visit.place }
            val time = fields[2].toLongOrNull() ?: return@mapNotNull null
            val at = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA).format(java.util.Date(time))
            "$at  $label"
        }

    fun advance() {
        val before = read()
        val after = TripMissionCore.advance(before)
        val record = "${before.planIndex}:${before.stopIndex}:${System.currentTimeMillis()}"
        val old = prefs.getString("visits", "").orEmpty().split(';').filter { it.isNotBlank() }
        prefs.edit().putString("visits", (listOf(record) + old).take(12).joinToString(";")).apply()
        save(after)
    }
    fun restart() { save(TripMissionCore.restart(read())) }
}
