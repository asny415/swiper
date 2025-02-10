package `fun`.wqiang.swiper

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream

class MainViewModel(val app: App) : AndroidViewModel(app) {
    private val executor = Executors.newFixedThreadPool(3)
    val running = MutableLiveData<Boolean>()
    val connected = MutableLiveData<Boolean>()
    private val paired = MutableLiveData<Boolean>()
    private val pairPort = MutableLiveData<Int>()
    private val scripts = MutableLiveData<List<JSONObject>>()
    private val manager:AbsAdbConnectionManager = AdbConnectionManager(getApplication())
    private val defaultIcon = CommonUtils.fetchPkgIconB64(app,"fun.wqiang.swiper")

    init {
        Handler(Looper.getMainLooper()).postDelayed({
            refreshAllScripts()
        }, 1000)
    }

    fun refreshAllScripts() {
        ensureScriptsDirectory(app)
        val mjsScripts = readAllScripts(app)
        val scriptsList = mutableListOf<JSONObject>()
        for ((fileName, code) in mjsScripts) {
            val jsenv = app.jsHelper!!.newJsEnv(app)
            try {
                val obj = JSONObject(
                    app.jsHelper!!.executeJavaScript(
                        jsenv,
                        "$code\n JSON.stringify(Object.assign({name:'', icon:'', description:'no description'},module))"
                    )
                )
                obj.put("code", code)
                obj.put("filename", fileName)
                if (obj.getString("icon") == "") {
                    obj.put("icon", defaultIcon)
                }
                scriptsList.add(obj)
            }catch (e: Exception) {
                Log.d(TAG, "exception when read script: $fileName, delete it")
                File(app.filesDir, "scripts" + File.separator + fileName).delete()
                e.printStackTrace()
            } finally {
                jsenv.close()
            }
        }
        scripts.postValue(scriptsList)    }

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
            } catch (th: Throwable) {
                th.printStackTrace()
                paired.postValue(false)
            }
        }
    }

    fun disconnect() {
        manager.disconnect()
    }
}