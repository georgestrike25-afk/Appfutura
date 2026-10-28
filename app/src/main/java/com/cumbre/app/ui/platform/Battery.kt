package com.cumbre.app.ui.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** ¿Android ya excluye a Cumbre de la optimización de batería? */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(packageName)
}

/**
 * Pide al sistema que no restrinja la batería de Cumbre. Si el teléfono no ofrece el diálogo
 * directo, abre la lista de optimización de batería o, en último caso, los ajustes de la app.
 */
@SuppressLint("BatteryLife")
fun Context.requestIgnoreBatteryOptimizations() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(direct) }.isSuccess) return
    if (runCatching { startActivity(list) }.isSuccess) return
    openAppSettings()
}

/** Consejos por fabricante: estas capas propias cierran apps aunque Android no lo haga. */
data class OemBatteryTip(val brand: String, val steps: String)

fun oemBatteryTip(): OemBatteryTip? {
    val m = (Build.MANUFACTURER ?: "").lowercase()
    return when {
        "xiaomi" in m || "redmi" in m || "poco" in m -> OemBatteryTip(
            "Xiaomi / Redmi / POCO",
            "Ajustes › Apps › Cumbre › Ahorro de batería › «Sin restricciones», y activá «Inicio automático». En recientes, bloqueá Cumbre con el candado.",
        )
        "samsung" in m -> OemBatteryTip(
            "Samsung",
            "Ajustes › Aplicaciones › Cumbre › Batería › «Sin restricciones». Revisá que Cumbre no esté en «Aplicaciones en suspensión».",
        )
        "huawei" in m || "honor" in m -> OemBatteryTip(
            "Huawei / Honor",
            "Ajustes › Batería › Inicio de aplicaciones › Cumbre › gestionar manualmente y activar las tres opciones.",
        )
        "oppo" in m || "realme" in m || "oneplus" in m -> OemBatteryTip(
            "OPPO / realme / OnePlus",
            "Ajustes › Batería › Cumbre › «Permitir actividad en segundo plano» y desactivá la optimización.",
        )
        "vivo" in m || "iqoo" in m -> OemBatteryTip(
            "vivo",
            "Ajustes › Batería › Consumo en segundo plano › Cumbre › «Permitir alto consumo en segundo plano».",
        )
        "motorola" in m || "lenovo" in m -> OemBatteryTip(
            "Motorola",
            "Ajustes › Apps › Cumbre › Batería › «Sin restricciones».",
        )
        else -> null
    }
}
