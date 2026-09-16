package xyz.mpv.rex.ui.player.managers

import android.content.Context
import android.util.Log
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.preferences.CustomButton
import xyz.mpv.rex.ui.preferences.CustomButtonSlots
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages custom user-defined buttons, including Lua script generation and MPV integration.
 */
class CustomButtonManager(
    private val context: Context,
    private val playerPreferences: PlayerPreferences,
    private val advancedPreferences: AdvancedPreferences,
    private val json: Json,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CustomButtonManager"
        private const val CUSTOM_BUTTONS_LOADED_FLAG = "user-data/mpvex/custombuttons_loaded"
    }

    data class CustomButtonState(
        val id: String,
        val label: String,
        val isLeft: Boolean,
    )

    private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
    val customButtons = _customButtons.asStateFlow()

    private var setupJob: Job? = null
    private val loadMutex = Mutex()
    private var isMpvReady = false
    private var scriptPath: String? = null

    fun onMpvInitialized() {
        isMpvReady = true
        reloadScript("mpv_initialized")
    }

    fun setup() {
        setupJob?.cancel()
        setupJob = scope.launch(Dispatchers.IO) {
            try {
                val uiButtons = mutableListOf<CustomButtonState>()
                if (!advancedPreferences.enableLuaScripts.get()) {
                    _customButtons.value = uiButtons
                    scriptPath = null
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                    return@launch
                }

                val scriptContent = buildString {
                    var jsonString = playerPreferences.customButtons.get()
                    // 迁移：旧版 mp.command('cmd','arg') 双参数写法在 Lua 中不生效，
                    // 自动替换为 mp.commandv('cmd','arg') 并写回，用户无需手动重置
                    val migrated = migrateLegacyCommandSyntax(jsonString)
                    if (migrated != jsonString) {
                        jsonString = migrated
                        runCatching { playerPreferences.customButtons.set(migrated) }
                    }
                    if (jsonString.isNotBlank()) {
                        try {
                            // Try new slot-based format first
                            val slotsData = json.decodeFromString<CustomButtonSlots>(jsonString)
                            slotsData.slots.forEachIndexed { index, btn ->
                                if (btn != null && btn.enabled) {
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = index < 4
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, uiButtons)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback to old format
                            try {
                                val customButtonsList = json.decodeFromString<List<CustomButton>>(jsonString)
                                customButtonsList.forEachIndexed { index, btn ->
                                    val safeId = btn.id.replace("-", "_")
                                    val isLeft = index < 4
                                    processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, uiButtons)
                                }
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to parse custom buttons JSON", e2)
                            }
                        }
                    }

                    if (uiButtons.isNotEmpty()) {
                        append("mp.set_property_native('$CUSTOM_BUTTONS_LOADED_FLAG', '1')\n")
                    }
                }

                _customButtons.value = uiButtons

                if (scriptContent.isNotEmpty()) {
                    val scriptsDir = File(context.filesDir, "scripts")
                    if (!scriptsDir.exists()) scriptsDir.mkdirs()

                    val file = File(scriptsDir, "custombuttons.lua")
                    file.writeText(scriptContent)
                    scriptPath = file.absolutePath

                    if (isMpvReady) {
                        if (!loadScript(file)) {
                            Log.w(TAG, "Failed to load custombuttons.lua")
                        }
                    } else {
                        Log.d(TAG, "Deferring custombuttons.lua load until MPV is ready")
                    }
                } else {
                    scriptPath = null
                    runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up custom buttons", e)
            }
        }
    }

    private fun reloadScript(reason: String) {
        if (!isMpvReady) return

        scope.launch(Dispatchers.IO) {
            loadMutex.withLock {
                if (!advancedPreferences.enableLuaScripts.get()) return@withLock

                val currentPath = scriptPath ?: return@withLock
                if (isScriptLoaded()) return@withLock

                val file = File(currentPath)
                if (!file.exists()) {
                    Log.w(TAG, "custombuttons.lua missing during $reason, rebuilding")
                    setup()
                    return@withLock
                }

                if (!loadScript(file)) {
                    Log.w(TAG, "custombuttons.lua load failed during $reason")
                }
            }
        }
    }

    private fun isScriptLoaded(): Boolean =
        runCatching { MPVLib.getPropertyString(CUSTOM_BUTTONS_LOADED_FLAG) == "1" }
            .getOrDefault(false)

    private fun loadScript(file: File): Boolean {
        runCatching { MPVLib.setPropertyString(CUSTOM_BUTTONS_LOADED_FLAG, "0") }
        return runCatching {
            MPVLib.command("load-script", file.absolutePath)
            true
        }.getOrElse {
            Log.w(TAG, "load-script failed: ${it.message}")
            false
        }
    }

    fun callButton(id: String) {
        // 监测按钮：完全复用系统原生绑定(toggle/display-page-N) + 偏好状态跟踪，
        // 与 MoreSheet/MPVView 的系统面板切换逻辑一致
        when (id) {
            "stats-cycle" -> {
                // 循环切页：面板关则先打开，再切到下一页
                val cur = advancedPreferences.enabledStatisticsPage.get()
                val next = (cur + 1) % 6
                if (cur == 0) {
                    // 面板当前关闭 -> toggle 打开（系统 toggle 常驻模式）
                    MPVLib.command("script-binding", "stats/display-stats-toggle")
                }
                MPVLib.command("script-binding", "stats/display-page-$next")
                advancedPreferences.enabledStatisticsPage.set(next)
                return
            }
            "stats-close" -> {
                // 关闭监测：面板开着才 toggle 关闭
                if (advancedPreferences.enabledStatisticsPage.get() != 0) {
                    MPVLib.command("script-binding", "stats/display-stats-toggle")
                    advancedPreferences.enabledStatisticsPage.set(0)
                }
                return
            }
        }
        val safeId = id.replace("-", "_")
        MPVLib.command("script-message", "call_button_$safeId")
    }

    fun callButtonLongPress(id: String) {
        // 长按监测循环 = 同点按：循环切页
        if (id == "stats-cycle") {
            callButton(id)
            return
        }
        val safeId = id.replace("-", "_")
        MPVLib.command("script-message", "call_button_long_$safeId")
    }

    private fun StringBuilder.processButton(
        originalId: String,
        safeId: String,
        label: String,
        command: String,
        longPressCommand: String,
        onStartup: String,
        isLeft: Boolean,
        uiList: MutableList<CustomButtonState>
    ) {
        if (label.isNotBlank()) {
            uiList.add(CustomButtonState(originalId, label, isLeft))

            if (onStartup.isNotBlank()) {
                append(onStartup)
                append("\n")
            }

            if (command.isNotBlank()) {
                append(
                    """
                    function button_${safeId}()
                        ${command}
                    end
                    mp.register_script_message('call_button_${safeId}', button_${safeId})
                    """.trimIndent()
                )
                append("\n")
            }

            if (longPressCommand.isNotBlank()) {
                append(
                    """
                    function button_long_${safeId}()
                        ${longPressCommand}
                    end
                    mp.register_script_message('call_button_long_${safeId}', button_long_${safeId})
                    """.trimIndent()
                )
                append("\n")
            }
        }
    }

    /**
     * 迁移旧版按钮命令语法：将 `mp.command('cmd','arg')` 双参数写法
     * 替换为 `mp.commandv('cmd','arg')`（Lua 中双参数 mp.command 只取第一个参数作为命令名，导致不生效）。
     */
    private fun migrateLegacyCommandSyntax(jsonString: String): String {
        if (jsonString.isBlank()) return jsonString
        // 匹配 mp.command('xxx','yyy') 或 mp.command("xxx","yyy")
        val regex = Regex("""mp\.command\(('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")\s*,\s*('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")\)""")
        val migrated = regex.replace(jsonString) { match ->
            val arg1 = match.groupValues[1]
            val arg2 = match.groupValues[2]
            "mp.commandv($arg1, $arg2)"
        }
        return migrated
    }
}
