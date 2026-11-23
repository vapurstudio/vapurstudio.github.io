package com.vapurstudio.hyperkit

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs shell commands through `su` (Magisk / KernelSU / APatch) off the main thread. */
object Root {
    data class Res(val code: Int, val out: String) {
        val ok get() = code == 0
    }

    private val worker = Executors.newSingleThreadExecutor()
    val main = Handler(Looper.getMainLooper())

    /** Blocking: never call on the main thread. */
    fun exec(cmd: String, timeoutSec: Long = 20): Res = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }
        reader.start()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroy(); Res(-2, "timeout")
        } else {
            reader.join(1000); Res(p.exitValue(), out.toString().trim())
        }
    } catch (e: Exception) {
        Res(-1, e.message ?: "su not found")
    }

    fun run(cmd: String, cb: ((Res) -> Unit)? = null) {
        worker.execute {
            val r = exec(cmd)
            if (cb != null) main.post { cb(r) }
        }
    }

    fun check(cb: (Boolean) -> Unit) = run("id") { cb(it.ok && it.out.contains("uid=0")) }

    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
