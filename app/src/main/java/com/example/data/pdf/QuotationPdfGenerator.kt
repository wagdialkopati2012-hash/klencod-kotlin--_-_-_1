package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.DateUtils
import com.example.core.MoneyUtils
import com.example.data.local.CompanyInfoEntity
import com.example.data.local.QuotationWithItems
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal

object QuotationPdfGenerator {

  // Standard A4 in PDF points (72 dpi): 595 x 842
  const val A4_WIDTH = 595
  const val A4_HEIGHT = 842

  private const val MARGIN_H = 34f
  private const val MARGIN_TOP = 28f
  private const val MARGIN_BOTTOM = 34f

  // Exact RTL column widths (sum = 527f = 595 - 2*34)
  // Order Right to Left:
  // 1. اسم الصنف (rightmost)
  // 2. الكمية
  // 3. السعر
  // 4. الإجمالي (leftmost)
  private const val COL_TOTAL_W = 105f
  private const val COL_PRICE_W = 92f
  private const val COL_QTY_W = 68f
  private const val COL_NAME_W = 262f

  private val navyColor = Color.parseColor("#172B4D")
  private val royalBlueColor = Color.parseColor("#2563EB")
  private val tealColor = Color.parseColor("#13B8A6")
  private val lightHeaderBg = Color.parseColor("#EAF2FF")
  private val zebraBg = Color.parseColor("#F7FAFF")
  private val borderColor = Color.parseColor("#DCE5F2")
  private val mainTextColor = Color.parseColor("#172033")
  private val mutedTextColor = Color.parseColor("#64748B")

  private val renderLock = Any()

