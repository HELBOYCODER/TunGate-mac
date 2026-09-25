package com.tungate

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import javax.imageio.ImageIO

object QrDecode {

    fun fromImage(file: File): String? = runCatching {
        val img = ImageIO.read(file) ?: return null
        val pixels = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, pixels, 0, img.width)
        val source = RGBLuminanceSource(img.width, img.height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().decode(bitmap).text
    }.getOrNull()
}
