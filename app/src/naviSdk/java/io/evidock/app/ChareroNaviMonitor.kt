package io.evidock.app

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** Read-only integration with the active Pleos navigation session.
 *  Neither an arrival callback nor a route change completes a user's real-world task.
 */
class ChareroNaviMonitor(
    private val context: Context,
    private val onState: (message: String, arrivalEvent: Boolean) -> Unit
) {
    private var helper: NaviHelper? = null
    private var running = false
    private var registered = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            deliver("내비게이션 경로 안내 시작", false)
        }
        override fun onRoutingCompleted() {
            deliver("내비게이션 경로 탐색 완료", false)
        }
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            deliver("내비게이션 경유지 ${waypointInfoList.size}개 확인", false)
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            deliver("내비게이션 목적지 정보 수신", false)
        }
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            deliver("내비 경유지 도착 알림 · 정차 후 할 일 확인", true)
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            deliver("내비 목적지 도착 알림 · 정차 후 할 일 확인", true)
        }
        override fun onError(errorCode: NaviErrorCode) {
            deliver("내비게이션 응답 오류 · 수동 점검은 가능", false)
        }
    }

    private fun deliver(message: String, arrivalEvent: Boolean) {
        if (running) {
            Log.i("CHARERO_NAVI", if (arrivalEvent) "ARRIVAL_EVENT_RECEIVED" else message)
            onState(message, arrivalEvent)
        }
    }

    fun start() {
        if (running) return
        running = true
        deliver("Pleos 내비 연결 확인 중", false)
        try {
            val instance = NaviHelper(context.applicationContext)
            helper = instance
            instance.initialize(
                onConnected = {
                    if (running && helper === instance) {
                        try {
                            instance.addListener(listener)
                            registered = true
                            deliver("Pleos 내비 연결됨 · 현재 경로 조회", false)
                            instance.getDestinationInfo()
                            instance.getWaypointInfo()
                        } catch (_: Exception) {
                            deliver("내비 정보 조회 불가 · 수동 점검 유지", false)
                        }
                    }
                },
                onFailed = { _ ->
                    deliver("내비 서비스 연결 실패 · 수동 점검 유지", false)
                }
            )
        } catch (_: Exception) {
            deliver("NaviHelper 사용 불가 · 수동 점검 유지", false)
        }
    }

    fun stop() {
        running = false
        val instance = helper
        if (instance != null) {
            if (registered) runCatching { instance.removeListener(listener) }
            runCatching { instance.release() }
        }
        registered = false
        helper = null
    }
}
