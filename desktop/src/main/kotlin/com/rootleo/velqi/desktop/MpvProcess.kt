package com.rootleo.velqi.desktop

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

/**
 * Controla una instancia de mpv (el mismo motor que usa Velqi en Windows)
 * via JSON IPC sobre un named pipe.
 *
 * Protocolo: request/response sincrono. Cada comando se escribe con un
 * request_id y se lee el pipe hasta recibir la respuesta con ese id.
 * Las respuestas de mpv son lineas JSON separadas por \n.
 */
class MpvProcess(
    private val mpvPath: String = System.getenv("MPV_PATH") ?: "C:\\Program Files\\MPV Player\\mpv.exe",
    private val pipeName: String = "velqi-luna-poc",
) : AutoCloseable {
    private var process: Process? = null
    private var raf: RandomAccessFile? = null
    private val lock = Any()
    private var nextId = 0L

    /** Ultimo evento end-file recibido (auto-avance de cola). */
    @Volatile
    var lastEndReason: String? = null

    @Volatile
    var ready: Boolean = false
        private set

    val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("win")

    /** Lanza mpv en idle y conecta al named pipe. */
    fun start(): String {
        if (ready) return "ok"
        if (!isWindows) {
            return "Este PoC usa named pipes de Windows; solo corre en Windows."
        }
        val process = try {
            ProcessBuilder(
                mpvPath,
                "--idle=yes",
                "--no-video",
                "--force-window=no",
                "--input-ipc-server=$pipeName",
                "--volume=100",
                // El stream de YouTube exige el User-Agent del cliente ANDROID (el del kernel)
                "--http-header-fields=User-Agent: com.google.android.youtube/20.01.35 (Linux; U; Android 13) gzip",
            ).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return "No se pudo lanzar mpv en '$mpvPath': ${e.message}"
        }
        this.process = process

        // Drenar la salida de mpv a un log para diagnosticar
        val logFile = java.io.File(System.getProperty("java.io.tmpdir"), "velqi-mpv.log")
        Thread {
            try {
                val log = logFile.bufferedWriter()
                process.inputStream.bufferedReader().forEachLine {
                    synchronized(log) { log.write(it); log.newLine(); log.flush() }
                }
                log.close()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        // Conectar al pipe con reintentos
        val pipePath = "\\\\.\\pipe\\$pipeName"
        var raf: RandomAccessFile? = null
        for (i in 0 until 40) {
            try {
                raf = RandomAccessFile(pipePath, "rw")
                break
            } catch (_: Exception) {
                Thread.sleep(250)
            }
        }
        if (raf == null) {
            process.destroy()
            return "mpv arranco pero no se pudo conectar al named pipe (${pipePath}). Log: ${logFile.absolutePath}"
        }
        this.raf = raf
        ready = true
        return "ok"
    }

    private fun readLineUtf8(): String? {
        val raf = this.raf ?: return null
        val out = ByteArrayOutputStream()
        while (true) {
            val b = raf.read()
            if (b == -1) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) break
            out.write(b)
        }
        return out.toString("UTF-8")
    }

    /**
     * Envia un comando JSON a mpv y espera su respuesta con el mismo request_id.
     * Devuelve el JSONObject de respuesta (con "data"/"error").
     */
    fun command(vararg args: Any?, timeoutMs: Long = 8000): JSONObject? {
        val raf = this.raf ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        return synchronized(lock) {
            val id = nextId++
            val sb = StringBuilder("{\"command\":[")
            args.forEachIndexed { i, arg ->
                if (i > 0) sb.append(",")
                sb.append(jsonValue(arg))
            }
            sb.append("],\"request_id\":").append(id).append("}\n")

            raf.write(sb.toString().toByteArray(Charsets.UTF_8))

            // Leer hasta recibir la respuesta de este request_id
            while (System.currentTimeMillis() < deadline) {
                val line = readLineUtf8() ?: return@synchronized null
                if (line.isBlank()) continue
                val obj = try {
                    JSONObject(line)
                } catch (_: Exception) {
                    continue
                }
                if (obj.has("request_id")) {
                    if (obj.optLong("request_id", -1) == id) {
                        return@synchronized obj
                    }
                    // respuesta de otro comando (no deberia pasar en modo sincrono)
                } else if (obj.optString("event") == "end-file") {
                    lastEndReason = obj.optString("reason", "unknown")
                }
            }
            null // timeout
        }
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean -> if (value) "true" else "false"
        is Number -> value.toString()
        else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    fun getProperty(name: String): Any? {
        val resp = command("get_property", name) ?: return null
        return if (resp.optString("error") == "success") resp.opt("data") else null
    }

    fun setProperty(name: String, value: Any?) {
        command("set_property", name, value)
    }

    fun loadUrl(url: String) {
        command("loadfile", url, "replace")
    }

    fun play() {
        setProperty("pause", false)
    }

    fun pause() {
        setProperty("pause", true)
    }

    fun seek(seconds: Double) {
        command("seek", seconds, "absolute+exact")
    }

    fun currentTime(): Double? = (getProperty("time-pos") as? Number)?.toDouble()

    fun duration(): Double? = (getProperty("duration") as? Number)?.toDouble()

    fun stop() {
        command("stop")
    }

    override fun close() {
        ready = false
        try {
            command("quit", timeoutMs = 500)
        } catch (_: Exception) {
        }
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        try {
            raf?.close()
        } catch (_: Exception) {
        }
    }
}
