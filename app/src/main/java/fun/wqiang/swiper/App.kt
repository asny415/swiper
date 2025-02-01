package `fun`.wqiang.swiper

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import io.github.muntashirakon.adb.PRNGFixes
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App  : Application() {
    var jsHelper: JsHelper? = null;

    override fun onCreate() {
        super.onCreate()
        PRNGFixes.apply()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        jsHelper = JsHelper(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        jsHelper?.close()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }
}