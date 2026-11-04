@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.ID_LOCALE
import com.myfinance.app.util.Period
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private val Palette = listOf(
    Color(0xFF6554E8), Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF3B82F6),
    Color(0xFF14B8A6), Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF64748B)
)

@Composable
fun ReportScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val txs by vm.transactions.collectAsState()
    var periodName by rememberSaveable { mutableStateOf(Period.MONTH.name) }
    var anchor by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val period = Period.valueOf(periodName)
    val green = MaterialTheme.extra.income
    val red = MaterialTheme.extra.expense

    val (s, e) = Dates.range(period, anchor)
    val (ps, pe) = Dates.range(period, Dates.shift(period, anchor, -1))
    val data = txs.filter { it.dateMillis in s until e }
    val prev = txs.filter { it.dateMillis in ps until pe }

    val income = data.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val expense = data.filter { it.type != TYPE_INCOME }.sumOf { it.amount }
    val pIncome = prev.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val pExpense = prev.filter { it.type != TYPE_INCOME }.sumOf { it.amount }

    val n = when (period) {
        Period.DAY -> 1
        Period.WEEK -> 7
        Period.MONTH -> Math.round((e - s) / Dates.DAY.toDouble()).toInt()
        Period.YEAR -> 12
    }
    val incB = LongArray(n)
    val expB = LongArray(n)
    data.forEach {
        val i = Dates.bucket(period, s, it.dateMillis).coerceIn(0, n - 1)
        if (it.type == TYPE_INCOME) incB[i] += it.amount else expB[i] += it.amount
    }
    val labels = when (period) {
        Period.DAY -> listOf(Fmt.dayMonth(s))
        Period.WEEK -> listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        Period.MONTH -> (1..n).map { it.toString() }
        Period.YEAR -> listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    }
    val byCat = data.filter { it.type != TYPE_INCOME }.groupBy { it.category }
        .map { it.key to it.value.sumOf { t -> t.amount } }.sortedByDescending { it.second }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Laporan", onBack = { nav.popBackStack() }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pilih jenis periode
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.values().forEach { p ->
                    val sel = p == period
                    Surface(
                        onClick = { periodName = p.name }, modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                p.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // Pilih periode
            SoftCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { anchor = Dates.shift(period, anchor, -1) }) { Icon(Icons.Rounded.ChevronLeft, "Sebelumnya") }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { pickDate = true }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(Dates.label(period, anchor), fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { anchor = Dates.shift(period, anchor, 1) }) { Icon(Icons.Rounded.ChevronRight, "Berikutnya") }
                }
            }
            // Ringkasan
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Total Pemasukan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.money(income, cur), fontWeight = FontWeight.Bold, color = green)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total Pengeluaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.money(expense, cur), fontWeight = FontWeight.Bold, color = red)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Selisih", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.money(income - expense, cur), fontWeight = FontWeight.Bold, color = if (income - expense >= 0) green else red)
                    }
                }
            }
            // Grafik batang
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grafik Pemasukan & Pengeluaran", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("Pemasukan", green); Legend("Pengeluaran", red)
                    }
                    Spacer(Modifier.height(8.dp))
                    BarChart(incB.toList(), expB.toList(), labels, if (period == Period.MONTH) 5 else 1, green, red)
                }
            }
            // Perbandingan
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dibanding Periode Sebelumnya", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(Dates.label(period, Dates.shift(period, anchor, -1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CompareRow("Pemasukan", income, pIncome, upIsGood = true, cur = cur)
                    CompareRow("Pengeluaran", expense, pExpense, upIsGood = false, cur = cur)
                }
            }
            // Diagram kategori
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pengeluaran per Kategori", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    if (byCat.isEmpty()) {
                        EmptyState("Belum ada pengeluaran pada periode ini.")
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Donut(byCat.map { it.second }, Fmt.compact(expense))
                        }
                        Spacer(Modifier.height(12.dp))
                        byCat.forEachIndexed { i, (name, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Palette[i % Palette.size]))
                                Spacer(Modifier.width(10.dp))
                                Text(name, Modifier.weight(1f))
                                Text(String.format(ID_LOCALE, "%.0f%%", v * 100.0 / expense), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(Fmt.money(v, cur), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickDate) AppDatePickerDialog(anchor, { anchor = it; pickDate = false }, { pickDate = false })
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompareRow(label: String, now: Long, before: Long, upIsGood: Boolean, cur: String) {
    val text: String
    var color = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        now == 0L && before == 0L -> text = "Tidak ada data"
        before == 0L -> text = "Baru (periode lalu kosong)"
        else -> {
            val pct = (now - before) * 100.0 / before
            val up = pct >= 0
            text = (if (up) "▲ " else "▼ ") + String.format(ID_LOCALE, "%.1f%%", kotlin.math.abs(pct))
            color = if (up == upIsGood) MaterialTheme.extra.income else MaterialTheme.extra.expense
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text("Sebelumnya: ${Fmt.money(before, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.End)
    }
}

private fun niceStep(maxV: Long): Long {
    val raw = if (maxV <= 0) 25000.0 else maxV / 4.0
    val mag = 10.0.pow(floor(log10(raw)))
    val nrm = raw / mag
    val nice = when {
        nrm <= 1.0 -> 1.0
        nrm <= 2.0 -> 2.0
        nrm <= 2.5 -> 2.5
        nrm <= 5.0 -> 5.0
        else -> 10.0
    }
    return max(1L, (nice * mag).toLong())
}

@Composable
private fun BarChart(
    income: List<Long>, expense: List<Long>, labels: List<String>, labelEvery: Int,
    incomeColor: Color, expenseColor: Color
) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxV = max(income.maxOrNull() ?: 0L, expense.maxOrNull() ?: 0L)
    val step = niceStep(maxV)
    val top = (step * 4).toFloat()
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - left
        val chartH = size.height - bottom - topPad
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; color = axis.toArgb(); textSize = 10.sp.toPx()
        }
        for (i in 0..4) {
            val y = topPad + chartH - chartH * i / 4f
            drawLine(grid, Offset(left, y), Offset(size.width, y), 1f)
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(Fmt.compact(step * i), left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        val n = income.size
        val group = chartW / n
        val bw = min(group * 0.34f, 14.dp.toPx())
        paint.textAlign = android.graphics.Paint.Align.CENTER
        for (i in 0 until n) {
            val cx = left + group * i + group / 2f
            val ih = chartH * (income[i] / top)
            val eh = chartH * (expense[i] / top)
            if (ih > 0f) drawRect(incomeColor, Offset(cx - bw, topPad + chartH - ih), Size(bw, ih))
            if (eh > 0f) drawRect(expenseColor, Offset(cx, topPad + chartH - eh), Size(bw, eh))
            if (i % labelEvery == 0) drawContext.canvas.nativeCanvas.drawText(labels[i], cx, size.height - 6.dp.toPx(), paint)
        }
    }
}

@Composable
private fun Donut(values: List<Long>, centerText: String) {
    val total = values.sum().toFloat()
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 28.dp.toPx()
            val d = size.minDimension - stroke
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = v / total * 360f
                drawArc(
                    Palette[i % Palette.size], start, sweep, false,
                    topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(d, d), style = Stroke(stroke)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerText, fontWeight = FontWeight.Bold)
        }
    }
}
