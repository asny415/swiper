package `fun`.wqiang.swiper

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class MainViewModel(val app: Application) : AndroidViewModel(app) {
    private val executor = Executors.newFixedThreadPool(3)
    val running = MutableLiveData<Boolean>()
    val connected = MutableLiveData<Boolean>()
    private val paired = MutableLiveData<Boolean>()
    private val needPair = MutableLiveData<Boolean>()
    private val pairPort = MutableLiveData<Int>()
    private val scripts = MutableLiveData<List<JSONObject>>()
    private val manager:AbsAdbConnectionManager = AdbConnectionManager(getApplication())

    init {
        Handler(Looper.getMainLooper()).postDelayed({
            refreshAllScripts()
        }, 1000)
    }

    fun refreshAllScripts() {
        ensureScriptsDirectory(app)
        val common = readCommonScript(app)
        val mjsScripts = readAllScripts(app)
        val scriptsList = mutableListOf<JSONObject>()
        for ((fileName, content) in mjsScripts) {
            val jsenv = (app as App).jsHelper!!.newJsEnv()
            val code =common.replace("export ","") + content.replace("export ","").replace(Regex("^import.*\\n"), "")

            val txt = app.jsHelper!!.executeJavaScript(jsenv,"$code\n JSON.stringify({name, description, pkg, icon:''})")
            Log.d("*** 脚本执行结果 ***", "$fileName:$txt")
            val obj = JSONObject(txt)
            obj.put("code", code)
            obj.put("filename", fileName)
            val pkg = obj.getString("pkg")
            val icon= getApplicationIcon(app, pkg)
            if (icon != null) {
                obj.put("icon", drawableToBase64(icon))
                scriptsList.add(obj)
            }
            jsenv.close()
        }
        scripts.postValue(scriptsList)    }

    private fun readCommonScript(context: Context): String {
        val files = listMjsFiles(context, "scripts/common")
        var result = ""
        for (file in files) {
            val filePath = "scripts/common/${file.name}"
            result += readTextFile(context,filePath)+"\n"
        }
        return result
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        // Check if the drawable has valid dimensions
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            // Create a single-color bitmap of 1x1 pixel if dimensions are invalid
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        // Create a new bitmap with the same dimensions as the drawable
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        // Create a canvas to draw on the bitmap
        val canvas = android.graphics.Canvas(bitmap)

        // Set the bounds of the drawable to match the canvas
        drawable.setBounds(0, 0, canvas.width, canvas.height)

        // Draw the drawable onto the canvas
        drawable.draw(canvas)

        return bitmap
    }
    private fun drawableToBase64(drawable: Drawable): String? {
        val bitmap = drawableToBitmap(drawable)
        val byteArrayOutputStream =
            ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream) // You can change the format and quality here
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray,
            Base64.DEFAULT)
    }
    private fun getApplicationIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("getApplicationIcon", "Package not found: $packageName", e)
            null
        }
    }

    private fun readTextFile(context: Context, filePath: String): String? {
        return try {
            File(context.filesDir, filePath).takeIf { it.exists() }?.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun ensureScriptsDirectory(context: Context) {
        val targetDir = File(context.filesDir, "scripts")

        // 如果目录不存在则创建
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            copyAssetsRecursively(context.assets, "scripts", targetDir)
        }
    }

    private fun copyAssetsRecursively(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        try {
            // 获取 assets 中的文件列表
            val files = assetManager.list(assetPath)
            if (files.isNullOrEmpty()) {
                // 如果是文件则直接复制
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(File(targetDir, assetPath.substringAfterLast('/'))).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // 如果是目录则递归处理
                for (file in files) {
                    val srcPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
                    val destFile = File(targetDir, file)

                    if (assetManager.list(srcPath)?.isNotEmpty() == true) {
                        // 创建子目录并递归复制
                        destFile.mkdirs()
                        copyAssetsRecursively(assetManager, srcPath, destFile)
                    } else {
                        // 复制文件
                        assetManager.open(srcPath).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun listMjsFiles(context: Context, path: String): List<File> {
        val targetDir = File(context.filesDir, path)

        return when {
            // 检查目录是否存在
            !targetDir.exists() -> {
                println("[警告] 目录不存在: ${targetDir.absolutePath}")
                emptyList()
            }
            // 验证是否为目录
            !targetDir.isDirectory -> {
                println("[错误] 路径不是目录: ${targetDir.absolutePath}")
                emptyList()
            }
            // 执行文件过滤
            else -> {
                targetDir.listFiles { _, name ->
                    name?.endsWith(".mjs", ignoreCase = true) == true
                }?.toList() ?: emptyList()
            }
        }.filter { it.isFile } // 二次验证确保是文件
    }

    private fun readAllScripts(context: Context): Map<String, String> {
        val scripts = mutableMapOf<String, String>()
        val files = listMjsFiles(context, "scripts")
        for (file in files) {
            scripts[file.name] = readTextFile(context, "scripts/" + file.name)!!
        }
        return scripts
    }

    fun watchScripts(): LiveData<List<JSONObject>> {
        return scripts
    }

    fun watchPairingPort(): LiveData<Int> {
        return pairPort
    }

    fun getPairingPort() {
        executor.submit {
            val atomicPort = AtomicInteger(-1)
            val resolveHostAndPort = CountDownLatch(1)

            val adbMdns = AdbMdns(
                getApplication(), AdbMdns.SERVICE_TYPE_TLS_PAIRING
            ) { _: InetAddress?, port: Int ->
                atomicPort.set(port)
                resolveHostAndPort.countDown()
            }
            adbMdns.start()

            try {
                if (!resolveHostAndPort.await(1, TimeUnit.MINUTES)) {
                    return@submit
                }
            } catch (ignore: InterruptedException) {
            } finally {
                adbMdns.stop()
            }
            pairPort.postValue(atomicPort.get())
        }
    }

    fun pair(port: Int, pairingCode: String?) {
        executor.submit {
            try {
                val pairingStatus: Boolean
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val manager = AdbConnectionManager.getInstance(getApplication())
                    pairingStatus = manager.pair(
                        AndroidUtils.getHostIpAddress(getApplication()), port,
                        pairingCode!!
                    )
                } else pairingStatus = false
                paired.postValue(pairingStatus)
                autoConnectInternal()
            } catch (th: Throwable) {
                th.printStackTrace()
                paired.postValue(false)
            }
        }
    }

    private fun autoConnectInternal(){
        var conn = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                conn = manager.autoConnect(getApplication(), 5000)
            } catch (e: AdbPairingRequiredException) {
                needPair.postValue(true)
                return
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        try {
            if (!conn) {
                conn = manager.connect(5555)
            }
        } catch (th:Throwable) {
            th.printStackTrace()
        }
        if (conn) {
            connected.postValue(true)
        }
    }

    fun autoConnect() {
        executor.submit {
            autoConnectInternal()
        }
    }

    fun startPackage(context: Context, pkg: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }

    fun disconnect() {
        manager.disconnect()
    }
}