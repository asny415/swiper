package `fun`.wqiang.swiper

import android.util.Log
import androidx.javascriptengine.JavaScriptSandbox
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import android.content.Context
import androidx.javascriptengine.JavaScriptIsolate
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

class JsHelper( context:Context) {
    var jsSandbox :JavaScriptSandbox? = null;
    init{
        val jsSandboxFuture = JavaScriptSandbox.createConnectedInstanceAsync(context);
        Futures.addCallback(jsSandboxFuture, object : FutureCallback<JavaScriptSandbox> {
            override fun onSuccess(sandbox: JavaScriptSandbox?) {
                // JavaScriptSandbox 创建成功
                // 你可以在这里使用 sandbox 对象执行 JavaScript 代码
                if (sandbox != null) {
                    jsSandbox = sandbox;
                    Log.d("JavaScriptEngine", "JavaScriptSandbox created successfully.")
                }
            }

            override fun onFailure(t: Throwable) {
                Log.e("JavaScriptEngine", "Failed to create JavaScriptSandbox.", t)
            }
        }, Executor { it.run() }) // 使用一个简单的 Executor
    }
    public fun executeJavaScript(jsCode :String):String {
//        val jsCode = "function sum(a, b) { let r = a + b; return r.toString(); }; sum(3, 4)"
        val jsIsolate: JavaScriptIsolate? = jsSandbox?.createIsolate()
        val jsResultFuture: ListenableFuture<String>? = jsIsolate?.evaluateJavaScriptAsync(jsCode)

        if (jsResultFuture != null) {
            try {
                return jsResultFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e :Exception) {
                Log.e("JavaScriptEngine", "Failed to execute JavaScript code.", e)
            }
        }
        return "";
    }

    fun close() {
        jsSandbox?.close()
    }
}