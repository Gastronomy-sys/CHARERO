package io.evidock.app

/** Unknown UX status never authorizes checklist input. */
enum class UxAvailability { UNKNOWN, RESTRICTED, CLEAR }

object UxAccessGate {
    fun mayEdit(ux: UxAvailability, manuallyStopped: Boolean?): Boolean =
        ux == UxAvailability.CLEAR && manuallyStopped == true

    fun shouldResetOnTransition(previous: UxAvailability, current: UxAvailability): Boolean =
        previous == UxAvailability.CLEAR && current != UxAvailability.CLEAR
}
