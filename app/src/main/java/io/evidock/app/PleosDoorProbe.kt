package io.evidock.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.content.Context
import android.util.Log

/** Read-only vehicle SDK probe; a door position does not certify safe stopping. */
class PleosDoorProbe(
    context: Context,
    private val onResult: (String) -> Unit
) {
    private val context = context.applicationContext
    private var vehicle: Vehicle? = null
    private var generation = 0

    fun start() {
        stop()
        val token = ++generation
        onResult("차량 Door SDK 연결 확인 중")
        try {
            val v = Vehicle(context)
            vehicle = v
            v.initialize()
            val door = v.getDoor()
            door.checkDoorPositionCapabilities(
                onComplete = { config, ranges ->
                    if (token == generation) {
                        val access = config.accessType.name
                        if (access != "READ" && access != "READ_WRITE") {
                            onResult("차량 문 위치 읽기 미지원")
                        } else {
                            val area = DoorArea(DoorValue.ROW_1_LEFT)
                            if (!ranges.containsKey(area)) {
                                onResult("운전석 앞문 영역 미지원")
                            } else {
                                door.getCurrentDoorPosition(
                                    area = area,
                                    onComplete = { value: Int? ->
                                        if (token == generation) onResult("차량 운전석 문 위치 원시값: ${value ?: "없음"}")
                                    },
                                    onFailed = { error: Exception ->
                                        if (token == generation) onResult("문 위치 조회 실패: ${error.javaClass.simpleName}")
                                    }
                                )
                            }
                        }
                    }
                },
                onFailed = { error: Exception ->
                    if (token == generation) onResult("Door 기능 조회 실패: ${error.javaClass.simpleName}")
                }
            )
        } catch (error: Throwable) {
            Log.w("EVIDOCK_VEHICLE", "Door SDK unavailable", error)
            stop()
            onResult("Vehicle SDK 연결 실패 — 문 상태 미확인")
        }
    }

    fun stop() {
        ++generation
        try { vehicle?.release() }
        catch (error: Throwable) { Log.w("EVIDOCK_VEHICLE", "Vehicle SDK release failed", error) }
        vehicle = null
    }
}