  private fun decodeLogo(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    if (!File(path).exists()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 512) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
      }
      .getOrNull()
  }

  /**
   * Builds the PDF file and the preview bitmaps safely. Never throws: tries lower bitmap scales if
   * memory is short, and falls back to drawing directly if the PDF renderer fails.
   * Returns null only if nothing could be rendered at all.
   */
  fun buildPreview(
    context: Context,
    quotationWithItems: QuotationWithItems,
    companyInfo: CompanyInfoEntity,
  ): Pair<File?, List<Bitmap>>? {
    val file = runCatching { generatePdfFile(context, quotationWithItems, companyInfo) }.getOrNull()
    for (scale in floatArrayOf(2.0f, 1.5f, 1.0f)) {
      var bitmaps: List<Bitmap> =
        if (file != null) {
          runCatching { renderPreviewBitmaps(file, scale) }.getOrNull().orEmpty()
        } else {
          emptyList()
        }
      if (bitmaps.isEmpty()) {
        bitmaps =
          runCatching {
              renderPageBitmapsDirect(context, quotationWithItems, companyInfo, scale)
            }
            .getOrNull()
            .orEmpty()
      }
      if (bitmaps.isNotEmpty()) return file to bitmaps
    }
    return null
  }

  fun generatePdfFile(
    context: Context,
    quotationWithItems: QuotationWithItems,
    companyInfo: CompanyInfoEntity,
  ): File = synchronized(renderLock) { generatePdfFileLocked(context, quotationWithItems, companyInfo) }

  private fun generatePdfFileLocked(
    context: Context,
    quotationWithItems: QuotationWithItems,
    companyInfo: CompanyInfoEntity,
  ): File {
    val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
    val safeNumber =
      quotationWithItems.quotation.quotationNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val outputFile = File(pdfDir, "quotation_${safeNumber}.pdf")

    val baseTypeface =
      ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic) ?: Typeface.DEFAULT
    val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)

    val logoBitmap = decodeLogo(companyInfo.logoPath)

    val items = quotationWithItems.items
    val itemsPerFirstPage = 12
    val itemsPerFollowingPage = 18

    val pages = mutableListOf<List<com.example.data.local.QuotationItemEntity>>()
    if (items.isEmpty()) {
      pages.add(emptyList())
    } else {
      val firstChunk = items.take(itemsPerFirstPage)
      pages.add(firstChunk)
      var offset = firstChunk.size
      while (offset < items.size) {
        val nextChunk = items.drop(offset).take(itemsPerFollowingPage)
        pages.add(nextChunk)
        offset += nextChunk.size
      }
    }

    val document = PdfDocument()
    try {
      pages.forEachIndexed { pageIndex, pageItems ->
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIndex + 1).create()
        val page = document.startPage(pageInfo)
        drawPage(
          canvas = page.canvas,
          pageIndex = pageIndex,
          totalPages = pages.size,
          quotationWithItems = quotationWithItems,
          pageItems = pageItems,
          companyInfo = companyInfo,
          logoBitmap = logoBitmap,
          regularTypeface = baseTypeface,
          boldTypeface = boldTypeface,
        )
        document.finishPage(page)
      }

      FileOutputStream(outputFile).use { fos -> document.writeTo(fos) }
    } finally {
      document.close()
      logoBitmap?.recycle()
    }
    return outputFile
  }

  fun renderPreviewBitmaps(pdfFile: File, scaleFactor: Float = 2.0f): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    fd.use { pfd ->
      PdfRenderer(pfd).use { renderer ->
        for (i in 0 until renderer.pageCount) {
          renderer.openPage(i).use { page ->
            val width = (page.width * scaleFactor).toInt().coerceAtLeast(1)
            val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
          }
        }
      }
    }
    return bitmaps
  }

  fun renderPageBitmapsDirect(
    context: Context,
    quotationWithItems: QuotationWithItems,
    companyInfo: CompanyInfoEntity,
    scaleFactor: Float = 2.0f,
  ): List<Bitmap> {
    val baseTypeface =
      ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic) ?: Typeface.DEFAULT
    val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
    val logoBitmap = decodeLogo(companyInfo.logoPath)

    val items = quotationWithItems.items
    val itemsPerFirstPage = 12
    val itemsPerFollowingPage = 18
    val pages = mutableListOf<List<com.example.data.local.QuotationItemEntity>>()
    if (items.isEmpty()) {
      pages.add(emptyList())
    } else {
      val firstChunk = items.take(itemsPerFirstPage)
      pages.add(firstChunk)
      var offset = firstChunk.size
      while (offset < items.size) {
        val nextChunk = items.drop(offset).take(itemsPerFollowingPage)
        pages.add(nextChunk)
        offset += nextChunk.size
      }
    }

    val bitmaps = mutableListOf<Bitmap>()
    try {
      pages.forEachIndexed { pageIndex, pageItems ->
        val w = (A4_WIDTH * scaleFactor).toInt().coerceAtLeast(1)
        val h = (A4_HEIGHT * scaleFactor).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scaleFactor, scaleFactor)
        drawPage(
          canvas = canvas,
          pageIndex = pageIndex,
          totalPages = pages.size,
          quotationWithItems = quotationWithItems,
          pageItems = pageItems,
          companyInfo = companyInfo,
          logoBitmap = logoBitmap,
          regularTypeface = baseTypeface,
          boldTypeface = boldTypeface,
        )
        bitmaps.add(bmp)
      }
    } finally {
      logoBitmap?.recycle()
    }
    return bitmaps
  }

  fun sharePdf(context: Context, pdfFile: File, quotationNumber: String) {
    val uri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "عرض سعر رقم $quotationNumber")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(intent, "مشاركة عرض السعر PDF"))
  }

  fun printPdf(context: Context, pdfFile: File, quotationNumber: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes?,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback?,
          extras: Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder("quotation_$quotationNumber.pdf")
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
              .build()
          callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>?,
          destination: ParcelFileDescriptor?,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback?,
        ) {
          try {
            FileInputStream(pdfFile).use { input ->
              FileOutputStream(destination?.fileDescriptor).use { output ->
                input.copyTo(output)
              }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
          }
        }
      }
    printManager.print("عرض سعر $quotationNumber", adapter, PrintAttributes.Builder().build())
  }

  private fun drawPage(
    canvas: Canvas,
    pageIndex: Int,
    totalPages: Int,
    quotationWithItems: QuotationWithItems,
    pageItems: List<com.example.data.local.QuotationItemEntity>,
    companyInfo: CompanyInfoEntity,
    logoBitmap: Bitmap?,
    regularTypeface: Typeface,
    boldTypeface: Typeface,
  ) {
    // Clean white A4 paper background
    canvas.drawColor(Color.WHITE)

    val contentLeft = MARGIN_H
    val contentRight = A4_WIDTH - MARGIN_H
    val contentWidth = contentRight - contentLeft
    var y = MARGIN_TOP

    // Top corporate accent gradient bar (Corporate Blue -> Teal)
    val topBarPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader =
          LinearGradient(
            contentLeft,
            y,
            contentRight,
            y,
            intArrayOf(navyColor, royalBlueColor, tealColor),
            null,
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRoundRect(RectF(contentLeft, y, contentRight, y + 6f), 3f, 3f, topBarPaint)
    y += 16f

    // Header block: Company info (Right side in RTL) + Logo (Left or Right depending on aesthetics)
    val headerPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFD")
      }
    val headerBorderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 1f
      }

    if (pageIndex == 0) {
      val headerHeight = 98f
      val headerRect = RectF(contentLeft, y, contentRight, y + headerHeight)
      canvas.drawRoundRect(headerRect, 12f, 12f, headerPaint)
      canvas.drawRoundRect(headerRect, 12f, 12f, headerBorderPaint)

      // Draw Logo on the left side of the RTL header (or decorative corporate badge if none)
      val logoBoxSize = 68f
      val logoRect =
        RectF(
          contentLeft + 16f,
          y + 15f,
          contentLeft + 16f + logoBoxSize,
          y + 15f + logoBoxSize,
        )
      if (logoBitmap != null) {
        canvas.save()
        val path =
          android.graphics.Path().apply {
            addRoundRect(logoRect, 10f, 10f, android.graphics.Path.Direction.CW)
          }
        canvas.clipPath(path)
        canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
      } else {
        val badgePaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
              LinearGradient(
                logoRect.left,
                logoRect.top,
                logoRect.right,
                logoRect.bottom,
                navyColor,
                royalBlueColor,
                Shader.TileMode.CLAMP,
              )
          }
        canvas.drawRoundRect(logoRect, 14f, 14f, badgePaint)
        drawRtlText(
          canvas = canvas,
          text = "عرض\nرسمي",
          x = logoRect.left,
          y = logoRect.top + 14f,
          width = logoRect.width().toInt(),
          sizeSp = 12f,
          color = Color.WHITE,
          typeface = boldTypeface,
          alignment = Layout.Alignment.ALIGN_CENTER,
        )
      }

      // Company text on the right side
      val textRightX = contentLeft + 100f
      val textWidth = (contentRight - 16f - textRightX).toInt()
      val companyName = companyInfo.companyName.ifBlank { "نظام المناقصات" }
      drawRtlText(
        canvas = canvas,
        text = companyName,
        x = textRightX,
        y = y + 12f,
        width = textWidth,
        sizeSp = 16f,
        color = navyColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      if (companyInfo.companySubtitle.isNotBlank()) {
        drawRtlText(
          canvas = canvas,
          text = companyInfo.companySubtitle,
          x = textRightX,
          y = y + 36f,
          width = textWidth,
          sizeSp = 10f,
          color = royalBlueColor,
          typeface = regularTypeface,
          alignment = Layout.Alignment.ALIGN_NORMAL,
        )
      }
      val contactLine =
        listOf(companyInfo.phone, companyInfo.address)
          .filter { it.isNotBlank() }
          .joinToString("  •  ")
      if (contactLine.isNotBlank()) {
        drawRtlText(
          canvas = canvas,
          text = contactLine,
          x = textRightX,
          y = y + 54f,
          width = textWidth,
          sizeSp = 9.5f,
          color = mutedTextColor,
          typeface = regularTypeface,
          alignment = Layout.Alignment.ALIGN_NORMAL,
        )
      }
      if (companyInfo.extraInfo.isNotBlank()) {
        drawRtlText(
          canvas = canvas,
          text = companyInfo.extraInfo,
          x = textRightX,
          y = y + 72f,
          width = textWidth,
          sizeSp = 8.5f,
          color = mutedTextColor,
          typeface = regularTypeface,
          alignment = Layout.Alignment.ALIGN_NORMAL,
        )
      }
      y += headerHeight + 16f

      // Document Title: عرض سعر
      drawRtlText(
        canvas = canvas,
        text = "عرض سعر",
        x = contentLeft,
        y = y,
        width = contentWidth.toInt(),
        sizeSp = 22f,
        color = navyColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_CENTER,
      )
      y += 32f

      // Thin teal divider under title
      val dividerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = tealColor
          strokeWidth = 2f
        }
      canvas.drawLine(
        A4_WIDTH / 2f - 45f,
        y,
        A4_WIDTH / 2f + 45f,
        y,
        dividerPaint,
      )
      y += 14f

      // Quotation Metadata card
      val metaRect = RectF(contentLeft, y, contentRight, y + 66f)
      canvas.drawRoundRect(metaRect, 10f, 10f, headerPaint)
      canvas.drawRoundRect(metaRect, 10f, 10f, headerBorderPaint)

      val q = quotationWithItems.quotation
      drawRtlText(
        canvas = canvas,
        text = "رقم العرض: ${q.quotationNumber}",
        x = contentLeft + 14f,
        y = y + 10f,
        width = (contentWidth - 28f).toInt(),
        sizeSp = 10.5f,
        color = mainTextColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      drawRtlText(
        canvas = canvas,
        text = "اسم العميل: ${q.customerName}",
        x = contentLeft + 14f,
        y = y + 29f,
        width = (contentWidth - 28f).toInt(),
        sizeSp = 10.5f,
        color = mainTextColor,
        typeface = regularTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      drawRtlText(
        canvas = canvas,
        text = "التاريخ: ${DateUtils.formatNumericDate(q.quotationDateMillis)}",
        x = contentLeft + 14f,
        y = y + 46f,
        width = (contentWidth - 28f).toInt(),
        sizeSp = 9.5f,
        color = mutedTextColor,
        typeface = regularTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      y += 80f
    } else {
      // Continuation page compact header
      drawRtlText(
        canvas = canvas,
        text =
          "عرض سعر رقم ${quotationWithItems.quotation.quotationNumber}  —  ${quotationWithItems.quotation.customerName}",
        x = contentLeft,
        y = y,
        width = contentWidth.toInt(),
        sizeSp = 11f,
        color = navyColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      y += 26f
    }

    // Products Table
    // Exact RTL Column X boundaries (left -> right in canvas coordinates):
    // [الإجمالي] [السعر] [الكمية] [اسم الصنف]
    val xTotalLeft = contentLeft
    val xPriceLeft = xTotalLeft + COL_TOTAL_W
    val xQtyLeft = xPriceLeft + COL_PRICE_W
    val xNameLeft = xQtyLeft + COL_QTY_W

    val tableHeaderHeight = 32f
    val tableHeaderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader =
          LinearGradient(
            contentLeft,
            y,
            contentRight,
            y,
            navyColor,
            royalBlueColor,
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRoundRect(
      RectF(contentLeft, y, contentRight, y + tableHeaderHeight),
      8f,
      8f,
      tableHeaderPaint,
    )

    val headerTextY = y + 8f
    drawRtlText(
      canvas, "اسم الصنف", xNameLeft + 8f, headerTextY, (COL_NAME_W - 16f).toInt(),
      10f, Color.WHITE, boldTypeface, Layout.Alignment.ALIGN_NORMAL,
    )
    drawRtlText(
      canvas, "الكمية", xQtyLeft + 4f, headerTextY, (COL_QTY_W - 8f).toInt(),
      10f, Color.WHITE, boldTypeface, Layout.Alignment.ALIGN_CENTER,
    )
    drawRtlText(
      canvas, "السعر", xPriceLeft + 4f, headerTextY, (COL_PRICE_W - 8f).toInt(),
      10f, Color.WHITE, boldTypeface, Layout.Alignment.ALIGN_CENTER,
    )
    drawRtlText(
      canvas, "الإجمالي", xTotalLeft + 6f, headerTextY, (COL_TOTAL_W - 12f).toInt(),
      10f, Color.WHITE, boldTypeface, Layout.Alignment.ALIGN_CENTER,
    )
    y += tableHeaderHeight

    val rowPaintEven = Paint().apply { color = Color.WHITE }
    val rowPaintOdd = Paint().apply { color = zebraBg }
    val rowBorderPaint =
      Paint().apply {
        color = borderColor
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
      }

    pageItems.forEachIndexed { idx, item ->
      val rowHeight = 30f
      val rowRect = RectF(contentLeft, y, contentRight, y + rowHeight)
      canvas.drawRect(rowRect, if (idx % 2 == 0) rowPaintEven else rowPaintOdd)
      canvas.drawLine(contentLeft, y + rowHeight, contentRight, y + rowHeight, rowBorderPaint)
      // Vertical column dividers
      canvas.drawLine(xPriceLeft, y, xPriceLeft, y + rowHeight, rowBorderPaint)
      canvas.drawLine(xQtyLeft, y, xQtyLeft, y + rowHeight, rowBorderPaint)
      canvas.drawLine(xNameLeft, y, xNameLeft, y + rowHeight, rowBorderPaint)

      val textY = y + 7f
      drawRtlText(
        canvas, item.productNameSnapshot, xNameLeft + 8f, textY, (COL_NAME_W - 16f).toInt(),
        9.5f, mainTextColor, regularTypeface, Layout.Alignment.ALIGN_NORMAL,
      )
      drawRtlText(
        canvas, item.quantity.toString(), xQtyLeft + 4f, textY, (COL_QTY_W - 8f).toInt(),
        9.5f, mainTextColor, regularTypeface, Layout.Alignment.ALIGN_CENTER,
      )
      drawRtlText(
        canvas, MoneyUtils.formatUsdString(item.unitPriceUsdSnapshot), xPriceLeft + 4f, textY,
        (COL_PRICE_W - 8f).toInt(), 9.5f, mainTextColor, regularTypeface, Layout.Alignment.ALIGN_CENTER,
      )
      drawRtlText(
        canvas, MoneyUtils.formatUsdString(item.lineTotalUsd), xTotalLeft + 6f, textY,
        (COL_TOTAL_W - 12f).toInt(), 9.5f, navyColor, boldTypeface, Layout.Alignment.ALIGN_CENTER,
      )
      y += rowHeight
    }

    // Bottom Summary on the last page
    if (pageIndex == totalPages - 1) {
      y += 14f
      val totalBd =
        quotationWithItems.quotation.totalAmountUsd.toBigDecimalOrNull() ?: BigDecimal.ZERO
      val totalBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightHeaderBg }
      val totalBorderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = royalBlueColor
          style = Paint.Style.STROKE
          strokeWidth = 1.2f
        }
      val totalRect = RectF(contentLeft, y, contentRight, y + 60f)
      canvas.drawRoundRect(totalRect, 10f, 10f, totalBoxPaint)
      canvas.drawRoundRect(totalRect, 10f, 10f, totalBorderPaint)

      drawRtlText(
        canvas = canvas,
        text = "الإجمالي الكلي:  ${MoneyUtils.formatUsd(totalBd)}",
        x = contentLeft + 14f,
        y = y + 9f,
        width = (contentWidth - 28f).toInt(),
        sizeSp = 13f,
        color = navyColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      drawRtlText(
        canvas = canvas,
        text = MoneyUtils.amountInArabicWords(totalBd),
        x = contentLeft + 14f,
        y = y + 34f,
        width = (contentWidth - 28f).toInt(),
        sizeSp = 9.5f,
        color = royalBlueColor,
        typeface = regularTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
      y += 72f

      if (quotationWithItems.quotation.notes.isNotBlank()) {
        drawRtlText(
          canvas = canvas,
          text = "ملاحظات إضافية: ${quotationWithItems.quotation.notes}",
          x = contentLeft,
          y = y,
          width = contentWidth.toInt(),
          sizeSp = 9.5f,
          color = mainTextColor,
          typeface = regularTypeface,
          alignment = Layout.Alignment.ALIGN_NORMAL,
        )
        y += 32f
      }

      // Signature & Approval area anchored near bottom
      val sigY = maxOf(y + 16f, A4_HEIGHT - 122f)
      val sigBoxLeft = contentLeft
      val sigBoxRight = contentLeft + 190f
      val sigPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = borderColor
          style = Paint.Style.STROKE
          strokeWidth = 1f
        }
      canvas.drawRoundRect(RectF(sigBoxLeft, sigY, sigBoxRight, sigY + 64f), 10f, 10f, sigPaint)
      drawRtlText(
        canvas = canvas,
        text = "الختم والاعتماد",
        x = sigBoxLeft + 8f,
        y = sigY + 8f,
        width = 174,
        sizeSp = 9.5f,
        color = mutedTextColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_CENTER,
      )

      drawRtlText(
        canvas = canvas,
        text = "مع خالص التقدير،\nإدارة المبيعات والمناقصات",
        x = contentRight - 220f,
        y = sigY + 8f,
        width = 220,
        sizeSp = 10f,
        color = navyColor,
        typeface = boldTypeface,
        alignment = Layout.Alignment.ALIGN_NORMAL,
      )
    }

    // Footer page number
    drawRtlText(
      canvas = canvas,
      text = "صفحة ${pageIndex + 1} من $totalPages",
      x = contentLeft,
      y = A4_HEIGHT - MARGIN_BOTTOM + 6f,
      width = contentWidth.toInt(),
      sizeSp = 8.5f,
      color = mutedTextColor,
      typeface = regularTypeface,
      alignment = Layout.Alignment.ALIGN_CENTER,
    )
  }

  private fun drawRtlText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    width: Int,
    sizeSp: Float,
    color: Int,
    typeface: Typeface,
    alignment: Layout.Alignment,
  ) {
    if (width <= 0) return
    val paint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = sizeSp
        this.typeface = typeface
      }
    val layout =
      StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setTextDirection(TextDirectionHeuristics.RTL)
        .setLineSpacing(0f, 1.15f)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
  }
}
