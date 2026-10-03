package com.aslamshoh.glazaai.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Шесть режимов распознавания из плитки на главном экране — те же, что в web- и iOS-версиях
 * (см. GlazaAI-iOS/GlazaAI/Views/RecognitionMode.swift). «Документы» честно использует тот же
 * общий OCR, что и «Текст» — backend не делает отдельного разбора полей паспорта/ID.
 */
enum class RecognitionMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    CURRENCY("Валюта", "Банкноты мира", Icons.Filled.AttachMoney),
    TEXT("Текст", "Печать и рукопись", Icons.Filled.TextFields),
    OBJECTS("Предметы", "Объекты вокруг", Icons.Filled.Category),
    DOCUMENTS("Документы", "Текст на документе (общий OCR)", Icons.Filled.Description),
    BARCODE("Штрих-коды", "Товары и цены", Icons.Filled.LocalOffer),
    QR("QR-коды", "Ссылки и данные", Icons.Filled.QrCodeScanner);

    companion object {
        /** Режимы с одиночным снимком — используют CaptureScreen. Barcode/QR — BarcodeScreen. */
        val singleShotModes = listOf(CURRENCY, TEXT, OBJECTS, DOCUMENTS)
    }
}
