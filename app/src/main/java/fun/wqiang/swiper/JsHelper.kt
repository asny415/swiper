package `fun`.wqiang.swiper

import android.util.Log
import com.shiqi.quickjs.JSContext
import com.shiqi.quickjs.QuickJS


class JsHelper {
    var quickJS: QuickJS = QuickJS.Builder().build()
    fun newJsEnv(): JSContext {
        val runtime = quickJS.createJSRuntime()
        return runtime.createJSContext()
    }
    fun executeJavaScript(jsctx: JSContext, jsCode :String):String {
        try {
            return jsctx.evaluate(jsCode, "module.js", String::class.java).toString()
        } catch (e :Exception) {
            Log.e("JavaScriptEngine", "Failed to execute JavaScript code.", e)
        }
        return ""
    }

}