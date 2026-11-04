package com.myfinance.app.util

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.TransactionEntity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Satu sel Excel maksimal 32.767 karakter, jadi Base64 dipotong per [CHUNK] karakter ke beberapa kolom. */
    private const val CHUNK = 30_000

    /** 0 -> A, 25 -> Z, 26 -> AA ... (kode lama 'A' + c rusak setelah kolom Z). */
    private fun col(i: Int): String {
        var n = i; val sb = StringBuilder()
        do { sb.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    private fun cell(sb: Appendable, ref: String, v: Any) {
        if (v is Long) sb.append("<c r=\"$ref\"><v>$v</v></c>")
        else sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(v.toString())}</t></is></c>")
    }

    /**
     * Membuat berkas .xlsx asli (tanpa library tambahan).
     * [receipts] = id transaksi -> foto struk Base64. Bila ada, kolom "Struk" ditambahkan di sheet Laporan dan
     * sheet kedua "Lampiran Struk" berisi Base64-nya (dipotong per kolom). Gabungkan semua bagian lalu decode
     * Base64 untuk mendapatkan kembali berkas JPEG.
     */
    fun xlsx(title: String, txs: List<TransactionEntity>, receipts: Map<Long, String> = emptyMap(), cur: String = "IDR"): ByteArray {
        val rows = mutableListOf<List<Any>>()
        rows += listOf<Any>(title)
        val withReceipts = receipts.isNotEmpty()
        rows += if (withReceipts) listOf<Any>("No", "Tanggal", "Jenis", "Kategori", "Keterangan", "Nominal (${Fmt.symbol(cur)})", "Struk")
        else listOf<Any>("No", "Tanggal", "Jenis", "Kategori", "Keterangan", "Nominal (${Fmt.symbol(cur)})")
        txs.forEachIndexed { i, t ->
            val base = listOf<Any>(
                (i + 1).toLong(), Fmt.date(t.dateMillis),
                if (t.type == TYPE_INCOME) "Pemasukan" else "Pengeluaran",
                t.category, t.note, if (t.type == TYPE_INCOME) t.amount else -t.amount
            )
            rows += if (withReceipts) base + (if (receipts.containsKey(t.id)) "Ada (sheet Lampiran Struk)" else "-") else base
        }
        val inc = txs.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
        val exp = txs.filter { it.type != TYPE_INCOME }.sumOf { it.amount }
        rows += listOf<Any>("")
        rows += listOf<Any>("", "", "", "", "Total Pemasukan", inc)
        rows += listOf<Any>("", "", "", "", "Total Pengeluaran", exp)
        rows += listOf<Any>("", "", "", "", "Selisih", inc - exp)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v -> cell(sb, "${col(c)}${r + 1}", v) }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun add(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            val ws = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
            val sheet2Type = if (withReceipts) """<Override PartName="/xl/worksheets/sheet2.xml" ContentType="$ws"/>""" else ""
            add("[Content_Types].xml", xml + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="$ws"/>$sheet2Type</Types>""")
            add("_rels/.rels", xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add("xl/workbook.xml", xml + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Laporan" sheetId="1" r:id="rId1"/>${if (withReceipts) """<sheet name="Lampiran Struk" sheetId="2" r:id="rId2"/>""" else ""}</sheets></workbook>""")
            add("xl/_rels/workbook.xml.rels", xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>${if (withReceipts) """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""" else ""}</Relationships>""")
            add("xl/worksheets/sheet1.xml", sb.toString())
            if (withReceipts) {
                // Ditulis langsung ke zip (tanpa membangun satu string raksasa) karena Base64 ukurannya besar
                z.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
                val w = z.writer(Charsets.UTF_8)
                w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                w.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                fun line(r: Int, cells: List<Any>) {
                    w.write("<row r=\"$r\">")
                    cells.forEachIndexed { c, v -> cell(w, "${col(c)}$r", v) }
                    w.write("</row>")
                }
                line(1, listOf("No (sama dengan sheet Laporan)", "Tanggal", "Keterangan", "Format", "Jumlah bagian", "Bagian 1, 2, 3, ... (gabungkan berurutan lalu decode Base64)"))
                var r = 2
                txs.forEachIndexed { i, t ->
                    val data = receipts[t.id] ?: return@forEachIndexed
                    val parts = data.chunked(CHUNK)
                    line(r++, listOf<Any>((i + 1).toLong(), Fmt.date(t.dateMillis), t.note.ifBlank { t.category }, "image/jpeg", parts.size.toLong()) + parts)
                }
                w.write("</sheetData></worksheet>")
                w.flush()
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    fun pdf(title: String, txs: List<TransactionEntity>, cur: String): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true; textSize = 10f }
        val bold = Paint(paint).apply { isFakeBoldText = true }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var canvas = page.canvas
        var y = 56f

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            canvas = page.canvas
            y = 50f
        }
        fun header() {
            canvas.drawText("Tanggal", 40f, y, bold); canvas.drawText("Jenis", 120f, y, bold)
            canvas.drawText("Kategori", 195f, y, bold); canvas.drawText("Keterangan", 280f, y, bold)
            bold.textAlign = Paint.Align.RIGHT; canvas.drawText("Nominal", 555f, y, bold); bold.textAlign = Paint.Align.LEFT
            y += 6f; canvas.drawLine(40f, y, 555f, y, paint); y += 14f
        }

        bold.textSize = 16f; canvas.drawText(title, 40f, y, bold); bold.textSize = 10f; y += 20f
        val inc = txs.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
        val exp = txs.filter { it.type != TYPE_INCOME }.sumOf { it.amount }
        canvas.drawText("Total Pemasukan : ${Fmt.money(inc, cur)}", 40f, y, paint); y += 14f
        canvas.drawText("Total Pengeluaran : ${Fmt.money(exp, cur)}", 40f, y, paint); y += 14f
        canvas.drawText("Selisih : ${Fmt.money(inc - exp, cur)}", 40f, y, bold); y += 24f
        header()

        txs.forEach { t ->
            if (y > 800f) { newPage(); header() }
            val income = t.type == TYPE_INCOME
            canvas.drawText(Fmt.date(t.dateMillis), 40f, y, paint)
            canvas.drawText(if (income) "Pemasukan" else "Pengeluaran", 120f, y, paint)
            canvas.drawText(t.category.take(14), 195f, y, paint)
            canvas.drawText(t.note.take(26), 280f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Fmt.signed(t.amount, income, cur), 555f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 16f
        }
        if (txs.isEmpty()) canvas.drawText("Tidak ada transaksi pada periode ini.", 40f, y, paint)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
