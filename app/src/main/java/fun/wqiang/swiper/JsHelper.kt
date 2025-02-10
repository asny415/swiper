package `fun`.wqiang.swiper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.shiqi.quickjs.JSContext
import com.shiqi.quickjs.JSFunction
import com.shiqi.quickjs.JSNumber
import com.shiqi.quickjs.JSString
import com.shiqi.quickjs.JSValue
import com.shiqi.quickjs.QuickJS
import `fun`.wqiang.swiper.TTSHelper.Companion.initTTS
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices.SHELL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class JsHelper {
    private var seed: Int = 10000
    private var quickJS: QuickJS = QuickJS.Builder().build()
    val mainThreadHandler = Handler(Looper.getMainLooper())
    fun newJsEnv(context: Context) : JSContext {
        val runtime = quickJS.createJSRuntime()
        val jsenv = runtime.createJSContext()
        globalGetApplicationIcon(context, jsenv)
        globalGetScreenWidth(context,jsenv)
        globalGetScreenHeight(context,jsenv)
        return jsenv
    }

    private fun globalGetApplicationIcon(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("getApplicationIcon", jsenv.createJSFunction{ctx, args ->
            val pkg = args[0].cast(JSString::class.java).string
            val b64 = CommonUtils.fetchPkgIconB64(context, pkg)
            ctx.createJSString(b64)
        })
    }
    private fun globalSay(context: Context, jsenv: JSContext):CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val utterances = HashMap<String, CompletableFuture<Void>>()
        var tts:TextToSpeech? = null
        val pm = PrefereManager(context)
        jsenv.globalObject.setProperty(
            "closeTTS", jsenv.createJSFunction{ _, _ ->
                if (tts != null) {
                    TTSHelper.closeTTS(context, tts!!)
                }
                null
            }
        )
        fun say(text: String): CompletableFuture<Void>? {
            //用户设置不要说话
            if (!pm.readSettingSpeak()) {
                Log.d(TAG, "say:$text disabled")
                return CompletableFuture.completedFuture(null)
            }
            Log.d(TAG, "say:$text")
            val utteranceId = Date().toString()
            utterances[utteranceId] = CompletableFuture()
            if (tts != null) {
                val ttsresult = tts!!.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                Log.d(TAG, "tts result:$ttsresult for text:$text")
                if (ttsresult < 0) {
                    return CompletableFuture.completedFuture(null)
                }
            }
            return utterances[utteranceId]
        }

        initTTS(context) { s: String? ->
            val fu: CompletableFuture<Void>? = utterances[s]
            if (fu != null) {
                fu.complete(null)
                utterances.remove(s)
            }
        }.thenAccept{textToSpeech->
            tts = textToSpeech
            jsenv.globalObject.setProperty(
                "say",
                jsenv.createJSFunction { jsContext: JSContext?, jsValues: Array<JSValue> ->
                    val text = jsValues[0].cast(
                        JSString::class.java
                    ).string
                    Log.d(TAG, "say js:$text")
                    jsContext!!.createJSPromise { resolve: JSFunction, _: JSFunction? ->
                        val args = arrayOfNulls<JSValue>(0)
                        say(text)?.thenAccept {
                            resolve.invoke(
                                jsenv.createJSNull(),
                                args
                            )
                        }
                    }
                })
            future.complete(null)
        }
        return future
    }
    fun initGlobals(
        context: Context,
        jsenv: JSContext
    ) :CompletableFuture<Void> {
        return globalSay(context, jsenv).thenApply {
            globalDelay(jsenv)
            globalSetTimeout(context, jsenv)
            globalLog(jsenv)
            globalAdb(context, jsenv)
            globalLaunchPackage(jsenv)
            globalCheckRunning(jsenv)
            globalSwipeUp(jsenv)
            globalScreenOCR(jsenv)
            startPendingProcess(jsenv)
            null
        }
    }

    @SuppressLint("SdCardPath")
    private fun globalScreenOCR(jsenv: JSContext) {
        jsenv.globalObject.setProperty("ocr", jsenv.createJSFunction { _, _ ->
            jsenv.createJSPromise { resolve, _ ->
                val bitmap = BitmapFactory.decodeFile("/sdcard/swiper_screen_cap.png")
                val result = JSONObject()
                if (bitmap != null) {
                    val textRecognizer =
                        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    val image = InputImage.fromBitmap(bitmap, 0)
                    textRecognizer.process(image).addOnSuccessListener { text: Text ->
                        try {
                            result.put("width", image.width)
                            result.put("height", image.height)
                            val results = JSONArray()
                            result.put("result", "succ")
                            result.put("nodes", results)
                            val textBlocks = text.textBlocks
                            for (block in textBlocks) {
                                // 获取文本块中的每一行
                                val lines = block.lines
                                for (line in lines) {
                                    // 获取每一行的元素（单个字符）
                                    val elements = line.elements
                                    for (element in elements) {
                                        // 获取每个字符的边界框
                                        val elementBoundingBox = element.boundingBox
                                        if (elementBoundingBox != null) {
                                            val node = JSONObject()
                                            node.put("text", element.text)
                                            val bbox = JSONObject()
                                            bbox.put("x", elementBoundingBox.left)
                                            bbox.put("y", elementBoundingBox.top)
                                            bbox.put("width", elementBoundingBox.width())
                                            bbox.put("height", elementBoundingBox.height())
                                            node.put("boundingBox", bbox)
                                            results.put(node)
                                        }
                                    }
                                }
                            }
                        } catch (e: JSONException) {
                            throw RuntimeException(e)
                        }
                    }.addOnFailureListener { e: Exception ->
                            // 处理错误
                            Log.e("TEST", "识别失败", e)
                        }.addOnCompleteListener {
                        Log.e("TEST", "识别结束")
                            bitmap.recycle()
                            textRecognizer.close()
                        File("/sdcard/swiper_screen_cap.png").delete()
                            resolve.invoke(
                                jsenv.createJSNull(),
                                Array<JSValue>(1) { jsenv.createJSString(result.toString()) })
                        }
                } else {
                    try {
                        result.put("result", "无法加载图像")
                        resolve.invoke(
                            jsenv.createJSNull(),
                            Array<JSValue>(1) { jsenv.createJSObject(result) })
                    } catch (e: JSONException) {
                        Log.e(TAG, "无法加载图像", e)
                    }
                }
            }
        })
        jsenv.evaluate("""async function screenOCR(){
            |await adb("screencap -p /sdcard/swiper_screen_cap.png && echo \"\"")
            |return await ocr()
            |}""".trimMargin(), "ocr.js")
    }

    private fun globalSwipeUp(jsenv: JSContext) {
        val cmd = "`input swipe \${x1} \${y1} \${x2} \${y2} \${duration} && echo \"\"`"
        jsenv.evaluate("""
            async function swipeUp(options={}) {
                const width = getScreenWidth()
                const height = getScreenHeight()
                const {x1,x2,y1,y2,duration} = Object.assign({
                x1: Math.round(width / 2 + Math.random() * 30 - 15),
                  y1 : Math.round((height * 3) / 4 + Math.random() * 100 - 50),
                  x2: Math.round(width / 2 + Math.random() * 30 - 15),
                  y2: Math.round((height * 1) / 4 + Math.random() * 100 - 50),
                 duration: Math.round(Math.random() * 100 - 50 + 600)
                }, options)
                await adb($cmd)
            }
        """, "plugin.js")
    }

    private fun globalCheckRunning(jsenv: JSContext) {
        jsenv.evaluate("async function checkRunning() {const rsp = await adb(`dumpsys window | grep -E 'mCurrentFocus'`); return rsp.trim().split(` `)[2].split(`}`)[0].split(`/`) }", "plugin.js")
    }

    private fun startPendingProcess(jsenv: JSContext) {
        val pendingTimer = Timer()
        pendingTimer.schedule(object : TimerTask() {
            override fun run() {
                mainThreadHandler.post {
                    try {
                        jsenv.executePendingJob()
                    } catch (e: Exception) {
                        pendingTimer.cancel()
                    }
                }
            }
        }, 0L, 1000)
    }

    private fun globalLaunchPackage(jsenv: JSContext) {
        jsenv.evaluate("function launchPackage(pkg) {return adb(`monkey -p \${pkg} -c android.intent.category.LAUNCHER 1`)}", "other.js")
    }

    private fun globalAdb(context: Context, jsenv: JSContext) {
        val manager = AdbConnectionManager.getInstance(context.applicationContext)
        val executor = Executors.newFixedThreadPool(3)
        var shellStream: AdbStream? = null
        val promiseMap:MutableMap<String, List<JSFunction>> = mutableMapOf()
        jsenv.globalObject.setProperty("adb", jsenv.createJSFunction { ctx, args ->
            val cmd = args[0].cast(JSString::class.java).string
            val thisSeed = seed++
            jsenv.createJSPromise{ resolve, reject ->
                promiseMap["cmd_$thisSeed"] = listOf(resolve,reject)
                var runnable:Runnable? = null
                runnable = Runnable{
                    try {
                        if (shellStream == null || shellStream!!.isClosed) {
                            shellStream = manager.openStream(SHELL)
                            Thread {
                                try {
                                    val reader = shellStream!!.openInputStream().bufferedReader()
                                    var line: String? = reader.readLine()
                                    while (line != null) {
                                        Log.d(TAG, "adb shell output: $line")
                                        if (line.startsWith("cmd_")) {
                                            val result = line.split(":", limit = 2)
                                            mainThreadHandler.post {
                                                if (result[0] in promiseMap) {
                                                    promiseMap[result[0]]!![0].invoke(
                                                        jsenv.createJSNull(),
                                                        Array<JSValue>(1) {
                                                            ctx.createJSString(result[1])
                                                        })
                                                    promiseMap.remove(result[0])
                                                }
                                            }
                                        }
                                        line = reader.readLine()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error reading output", e)
                                }
                            }.start()
                        }
                    } catch (e:Exception) {
                        Log.e("ADB", "Error executing command", e)
                        if (e.message == "Not connected to ADB." || e.message == "connect() must be called first"){
                            var connected = false
                            try {
                                connected = manager.autoConnect(context.applicationContext, 5000)
                            } catch (e: AdbPairingRequiredException) {
                                Log.e(TAG, "Error executing command", e)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error connect", e)
                            }
                            if (!connected) {
                                Log.d(TAG, "try connect to 5555!")
                                connected = manager.connect(5555)
                            }
                            if (!connected) {
                                Log.e(TAG, "Error connect to ADB!")
                            } else {
                                Log.d(TAG, "connect to ADB success!")
                                executor.submit(runnable)
                                return@Runnable
                            }
                        }
                    }
                    val cmd2write = "$cmd | sed 's/^/cmd_$thisSeed:/'\n"
                    Log.d(TAG, "executing command: $cmd2write")
                    val os = shellStream!!.openOutputStream()
                    os.write(cmd2write.toByteArray(
                        StandardCharsets.UTF_8))
                    os.flush()
                    os.write("\n".toByteArray(
                        StandardCharsets.UTF_8))
                }
                executor.submit(runnable)
            }
        })
    }

    private fun globalLog(jsenv: JSContext) {
        jsenv.globalObject.setProperty("log", jsenv.createJSFunction{ ctx, args ->
            val log = args[0].cast(JSString::class.java).string
            Log.d("__JS_LOG__", log)
            ctx.createJSNull()
        })
        jsenv.evaluate("const console={log};", "console.js")
    }

    private fun globalSetTimeout(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("setTimeout", jsenv.createJSFunction{ ctx, args ->
            val cb = args[0].cast(JSFunction::class.java)
            val ms = args[1].cast(JSNumber::class.java).double.toLong()
            val t = Timer()
            t.schedule(object: TimerTask() {
                override fun run() {
                    mainThreadHandler.post {
                        try{
                            cb.invoke(jsenv.createJSNull(), Array<JSValue>(0) { ctx.createJSNull() })
                        } catch (e: Exception) {
                            Log.d(TAG, "Error executing callback", e)
                        }
                    }
                }
            }, ms)
            ctx.createJSNumber(CommonUtils.getScreenHeight(context))
        })
    }

    private fun globalDelay(jsenv: JSContext) {
        jsenv.evaluate("const delay=(ms)=>new Promise(r=>setTimeout(r, ms));","delay.js")
    }

    private fun globalGetScreenWidth(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("getScreenWidth", jsenv.createJSFunction{ ctx, _ ->
            ctx.createJSNumber(CommonUtils.getScreenWidth(context))
        })
    }
    private fun globalGetScreenHeight(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("getScreenHeight", jsenv.createJSFunction{ ctx, _ ->
            ctx.createJSNumber(CommonUtils.getScreenHeight(context))
        })
    }

    fun executeJavaScript(jsctx: JSContext, jsCode :String):String {
        try {
            return jsctx.evaluate(jsCode, "module.js", String::class.java).toString()
        } catch (e :Exception) {
            Log.e("JavaScriptEngine", "Failed to execute JavaScript code.", e)
        }
        return ""
    }

    fun getName(jsenv: JSContext, script: String): String {
        return executeJavaScript(jsenv, "$script\n module.name")
    }

}