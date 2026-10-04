package org.cortex.terminal.runtime

import android.content.Context
import android.os.Build

object CortexRuntime {
    val is64Bit: Boolean
        get() = try {
            android.os.Process.is64Bit()
        } catch (e: Throwable) {
            Build.SUPPORTED_ABIS.firstOrNull()?.contains("64") ?: true
        }

    val architecture: String
        get() = if (is64Bit) {
            Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: "arm64-v8a"
        } else {
            Build.SUPPORTED_32_BIT_ABIS.firstOrNull() ?: "armeabi-v7a"
        }

    fun initialize(context: Context) {
        BootstrapManager.initializeFileSystem(context)
    }
}
