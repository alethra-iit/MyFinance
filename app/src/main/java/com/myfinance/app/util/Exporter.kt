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

    /** Membuat berkas .xlsx asli (tanpa library tambahan). */
    fun xlsx(title: String, txs: List<TransactionEntity>): ByteArray {
        val rows = mutableListOf<List<Any>>()
        rows += listOf<Any>(title)
        rows += listOf<Any>("No", "Tanggal", "Jenis", "Kategori", "Keterangan", "Nominal (Rp)")
        txs.forEachIndexed { i, t ->
            rows += listOf<Any>(
                (i + 1).toLong(), Fmt.date(t.dateMillis),
                if (t.type == TYPE_INCOME) "Pemasukan" else "Pengeluaran",
                t.category, t.note, if (t.type == TYPE_INCOME) t.amount else -t.amount
            )
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
            row.forEachIndexed { c, v ->
                val ref = "${'A' + c}${r + 1}"
                if (v is Long) sb.append("<c r=\"$ref\"><v>$v</v></c>")
                else sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(v.toString())}</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun add(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            add("[Content_Types].xml", xml + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            add("_rels/.rels", xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add("xl/workbook.xml", xml + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Laporan" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            add("xl/_rels/workbook.xml.rels", xml + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            add("xl/worksheets/sheet1.xml", sb.toString())
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
