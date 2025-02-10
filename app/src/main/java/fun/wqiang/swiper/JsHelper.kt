package `fun`.wqiang.swiper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.shiqi.quickjs.JSContext
import com.shiqi.quickjs.JSFunction
import com.shiqi.quickjs.JSNumber
import com.shiqi.quickjs.JSString
import com.shiqi.quickjs.JSValue
import com.shiqi.quickjs.QuickJS
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices.SHELL
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

class JsHelper {
    private var seed: Int = 10000
    private var quickJS: QuickJS = QuickJS.Builder().build()
    val mainThreadHandler = Handler(Looper.getMainLooper())
    fun newJsEnv(context: Context): JSContext {
        val runtime = quickJS.createJSRuntime()
        val ctx = runtime.createJSContext()
        initGlobalValue(context, ctx)
        return ctx
    }

    private fun globalGetApplicationIcon(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("getApplicationIcon", jsenv.createJSFunction{ctx, args ->
            val pkg = args[0].cast(JSString::class.java).string;
            val b64 = CommonUtils.fetchPkgIconB64(context, pkg)
            ctx.createJSString(b64);
        })
    }
    private fun initGlobalValue(
        context: Context,
        jsenv: JSContext
    ) {
        globalGetApplicationIcon(context, jsenv)
        globalGetScreenWidth(context,jsenv)
        globalGetScreenHeight(context,jsenv)
        globalDelay(context, jsenv)
        globalSetTimeout(context, jsenv)
        globalLog(context, jsenv)
        globalAdb(context, jsenv)
        globalLaunchPackage(context, jsenv)
        globalCheckRunning(context, jsenv)
        globalSwipeUp(context, jsenv)
        globalScreenOCR(context, jsenv)
        startPendingProcess(context,jsenv)
    }

    private fun globalScreenOCR(context: Context, jsenv: JSContext) {
        //TODO
    }

    private fun globalSwipeUp(context: Context, jsenv: JSContext) {
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

    private fun globalCheckRunning(context: Context, jsenv: JSContext) {
        jsenv.evaluate("async function checkRunning(pkg) {const rsp = await adb(`dumpsys window | grep -E 'mCurrentFocus'`); return rsp.trim().split(` `)[2].split(`}`)[0].split(`/`) }", "plugin.js")
    }

    private fun startPendingProcess(context: Context, jsenv: JSContext) {
        val pendingTimer = Timer()
        pendingTimer.schedule(object : TimerTask() {
            override fun run() {
                mainThreadHandler.post({
                    try {
                        jsenv.executePendingJob()
                    }catch (e:Exception) {
                        pendingTimer.cancel()
                    }
                })
            }
        }, 0L, 1000)
    }

    private fun globalLaunchPackage(context: Context, jsenv: JSContext) {
        jsenv.evaluate("function launchPackage(pkg) {return adb(`monkey -p \${pkg} -c android.intent.category.LAUNCHER 1`)}", "other.js")
    }

    private fun globalAdb(context: Context, jsenv: JSContext) {
        val manager = AdbConnectionManager.getInstance(context.applicationContext)
        val executor = Executors.newFixedThreadPool(3)
        var shellStream: AdbStream? = null
        val promiseMap:MutableMap<String, List<JSFunction>> = mutableMapOf();
        jsenv.globalObject.setProperty("adb", jsenv.createJSFunction { ctx, args ->
            val cmd = args[0].cast(JSString::class.java).string;
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
                                            mainThreadHandler.post({
                                                if (result[0] in promiseMap) {
                                                    promiseMap[result[0]]!![0].invoke(jsenv.createJSNull(), Array<JSValue>(1) {
                                                        ctx.createJSString(result[1])
                                                    })
                                                    promiseMap.remove(result[0])
                                                }
                                            })
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

    private fun globalLog(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("log", jsenv.createJSFunction{ ctx, args ->
            val log = args[0].cast(JSString::class.java).string;
            Log.d(TAG, "Log from JS: $log")
            ctx.createJSNull()
        })
        jsenv.evaluate("const console={log};", "console.js")
    }

    private fun globalSetTimeout(context: Context, jsenv: JSContext) {
        jsenv.globalObject.setProperty("setTimeout", jsenv.createJSFunction{ ctx, args ->
            val cb = args[0].cast(JSFunction::class.java);
            val ms = args[1].cast(JSNumber::class.java).long;
            val t = Timer()
            t.schedule(object: TimerTask() {
                override fun run() {
                    mainThreadHandler.post {
                        cb.invoke(jsenv.createJSNull(), Array<JSValue>(0, { ctx.createJSNull() }))
                    }
                }
            }, ms)
            ctx.createJSNumber(CommonUtils.getScreenHeight(context))
        })
    }

    private fun globalDelay(context: Context, jsenv: JSContext) {
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