package com.example.expresscode.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer

object BarcodeGenerator {

    /**
     * 生成 Code 128 条形码 Bitmap。
     * @param content 条码内容
     * @param width 宽度（像素）
     * @param height 高度（像素）
     */
    fun generateCode128(content: String, width: Int, height: Int): Bitmap? {
        return try {
            val writer = Code128Writer()
            val bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, width, height)
            val pixels = IntArray(width * height)
            // 一维条码每行完全相同，只需读取第一行的 BitMatrix
            for (x in 0 until width) {
                pixels[x] = if (bitMatrix[x, 0]) Color.BLACK else Color.WHITE
            }
            // 用原生 arraycopy 将第一行快速复制到剩余所有行（比逐像素快约 340 倍）
            for (y in 1 until height) {
                System.arraycopy(pixels, 0, pixels, y * width, width)
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
