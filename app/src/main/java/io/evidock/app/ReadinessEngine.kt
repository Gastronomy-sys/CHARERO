package io.evidock.app

enum class Evidence { UNKNOWN, CONFIRMED_BY_USER, NEGATIVE_BY_USER }
enum class Status { NOT_PARKED, RECONSIDER, VERIFY, FINAL_HUMAN_CHECK }

data class StopCase(
    val parked: Boolean?,
    val stopRule: Evidence = Evidence.UNKNOWN,
    val walkingPath: Evidence = Evidence.UNKNOWN,
    val receiver: Evidence = Evidence.UNKNOWN
)

data class Assessment(val status: Status, val reasons: List<String>, val checked: Int, val total: Int = 3)

/** No automatic safety, legal or accessibility certification. */
object ReadinessEngine {
    fun assess(s: StopCase): Assessment {
        val evidence = listOf(
            "정차 허용 여부" to s.stopRule,
            "승하차 동선" to s.walkingPath,
            "인계 상대 준비" to s.receiver
        )
        val checked = evidence.count { it.second == Evidence.CONFIRMED_BY_USER }
        if (s.parked != true) {
            return Assessment(Status.NOT_PARKED,
                listOf("정차 상태가 확인되지 않아 확인 화면을 제한합니다"), checked)
        }
        val neg = evidence.filter { it.second == Evidence.NEGATIVE_BY_USER }
            .map { "${it.first}: 부정 확인" }
        if (neg.isNotEmpty()) return Assessment(Status.RECONSIDER, neg, checked)
        val unknown = evidence.filter { it.second == Evidence.UNKNOWN }
            .map { "${it.first}: 아직 확인되지 않음" }
        if (unknown.isNotEmpty()) return Assessment(Status.VERIFY, unknown, checked)
        return Assessment(Status.FINAL_HUMAN_CHECK,
            listOf("모든 항목은 이용자 수동 확인이며 실제 현장 안전성을 보증하지 않습니다"), checked)
    }
}
