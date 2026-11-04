package com.myfinance.app.util

import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.TransactionEntity
import com.myfinance.app.data.TransferEntity
import com.myfinance.app.data.WalletEntity

/**
 * Saldo tiap dompet (id dompet -> saldo):
 * saldo awal + pemasukan - pengeluaran + transfer masuk - transfer keluar.
 * Transfer hanya memindahkan uang, jadi jumlah semua saldo = total pemasukan - total pengeluaran + total saldo awal.
 */
fun walletBalances(
    wallets: List<WalletEntity>,
    txs: List<TransactionEntity>,
    transfers: List<TransferEntity>
): Map<Long, Long> {
    val m = HashMap<Long, Long>()
    wallets.forEach { m[it.id] = it.initialBalance }
    for (t in txs) {
        val before = m[t.walletId] ?: continue
        m[t.walletId] = before + if (t.type == TYPE_INCOME) t.amount else -t.amount
    }
    for (tr in transfers) {
        val from = m[tr.fromWalletId]
        val to = m[tr.toWalletId]
        if (from != null && to != null) {
            m[tr.fromWalletId] = from - tr.amount
            m[tr.toWalletId] = to + tr.amount
        }
    }
    return m
}
