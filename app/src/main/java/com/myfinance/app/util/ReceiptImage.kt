package com.myfinance.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Foto struk -> JPEG kecil -> Base64.
 * Base64 sendiri membuat data ~33% LEBIH BESAR dari biner-nya; yang membuatnya kecil adalah
 * pengecilan dimensi + kompresi JPEG di sini (target biner <= [MAX_BYTES], jadi Base64 sekitar 105 KB).
 */
object ReceiptImage {
    private const val MAX_SIDE = 1024        // sisi terpanjang awal (px); cukup agar tulisan struk terbaca
    private const val MIN_SIDE = 480         // batas terkecil bila masih terlalu besar
    private const val MAX_BYTES = 80_000     // target ukuran JPEG
    private val QUALITIES = intArrayOf(75, 65, 55, 45, 35)

    /** Baca gambar dari [uri], perbaiki rotasi EXIF, perkecil & kompres. Null bila gambar tidak bisa dibaca. */
    fun encode(resolver: ContentResolver, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode kasar dulu (inSampleSize pangkat 2) agar foto 12 MP tidak memenuhi memori
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val upright = applyOrientation(decoded, orientation)
        if (upright !== decoded) decoded.recycle()

        var side = MAX_SIDE
        try {
            while (true) {
                val scaled = scaleFlat(upright, side)
                try {
                    for (q in QUALITIES) {
                        val bos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, q, bos)
                        val last = side <= MIN_SIDE && q == QUALITIES.last()
                        if (bos.size() <= MAX_BYTES || last) return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                    }
                } finally {
                    if (scaled !== upright) scaled.recycle()
                }
                side = (side * 0.85f).toInt().coerceAtLeast(MIN_SIDE)
            }
        } finally {
            upright.recycle()
        }
    }

    /** Base64 -> Bitmap untuk ditampilkan. Null bila data rusak. */
    fun decode(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Perkecil sampai sisi terpanjang = [side] (tidak pernah memperbesar), di atas latar putih (JPEG tak punya alfa). */
    private fun scaleFlat(src: Bitmap, side: Int): Bitmap {
        val longest = max(src.width, src.height)
        val s = if (longest > side) side.toFloat() / longest else 1f
        val w = (src.width * s).toInt().coerceAtLeast(1)
        val h = (src.height * s).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val m = Matrix().apply { postScale(s, s) }
        c.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
