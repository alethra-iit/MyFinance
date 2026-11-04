package com.myfinance.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.BudgetStatus
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.budgetMessage

val BudgetAmber = Color(0xFFF59E0B)

@Composable
fun budgetColor(level: Int): Color = when (level) {
    2 -> MaterialTheme.extra.expense
    1 -> BudgetAmber
    else -> MaterialTheme.extra.income
}

@Composable
fun BudgetBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** Satu baris anggaran: ikon, nama, terpakai / batas, bar progres, persen. money() menghormati mode "sembunyikan saldo". */
@Composable
fun BudgetRow(st: BudgetStatus, money: (Long) -> String, modifier: Modifier = Modifier) {
    val color = budgetColor(st.level)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(categoryIcon(st.category, TYPE_EXPENSE), categoryColor(st.category, TYPE_EXPENSE), size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(st.category, fontWeight = FontWeight.SemiBold)
                Text(
                    "${money(st.spent)} dari ${money(st.limit)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${st.pct}%", fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(8.dp))
        BudgetBar(st.fraction, color)
        if (st.level == 2) {
            Spacer(Modifier.height(4.dp))
            Text("Melewati batas ${money(st.spent - st.limit)}", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun BudgetWarningBanner(st: BudgetStatus, cur: String, hidden: Boolean) {
    val color = budgetColor(st.level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .16f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Warning, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(
            if (hidden) (if (st.level == 2) "Anggaran ${st.category} terlampaui (${st.pct}%)" else "Anggaran ${st.category} sudah ${st.pct}%")
            else budgetMessage(st, cur),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
        )
    }
}
