package com.example.tyranornext.scanner

import android.content.Context
import android.content.SharedPreferences
import com.core.engine.EnginePrefs
import com.core.nativeplugin.NativePluginConstants
import com.core.nativeplugin.NativePluginManager
import java.io.File

/**
 * 直接集成（非模块化）：把随 APK 打包在 assets 的引擎原生插件，
 * 首次启动时自动"安装"到 app 私有插件目录，并标记为已安装+已启用。
 *
 * 引擎加载器（NativeLibraryLoader/OnsLibLoader/Artemis 相关）从
 * filesDir/engine_plugins/<engine>/current/arm64-v8a/ 读取 .so；
 * 此处复制打包为 assets 的 <engine> 目录到该目录，无需用户手动导入 zip。
 */
object EnginePluginBootstrap {

    private const val TAG = "EnginePluginBootstrap"
    // sourceSets.assets.srcDir 取目录内容为 assets 根，故捆绑资源直接位于 <engine>/ 下。

    private class EngineSpec(
        val engineId: String,
        val installedKey: String,
        val enabledKey: String,
    )

    private val engines = listOf(
        EngineSpec(
            NativePluginConstants.ENGINE_KIRIKIROID2,
            EnginePrefs.KEY_NATIVE_PLUGIN_KIRIKIROID2_INSTALLED,
            EnginePrefs.KEY_NATIVE_PLUGIN_KIRIKIROID2_ENABLED,
        ),
        EngineSpec(
            NativePluginConstants.ENGINE_ONS,
            EnginePrefs.KEY_NATIVE_PLUGIN_ONS_INSTALLED,
            EnginePrefs.KEY_NATIVE_PLUGIN_ONS_ENABLED,
        ),
        EngineSpec(
            NativePluginConstants.ENGINE_ARTEMIS,
            EnginePrefs.KEY_NATIVE_PLUGIN_ARTEMIS_INSTALLED,
            EnginePrefs.KEY_NATIVE_PLUGIN_ARTEMIS_ENABLED,
        ),
    )

    /** 幂等：仅对尚未安装的引擎执行一次复制。每次应用启动调用开销极低。 */
    @JvmStatic
    fun provisionIfNeeded(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(EnginePrefs.APP_PREFS, Context.MODE_PRIVATE)
        for (spec in engines) {
            if (prefs.getBoolean(spec.installedKey, false)) continue
            try {
                copyAssetDir(app, spec.engineId, currentDirFor(app, spec.engineId))
                markInstalled(prefs, spec)
                android.util.Log.i(TAG, "provisioned native plugin: ${spec.engineId}")
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "provision ${spec.engineId} failed", t)
            }
        }
    }

    private fun markInstalled(prefs: SharedPreferences, spec: EngineSpec) {
        prefs.edit()
            .putBoolean(spec.installedKey, true)
            .putBoolean(spec.enabledKey, true)
            .apply()
    }

    private fun currentDirFor(app: Context, engineId: String): File = when (engineId) {
        NativePluginConstants.ENGINE_KIRIKIROID2 -> NativePluginManager.kirikiroid2CurrentDir(app)
        NativePluginConstants.ENGINE_ONS -> NativePluginManager.onsCurrentDir(app)
        NativePluginConstants.ENGINE_ARTEMIS -> NativePluginManager.artemisCurrentDir(app)
        else -> error("unknown engine: $engineId")
    }

    private fun copyAssetDir(context: Context, assetDir: String, destDir: File) {
        val children = context.assets.list(assetDir) ?: return
        for (child in children) {
            val assetPath = "$assetDir/$child"
            val isDir = context.assets.list(assetPath)?.isNotEmpty() == true
            if (isDir) {
                copyAssetDir(context, assetPath, File(destDir, child))
            } else {
                val out = File(destDir, child)
                out.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}