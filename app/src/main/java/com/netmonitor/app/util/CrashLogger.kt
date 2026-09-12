package com.netmonitor.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))

                val time = SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.US
                ).format(Date())

                val file = File(
                    context.filesDir,
                    "crash_$time.txt"
                )

                file.writeText(
                    "Thread: ${thread.name}\n\n${sw}"
                )
            } catch (_: Exception) {
                // ignore
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
