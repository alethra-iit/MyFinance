package com.myfinance.app.util

import com.myfinance.app.data.FREQ_WEEKLY
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

val ID_LOCALE: Locale = Locale("id", "ID")

enum class Period(val label: String) {
    DAY("Harian"), WEEK("Mingguan"), MONTH("Bulanan"), YEAR("Tahunan")
}

object Fmt {
    fun symbol(c: String) = when (c) { "USD" -> "$"; "EUR" -> "€"; else -> "Rp" }

    fun money(v: Long, c: String = "IDR"): String {
        val sign = if (v < 0) "-" else ""
        val s = abs(v).toString().reversed().chunked(3).joinToString(".").reversed()
        return "$sign${symbol(c)} $s"
    }

    fun signed(v: Long, income: Boolean, c: String) = (if (income) "+" else "-") + money(abs(v), c)

    private fun trim(d: Double) =
        if (d % 1.0 == 0.0) d.toLong().toString() else String.format(ID_LOCALE, "%.1f", d)

    fun compact(v: Long): String = when {
        v >= 1_000_000_000L -> "${trim(v / 1e9)} M"
        v >= 1_000_000L -> "${trim(v / 1e6)} jt"
        v >= 1_000L -> "${trim(v / 1e3)} rb"
        else -> v.toString()
    }

    private fun f(p: String, ms: Long) = SimpleDateFormat(p, ID_LOCALE).format(Date(ms))
    fun date(ms: Long) = f("dd MMM yyyy", ms)
    fun dayMonth(ms: Long) = f("dd MMM", ms)
    fun monthYear(ms: Long) = f("MMM yyyy", ms)
    fun longMonth(ms: Long) = f("MMMM yyyy", ms)
    fun year(ms: Long) = f("yyyy", ms)
    fun weekday(ms: Long) = f("EEEE", ms)
    fun dayOfMonth(ms: Long) = f("d", ms)
    fun fileStamp(ms: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))
}

object Dates {
    const val DAY = 86_400_000L

    private fun cal(ms: Long): Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = ms
    }

    fun startOfDay(ms: Long): Long = cal(ms).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun today() = startOfDay(System.currentTimeMillis())

    /** Mengembalikan [awal, akhir) dari periode yang memuat anchor. Minggu dimulai hari Senin. */
    fun range(p: Period, anchor: Long): Pair<Long, Long> {
        val c = cal(startOfDay(anchor))
        when (p) {
            Period.DAY -> {}
            Period.WEEK -> {
                val diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                c.add(Calendar.DAY_OF_MONTH, -diff)
            }
            Period.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
            Period.YEAR -> { c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1) }
        }
        val start = c.timeInMillis
        when (p) {
            Period.DAY -> c.add(Calendar.DAY_OF_MONTH, 1)
            Period.WEEK -> c.add(Calendar.DAY_OF_MONTH, 7)
            Period.MONTH -> c.add(Calendar.MONTH, 1)
            Period.YEAR -> c.add(Calendar.YEAR, 1)
        }
        return start to c.timeInMillis
    }

    /** Kemunculan berikutnya setelah [current]. Bulanan: tanggal patokan, dipendekkan di bulan yang lebih pendek. */
    fun nextOccurrence(freq: String, current: Long, anchorDay: Int): Long {
        val c = cal(startOfDay(current))
        if (freq == FREQ_WEEKLY) {
            c.add(Calendar.DAY_OF_MONTH, 7)
        } else {
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, 1)
            c.set(Calendar.DAY_OF_MONTH, minOf(anchorDay.coerceAtLeast(1), c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return c.timeInMillis
    }

    fun dayOfMonthOf(ms: Long): Int = cal(ms).get(Calendar.DAY_OF_MONTH)

    fun shift(p: Period, anchor: Long, delta: Int): Long {
        val c = cal(anchor)
        when (p) {
            Period.DAY -> c.add(Calendar.DAY_OF_MONTH, delta)
            Period.WEEK -> c.add(Calendar.DAY_OF_MONTH, 7 * delta)
            Period.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, delta) }
            Period.YEAR -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.YEAR, delta) }
        }
        return c.timeInMillis
    }

    fun label(p: Period, anchor: Long): String {
        val (s, e) = range(p, anchor)
        return when (p) {
            Period.DAY -> Fmt.date(s)
            Period.WEEK -> "${Fmt.dayMonth(s)} – ${Fmt.date(e - 1)}"
            Period.MONTH -> Fmt.longMonth(s)
            Period.YEAR -> Fmt.year(s)
        }
    }

    /** Indeks batang grafik untuk sebuah tanggal. */
    fun bucket(p: Period, start: Long, ms: Long): Int {
        val c = cal(ms)
        return when (p) {
            Period.DAY -> 0
            Period.WEEK -> Math.round((startOfDay(ms) - start) / DAY.toDouble()).toInt()
            Period.MONTH -> c.get(Calendar.DAY_OF_MONTH) - 1
            Period.YEAR -> c.get(Calendar.MONTH)
        }
    }

    // DatePicker Material3 bekerja dalam UTC tengah malam; konversi ke/dari hari lokal.
    fun utcToLocal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun localToUtc(local: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
