package `fun`.wqiang.swiper

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Suppress("DEPRECATION")
class ActivityUtils(private val activity: MainActivity, val viewModel: MainViewModel) {

    private val TAG: String = "ActivityUtils"

    fun handleReceivedFile(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> handleSingleFile(intent.data)
            Intent.ACTION_SEND -> handleSharedFile(intent)
        }
    }

    // 处理直接打开文件（ACTION_VIEW）
    private fun handleSingleFile(uri: Uri?) {
        uri ?: return
        activity.lifecycleScope.launch(Dispatchers.IO) {
            saveFileFromUri(uri)?.let { savedPath ->
                activity.runOnUiThread {
                    viewModel.refreshAllScripts()
                    Toast.makeText(activity,
                        "文件已保存至：$savedPath", Toast.LENGTH_LONG).show()

                }
            }
        }
    }

    // 处理分享文件（ACTION_SEND）
    private fun handleSharedFile(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
            handleSingleFile(uri)
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

    fun unlinkFile(path: String) {
        Log.d(TAG, "unlinkFile: $path")
        val f = File(activity.filesDir,"scripts"+ File.separator +path)
        f.delete()
        Handler().postDelayed( { viewModel.refreshAllScripts() }, 100)
    }
}