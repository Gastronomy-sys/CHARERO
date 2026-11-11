package io.evidock.app

import android.content.Context
import android.util.Log
import java.lang.reflect.Proxy

/** Subscribes to AAOS CarUxRestrictions without bundling the platform car jar. */
class PleosUxRestrictionMonitor(
    private val context: Context,
    private val onUpdate: (UxAvailability, String) -> Unit
) {
    private var car: Any? = null
    private var manager: Any? = null
    private var listener: Any? = null
    private var listenerType: Class<*>? = null
    private var generation: Int = 0

    fun start() {
        stop()
        val token = ++generation
        onUpdate(UxAvailability.UNKNOWN, "차량 UX 제한 확인 중")
        try {
            val carClass = Class.forName("android.car.Car")
            val instance = carClass.getMethod("createCar", Context::class.java)
                .invoke(null, context.applicationContext)
                ?: throw IllegalStateException("Car service not available")
            car = instance
            val name = carClass.getField("CAR_UX_RESTRICTION_SERVICE").get(null) as String
            val uxManager = carClass.getMethod("getCarManager", String::class.java)
                .invoke(instance, name)
                ?: throw IllegalStateException("UX restrictions manager not available")
            manager = uxManager
            val iface = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener"
            )
            listenerType = iface
            val handler = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, args ->
                when (method.name) {
                    "onUxRestrictionsChanged" -> {
                        if (token == generation) readRestrictions(args?.firstOrNull())
                        null
                    }
                    "toString" -> "EVIDOCK UX Listener"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            listener = handler
            uxManager.javaClass.getMethod("registerListener", iface).invoke(uxManager, handler)
            val current = uxManager.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(uxManager)
            readRestrictions(current)
        } catch (error: Throwable) {
            Log.w("EVIDOCK_UX", "CarUxRestrictions unavailable", error)
            stop()
            onUpdate(UxAvailability.UNKNOWN, "차량 UX 상태를 확인할 수 없습니다")
        }
    }

    private fun readRestrictions(value: Any?) {
        if (value == null) {
            onUpdate(UxAvailability.UNKNOWN, "차량 UX 상태를 확인할 수 없습니다")
            return
        }
        try {
            val cls = value.javaClass
            val requires = cls.getMethod("isRequiresDistractionOptimization").invoke(value) as Boolean
            val restrictions = cls.getMethod("getActiveRestrictions").invoke(value) as Int
            if (requires || restrictions != 0) {
                onUpdate(UxAvailability.RESTRICTED, "주행 관련 UX 제한 활성화")
            } else {
                onUpdate(UxAvailability.CLEAR, "플랫폼 UX 제한 없음 — 실제 정차 확인은 별도 필요")
            }
        } catch (error: Throwable) {
            Log.w("EVIDOCK_UX", "CarUxRestrictions read failed", error)
            onUpdate(UxAvailability.UNKNOWN, "차량 UX 상태를 확인할 수 없습니다")
        }
    }

    fun stop() {
        ++generation
        val m = manager
        if (m != null && listener != null) {
            try { m.javaClass.getMethod("unregisterListener").invoke(m) }
            catch (error: Throwable) { Log.w("EVIDOCK_UX", "Cannot unregister UX listener", error) }
        }
        val instance = car
        if (instance != null) {
            try { instance.javaClass.getMethod("disconnect").invoke(instance) }
            catch (error: Throwable) { Log.w("EVIDOCK_UX", "Car disconnect failed", error) }
        }
        car = null
        manager = null
        listener = null
        listenerType = null
    }
}
