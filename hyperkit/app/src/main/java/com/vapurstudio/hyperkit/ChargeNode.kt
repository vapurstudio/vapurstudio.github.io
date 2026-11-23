package com.vapurstudio.hyperkit

/** Known kernel switches that pause charging. Which one exists depends on the phone. */
object ChargeNode {
    // path to (value that stops charging, value that resumes)
    val CANDIDATES = listOf(
        "/sys/class/power_supply/battery/input_suspend" to ("1" to "0"),
        "/sys/class/power_supply/battery/charging_enabled" to ("0" to "1"),
        "/sys/class/power_supply/battery/battery_charging_enabled" to ("0" to "1"),
        "/sys/class/qcom-battery/input_suspend" to ("1" to "0"),
        "/sys/class/power_supply/battery/store_mode" to ("1" to "0"),
    )

    fun write(path: String, stop: Boolean): String {
        val v = CANDIDATES.firstOrNull { it.first == path }?.second ?: ("1" to "0")
        return "echo ${if (stop) v.first else v.second} > $path"
    }

    /** Shell snippet printing the first node that exists. */
    fun detectCmd() = CANDIDATES.joinToString("; ") { "[ -e ${it.first} ] && echo ${it.first}" } + "; true"
}
