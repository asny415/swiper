package `fun`.wqiang.swiper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture

@Suppress("DEPRECATION")
class ActivityUtils(private val activity: MainActivity, private val viewModel: MainViewModel) {
    private var lastVM: GreetingDataModel? = null
    var tts: TextToSpeech? = null
    private  val tAG = "ActivityUtils"
    private val pm = PrefereManager(activity)
    init {
        TTSHelper.initTTS(activity) {}.thenAccept {
            tts=it
        }
    }

    fun handleReceivedFile(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> handleSingleFile(intent.data, false)
            Intent.ACTION_SEND -> handleSharedFile(intent)
        }
    }

    // 处理直接打开文件（ACTION_VIEW）
    private fun handleSingleFile(uri: Uri?, run: Boolean){
        uri ?: return
        if (pm.readSettingAllowImport()) {
            lastVM!!.importDialogResult.value = CompletableFuture()
            lastVM!!.importDialogResult.value.thenAccept{allow->
                Log.d(TAG, "import dialog result:$allow")
                Handler().postDelayed({
                    if (allow) {
                        val savePath = saveFileFromUri(uri)
                        viewModel.refreshAllScripts()
                        if (savePath != null && run) {
                            val code = File(savePath).readText(Charsets.UTF_8)
                            runScript(code)
                        }
                    }
                }, 3000)
            }
            lastVM!!.showImportDialog.value = true
        } else {
            //onResume 会停止服务运行
            Handler().postDelayed({
                val savePath = saveFileFromUri(uri)
                if (savePath!=null && run) {
                    val code = File(savePath).readText(Charsets.UTF_8)
                    runScript(code)
                }
            }, 2000)
        }
    }

    // 处理分享文件（ACTION_SEND）
    private fun handleSharedFile(intent: Intent) {
        val script = intent.getStringExtra("script")
        if (script?.isNotEmpty() == true) {
            Log.d(TAG, "script is:$script")
            handleSingleFile(Uri.parse(script), intent.getStringExtra("run") == "1")
            return
        }
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
            handleSingleFile(uri, false)
        }
    }

    // 核心保存方法
    private fun saveFileFromUri(uri: Uri): String? {
        return try {
            // 创建目标目录
            val targetDir = File(activity.filesDir, "scripts").apply { mkdirs() }

            // 获取原始文件名
            val fileName = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}.mjs"

            // 读取并保存文件
            activity.contentResolver.openInputStream(uri)?.use { input ->
                File(targetDir, fileName).outputStream().use { output ->
                    input.copyTo(output)
                }
                return File(targetDir, fileName).absolutePath
            }
            null
        } catch (e: Exception) {
            Log.e("FileReceiver", "文件保存失败", e)
            null
        }
    }

    // 安全获取文件名（适配不同来源）
    @SuppressLint("Range")
    private fun getFileNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        )
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }?.replace(Regex("[^a-zA-Z0-9._-]"), "_") // 清理非法字符
    }

    private fun unlinkFile(path: String) {
        Log.d(tAG, "unlinkFile: $path")
        val f = File(activity.filesDir,"scripts"+ File.separator +path)
        f.delete()
        Handler().postDelayed( { viewModel.refreshAllScripts() }, 100)
    }

    @Composable
    fun getGreetingDataModel(): GreetingDataModel {
        var connected by remember { mutableStateOf(true) }
        var running by remember { mutableStateOf(false) }
        var pairPort by remember { mutableStateOf("") }
        var scripts by remember { mutableStateOf(listOf<JSONObject>()) }
        viewModel.connected.observe(activity) {
            Log.d("TEST", "connected: $it")
            connected = it
        }
        viewModel.running.observe(activity) {
            running = it
        }
        viewModel.watchPairingPort().observe(activity) { port ->
            pairPort = if (port != -1) "$port" else ""
        }
        viewModel.watchScripts().observe(activity) { scripts = it }
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val volumn = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        lastVM =  GreetingDataModel(connected, pairPort,
        currentVolumn = volumn,
        maxVolumn = maxVolume,
        showImportDialog = remember { mutableStateOf(false) },
        importDialogResult = remember { mutableStateOf(CompletableFuture()) },
        scripts = scripts, selectedPage = remember { mutableStateOf("Home") } ,
        onDeletedItem = { item ->
            unlinkFile(item.getString("filename"))
        },
        readSettingSpeak =  { pm.readSettingSpeak() },
        saveSettingSpeak = {value: Boolean -> pm.saveSettingSpeak(value) },
        readSettingAllowImport = { pm.readSettingAllowImport() },
            saveSettingAllowImport = {value: Boolean -> pm.saveSettingAllowImport(value) },
        setVolumn = {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, it, 0)
            tts!!.speak("测试", TextToSpeech.QUEUE_ADD, null, null)
            pm.saveVolumn(it)
        },
        onClickItem = { item ->
            viewModel.disconnect()
            runScript(item.getString("code"))
        },
        onShowDialog = {
            viewModel.getPairingPort()
        }, onPair = { port, pairCode ->
            viewModel.pair(port, pairCode)
        })
        return lastVM!!
    }

    private fun runScript(code: String) {
        val intent = Intent(activity, HelperService::class.java)
        intent.putExtra("script", code)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }
}