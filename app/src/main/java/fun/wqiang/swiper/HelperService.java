package fun.wqiang.swiper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

import com.shiqi.quickjs.JSContext;
import com.shiqi.quickjs.JSUndefined;

public class HelperService extends Service {
    private static final String TAG = "HelperService";
    public static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "my_service_channel";

    @Nullable
    private NotificationManager notificationManager;
    private AbsAdbConnectionManager manager;

    private JsHelper jsHelper = null;
    public static final String ACTION_STOP="ACTION-STOP";
    private CompletableFuture<String> mainFuture = null;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = AdbConnectionManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        jsHelper = ((App) getApplication()).getJsHelper();
        createNotification();
        new Thread(() -> {
            try {
                manager.autoConnect(HelperService.this,1000);
            } catch (Exception e) {
                Log.e(TAG, "Error auto connecting to ADB",e);
                try {
                    manager.connect(5555);
                } catch (Exception ex) {
                    Log.e(TAG, "Error connecting to 5555",ex);
                }
            }
        }).start();
        Log.d(TAG, "onCreate called");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            manager.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        if (Objects.equals(intent.getAction(), ACTION_STOP)) {
            if (mainFuture!=null) {
                mainFuture.complete("用户中止");
                mainFuture = null;
            }
            return START_NOT_STICKY;
        }
        String script = intent.getStringExtra("script");
        JSContext jsenv = jsHelper.newJsEnv(this);
        jsHelper.initGlobals(this, jsenv).thenAccept((a) -> {
            mainFuture = new CompletableFuture<>();
            jsenv.getGlobalObject().setProperty("finish", jsenv.createJSFunction((jsContext, jsValues) -> {
                if (jsValues[0] instanceof JSUndefined) {
                    mainFuture.complete("");
                } else {
                    mainFuture.complete(jsValues[0].toString());
                }
                return null;
            }));
            jsenv.evaluate(script + "\n module.go().catch(finish).then(closeTTS).then(()=>launchPackage('fun.wqiang.swiper'))", "main.js");
            mainFuture.thenAccept(reason->{
                Log.d(TAG, "运行终止: " + reason);
                jsenv.close();
            });
        });
        return START_NOT_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // 如果您的服务需要绑定到其他组件，请在此返回 IBinder 对象
        // 否则，返回 null
        return null;
    }

    private void createNotification() {
        // 创建通知渠道（Android 8.0 及以上版本需要）
        createNotificationChannel();

        Intent back = new Intent(this, MainActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent playIntent = PendingIntent.getActivity(this, 0, back, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // 创建通知构建器
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("滑动小助手正在运行中...")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(playIntent)
                .setAutoCancel(false)
                .build();

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "createNotification called");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "滑动通知",
                    NotificationManager.IMPORTANCE_HIGH
            );
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }
}

