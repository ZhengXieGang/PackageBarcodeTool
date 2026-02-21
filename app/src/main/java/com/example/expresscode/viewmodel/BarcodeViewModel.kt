package com.example.expresscode.viewmodel

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class BarcodeViewModel : ViewModel() {

    val barcodeList = mutableStateListOf<String>()

    val isCarouselMode = mutableStateOf(false)

    /** 轮播速度，单位毫秒，范围 500 ~ 2000 */
    val carouselSpeed = mutableFloatStateOf(1000f)

    val isCarouselPaused = mutableStateOf(false)

    val currentCarouselIndex = mutableStateOf(0)

    /** 扫描暂停状态 */
    val isScanningPaused = mutableStateOf(false)


    enum class CarouselMode {
        InApp,
        Suspended
    }

    val carouselDisplayMode = mutableStateOf(CarouselMode.InApp)

    fun loadCarouselMode(context: android.content.Context) {
        val modeInt = com.example.expresscode.util.PreferenceUtils.getCarouselMode(context)
        carouselDisplayMode.value = if (modeInt == com.example.expresscode.util.PreferenceUtils.MODE_SUSPENDED) 
            CarouselMode.Suspended else CarouselMode.InApp
    }

    fun loadCarouselSpeed(context: android.content.Context) {
        carouselSpeed.floatValue = com.example.expresscode.util.PreferenceUtils.getCarouselSpeed(context)
    }

    fun setCarouselMode(context: android.content.Context, mode: CarouselMode) {
        carouselDisplayMode.value = mode
        val modeInt = if (mode == CarouselMode.Suspended) 
            com.example.expresscode.util.PreferenceUtils.MODE_SUSPENDED 
        else com.example.expresscode.util.PreferenceUtils.MODE_IN_APP
        com.example.expresscode.util.PreferenceUtils.saveCarouselMode(context, modeInt)
    }



    /**
     * 添加条码，自动去重。
     * @return true 表示新增成功，false 表示已存在被忽略。
     */
    fun addBarcode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return false
        if (barcodeList.contains(trimmed)) return false
        barcodeList.add(trimmed)
        return true
    }

    fun removeBarcode(code: String) {
        barcodeList.remove(code)
        if (barcodeList.isNotEmpty()) {
            currentCarouselIndex.value = currentCarouselIndex.value.coerceIn(0, barcodeList.size - 1)
        } else {
            currentCarouselIndex.value = 0
        }
    }

    fun toggleCarouselMode(initialPaused: Boolean = false) {
        if (barcodeList.isNotEmpty()) {
            isCarouselMode.value = !isCarouselMode.value
            if (isCarouselMode.value) {
                isCarouselPaused.value = initialPaused
                currentCarouselIndex.value = 0
            }
        }
    }

    fun togglePause() {
        isCarouselPaused.value = !isCarouselPaused.value
    }

    fun toggleScanningPause() {
        isScanningPaused.value = !isScanningPaused.value
    }

    fun nextBarcode() {
        if (barcodeList.isNotEmpty()) {
            currentCarouselIndex.value = (currentCarouselIndex.value + 1) % barcodeList.size
        }
    }
}
