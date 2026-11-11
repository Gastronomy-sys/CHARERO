package io.evidock.app

import android.content.Context

/** Offline smoke-test implementation, intentionally without navigation functionality. */
class ChareroNaviMonitor(
    context: Context,
    private val onState: (message: String, arrivalEvent: Boolean) -> Unit
) {
    fun start() { onState("내비 SDK 미포함 빌드 · 수동 점검만 가능", false) }
    fun stop() {}
}
