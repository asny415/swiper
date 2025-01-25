package `fun`.wqiang.swiper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    var jsSupported: Boolean = false
    private val executor = Executors.newFixedThreadPool(3)
    val running = MutableLiveData<Boolean>()
    val connected = MutableLiveData<Boolean>()
    private val paired = MutableLiveData<Boolean>()
    private val needPair = MutableLiveData<Boolean>()
    private val pairPort = MutableLiveData<Int>()
    private val CHANNEL_ID: String = "my_service_channel"
    private val NOTIFICATION_ID: Int = 1


    private fun createActivityIntent(context: Context): Intent {
        // 创建一个 Intent，用于启动目标 Activity
        val intent = Intent(context, MainActivity::class.java)
        // 可以添加一些额外的参数
        intent.putExtra("key", "value")
        // 设置启动模式
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return intent
    }

    private fun createActivityPendingIntent(context: Context): PendingIntent {
        val intent = createActivityIntent(context)
        // 创建一个 PendingIntent，用于启动 Activity
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return pendingIntent
    }

    fun showNotification(context: Context) {
        val pendingIntent = createActivityPendingIntent(context)

        // 创建一个通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("点我快速返回")
            .setContentIntent(pendingIntent) // 设置 PendingIntent
            .setAutoCancel(true) // 点击后自动取消

            val channel = NotificationChannel(
                CHANNEL_ID,
                "滑动通知",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)


        notificationManager.notify(NOTIFICATION_ID, builder.build())
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

    fun autoConnect() {
        executor.submit {
            autoConnectInternal()
        }
    }

    fun checkNotifyReady() {
            val notificationManager: NotificationManagerCompat =
                NotificationManagerCompat.from(getApplication())
            val activeNotifications: MutableList<StatusBarNotification> =
                notificationManager.activeNotifications

            val notificationId = HelperService.NOTIFICATION_ID

            var isNotificationVisible = false
        for (n in  activeNotifications) {
            if (n.id == notificationId) {
                isNotificationVisible = true
                break
            }
        }
    running.postValue(isNotificationVisible)
    }
}