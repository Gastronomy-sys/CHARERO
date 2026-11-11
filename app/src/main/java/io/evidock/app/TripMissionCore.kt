package io.evidock.app

/** Purpose-bound itinerary; a plan is not a navigation route. */
data class MissionStop(val place: String, val action: String, val checkTitle: String)
data class MissionPlan(val id: String, val title: String, val stops: List<MissionStop>)
data class MissionPosition(val planIndex: Int = 0, val stopIndex: Int = 0,
                           val finished: Boolean = false, val runsCompleted: Int = 0)

object TripMissionCore {
    // Example templates. They make no claims about automatic arrival detection.
    val plans: List<MissionPlan> = listOf(
        MissionPlan("errands", "퇴근 경유", listOf(
            MissionStop("세탁소", "맡긴 옷을 수령했는지 확인", "수령 물품 확인"),
            MissionStop("픽업 장소", "인계 상대와 탑승 준비를 직접 확인", "인계 상대 준비"),
            MissionStop("마트", "구매·수령할 물품을 직접 확인", "구매 물품 확인")
        )),
        MissionPlan("pickup", "가족 픽업", listOf(
            MissionStop("픽업 장소", "상대방의 준비와 승하차 동선을 확인", "인계 상대 준비"),
            MissionStop("귀가", "차량에 남겨진 짐이 없는지 직접 확인", "짐 정리 확인")
        )),
        MissionPlan("business", "업무 방문", listOf(
            MissionStop("첫 번째 방문처", "전달할 서류·물품을 직접 확인", "전달 물품 확인"),
            MissionStop("두 번째 방문처", "방문 목적을 수행했는지 직접 확인", "방문 업무 확인"),
            MissionStop("복귀 장소", "남은 업무 및 소지품을 직접 확인", "소지품 확인")
        ))
    )

    fun normalize(s: MissionPosition): MissionPosition {
        val index = s.planIndex.coerceIn(0, plans.lastIndex)
        val last = plans[index].stops.lastIndex
        return s.copy(planIndex = index, stopIndex = s.stopIndex.coerceIn(0, last), runsCompleted = s.runsCompleted.coerceAtLeast(0))
    }

    fun currentPlan(s: MissionPosition): MissionPlan = plans[normalize(s).planIndex]
    fun currentStop(s: MissionPosition): MissionStop {
        val state = normalize(s)
        return currentPlan(state).stops[state.stopIndex]
    }

    /** No editing without a clear platform UX state AND user-confirmed stop. */
    fun canChange(uxClear: Boolean, userStopConfirmed: Boolean): Boolean = uxClear && userStopConfirmed

    /** Manual positive checks only: a reported issue must never be auto-completed. */
    fun canComplete(uxClear: Boolean, userStopConfirmed: Boolean, manualPositive: List<Boolean>): Boolean =
        canChange(uxClear, userStopConfirmed) && manualPositive.size == 3 && manualPositive.all { it }

    fun choose(state: MissionPosition, index: Int): MissionPosition {
        require(index in plans.indices)
        return MissionPosition(index, 0, false, state.runsCompleted.coerceAtLeast(0))
    }

    fun advance(state: MissionPosition): MissionPosition {
        val s = normalize(state)
        require(!s.finished) { "Completed run must restart explicitly" }
        val last = currentPlan(s).stops.lastIndex
        return if (s.stopIndex < last) s.copy(stopIndex = s.stopIndex + 1)
               else s.copy(finished = true, runsCompleted = s.runsCompleted + 1)
    }

    fun restart(state: MissionPosition): MissionPosition {
        val s = normalize(state)
        require(s.finished) { "Cannot restart unfinished run" }
        return s.copy(stopIndex = 0, finished = false)
    }
}
