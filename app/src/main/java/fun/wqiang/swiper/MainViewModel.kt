package `fun`.wqiang.swiper

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newFixedThreadPool(3)
    val running = MutableLiveData<Boolean>()
    val connected = MutableLiveData<Boolean>()
    private val paired = MutableLiveData<Boolean>()
    private val needPair = MutableLiveData<Boolean>()
    private val pairPort = MutableLiveData<Int>()

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
        val manager:AbsAdbConnectionManager = AdbConnectionManager(getApplication())
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

    fun checkServiceRunning() {
        val activityManager = getApplication<App>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses
        for (processInfo in processes) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE &&
                processInfo.processName == "fun.wqiang.swiper") {
                running.postValue(true)
                return
            }
        }
        running.postValue(false)
    }

    fun autoConnect() {
        executor.submit {
            autoConnectInternal()
        }
    }
}