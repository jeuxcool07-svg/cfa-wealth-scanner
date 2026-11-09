package com.adtech.cfawealth

import android.graphics.Rect

data class ScanResult(
    val id: Int,
    val box: Rect,
    val fcfa: Long?,
    val progress: Int
)

data class ScanFrame(
    val imageWidth: Int,
    val imageHeight: Int,
    val people: List<ScanResult>
)
