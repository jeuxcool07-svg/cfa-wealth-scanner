package com.adtech.cfawealth

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class CameraAnalyzer(
    private val onResult: (style: Float, clean: Float, fcfa: Long) -> Unit
) : ImageAnalysis.Analyzer {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private var last = 0L

    @ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = proxy.image
        if (media == null || now - last < 700) {
            proxy.close()
            return
        }
        last = now

        val clean = cleanliness(proxy)
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val style = WealthScorer.styleScore(labels.map { it.text to it.confidence })
                onResult(style, clean, WealthScorer.toFcfa(style, clean))
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun cleanliness(proxy: ImageProxy): Float {
        val buf = proxy.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (i in bytes.indices step 50) {
            val v = bytes[i].toInt() and 0xFF
            sum += v
            sumSq += v * v
            n++
        }
        if (n == 0) return 0f
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        val bright = (mean / 255.0).toFloat()
        val smooth = (1f - (variance / 6000.0).toFloat()).coerceIn(0f, 1f)
        val result = bright * 0.6f + smooth * 0.4f
        return result.coerceIn(0f, 1f)
    }
}
