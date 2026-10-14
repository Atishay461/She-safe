package com.aaforge.shesafe.core

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeProvider {
    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX (EEEE, dd MMM yyyy hh:mm a)", Locale.US)

    fun nowFormatted(): String {
        return ZonedDateTime.now().format(isoFormatter)
    }
}
