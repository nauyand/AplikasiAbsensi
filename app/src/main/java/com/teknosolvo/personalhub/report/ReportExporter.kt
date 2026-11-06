package com.teknosolvo.personalhub.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.tracking.Stats
import com.teknosolvo.personalhub.util.DATE_MEDIUM
import com.teknosolvo.personalhub.util.durationShort
import com.teknosolvo.personalhub.util.hm
import com.teknosolvo.personalhub.util.toLocalDate
import com.teknosolvo.personalhub.util.zone
import java.io.ByteArrayOutputStream
import java.time.LocalDate

data class ReportFilter(
    val from: LocalDate,
    val to: LocalDate,
    /** Null = all places. */
    val placeId: Long? = null,
    /** Null = all kinds. */
    val kind: String? = null,
)

object ReportExporter {

    fun filter(all: List<DailyActivity>, f: ReportFilter): List<DailyActivity> = all.filter { a ->
        val d = a.startAt.toLocalDate()
        !d.isBefore(f.from) && !d.isAfter(f.to) &&
            (f.placeId == null || a.placeId == f.placeId) &&
            (f.kind == null || a.kind == f.kind)
    }.sortedBy { it.startAt }

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun csv(rows: List<DailyActivity>, now: Long = System.currentTimeMillis()): ByteArray {
        val sb = StringBuilder()
        sb.append("Tanggal,Lokasi,Jenis,Masuk,Keluar,Durasi (menit),Sumber,Dikoreksi,Catatan\n")
        for (a in rows) {
            val end = a.endAt
            val minutes = ((end ?: now) - a.startAt) / 60_000
            sb.append(listOf(
                a.date, esc(a.title), PlaceKind.label(a.kind), a.startAt.hm(), end?.hm() ?: "(masih di lokasi)",
                minutes.toString(), ActivitySource.label(a.source), if (a.edited) "Ya" else "Tidak", esc(a.note ?: ""),
            ).joinToString(","))
            sb.append('\n')
        }
        // UTF-8 BOM so spreadsheet apps detect the encoding.
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray()
    }

    fun pdf(rows: List<DailyActivity>, f: ReportFilter, title: String, now: Long = System.currentTimeMillis()): ByteArray {
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 40f
        val titlePaint = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(40, 30, 90) }
        val head = Paint().apply { textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.DKGRAY }
        val body = Paint().apply { textSize = 9.5f; color = Color.BLACK }
        val muted = Paint().apply { textSize = 9f; color = Color.GRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        val cols = floatArrayOf(margin, 120f, 270f, 330f, 385f, 440f)
        val headers = listOf("Tanggal", "Lokasi", "Masuk", "Keluar", "Durasi", "Ket.")

        val summary = Stats.period(
            generateSequence(f.from) { it.plusDays(1) }.takeWhile { !it.isAfter(f.to) }.toList(), rows, zone(), now,
        )

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            val c = page!!.canvas
            y = margin + 10
            if (pageNo == 1) {
                c.drawText(title, margin, y, titlePaint); y += 18
                c.drawText("Periode ${f.from.format(DATE_MEDIUM)} – ${f.to.format(DATE_MEDIUM)}", margin, y, muted); y += 14
                c.drawText(
                    "Kantor ${Stats.formatHours(summary.officeMs)} · Rumah ${Stats.formatHours(summary.homeMs)} · " +
                        "Istirahat ${Stats.formatHours(summary.breakMs)} · ${summary.officeDays} hari di kantor",
                    margin, y, muted,
                ); y += 12
                summary.avgOfficeArrival?.let {
                    c.drawText("Rata-rata datang ${Stats.hm(it)} · pulang ${summary.avgOfficeLeave?.let(Stats::hm) ?: "-"}", margin, y, muted)
                    y += 12
                }
                y += 10
            }
            headers.forEachIndexed { i, s -> c.drawText(s, cols[i], y, head) }
            y += 6
            c.drawLine(margin, y, w - margin, y, line)
            y += 14
        }
        newPage()
        for (a in rows) {
            if (y > h - margin) newPage()
            val c = page!!.canvas
            val end = a.endAt
            c.drawText(a.date, cols[0], y, body)
            c.drawText(a.title.take(28), cols[1], y, body)
            c.drawText(a.startAt.hm(), cols[2], y, body)
            c.drawText(end?.hm() ?: "-", cols[3], y, body)
            c.drawText(durationShort((end ?: now) - a.startAt), cols[4], y, body)
            val tag = buildList {
                if (a.isManual) add("manual")
                if (a.source == ActivitySource.SIMULATED) add("simulasi")
                a.note?.let { add(it.take(20)) }
            }.joinToString(", ")
            c.drawText(tag, cols[5], y, muted)
            y += 15
        }
        if (rows.isEmpty()) page!!.canvas.drawText("Tidak ada aktivitas pada periode ini.", margin, y, muted)
        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
