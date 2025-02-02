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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Base64


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newFixedThreadPool(3)
    val running = MutableLiveData<Boolean>()
    val connected = MutableLiveData<Boolean>()
    private val paired = MutableLiveData<Boolean>()
    private val needPair = MutableLiveData<Boolean>()
    private val pairPort = MutableLiveData<Int>()
    private val scripts = MutableLiveData<List<JSONObject>>()
    private val manager:AbsAdbConnectionManager = AdbConnectionManager(getApplication())

    init {
        val common = readCommonScript(application)
        val mjsScripts = readAllMjsFilesFromAssets(application)
        val scriptsList = mutableListOf<JSONObject>()
        Handler(Looper.getMainLooper()).postDelayed({
            val jsenv = (application as App).jsHelper!!.newJsEnv()
            for ((fileName, content) in mjsScripts) {
                val code =common.replace("export ","") + content.replace("export ","").replace(Regex("^import.*\\n"), "")

                val txt = application.jsHelper!!.executeJavaScript(jsenv,"$code\n JSON.stringify({name, description, pkg, icon:''})")
                Log.d("*** 脚本执行结果 ***", "$fileName:$txt")
                val obj = JSONObject(txt)
                obj.put("code", code)
                val pkg = obj.getString("pkg")
                val icon= getApplicationIcon(application, pkg)
                if (icon != null) {
                    obj.put("icon", drawableToBase64(icon))
                    scriptsList.add(obj)
                }
            }
            scripts.postValue(scriptsList)
            jsenv.close()
        }, 1000)
    }

    private fun readCommonScript(context: Context): String {
        val assetManager = context.assets
        var result = ""
        try {
            val files = assetManager.list("scripts/common") // List files in the "scripts" directory
            if (files != null) {
                for (file in files) {
                    if (file.endsWith(".mjs")) {
                        val filePath = "scripts/common/$file"
                        result += readAssetFile(context,filePath)+"\n"
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("readAllMjsFiles", "Error listing files in assets/scripts", e)
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
    private fun readAssetFile(context: Context, path: String): String {
        val assetManager = context.assets
        val inputStream = assetManager.open(path)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            stringBuilder.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()
        return stringBuilder.toString()
    }
    private fun readAllMjsFilesFromAssets(context: Context): Map<String, String> {
        val scripts = mutableMapOf<String, String>()
        val assetManager = context.assets
        try {
            val files = assetManager.list("scripts") // List files in the "scripts" directory
            if (files != null) {
                for (file in files) {
                    if (file.endsWith(".mjs")) {
                        val filePath = "scripts/$file"
                        scripts[file] = readAssetFile(context,filePath)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("readAllMjsFiles", "Error listing files in assets/scripts", e)
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