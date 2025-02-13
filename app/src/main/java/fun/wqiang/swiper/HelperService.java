package fun.wqiang.swiper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class HelperService extends Service {
    private static final String TAG = "HelperService";
    public static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "my_service_channel";

    @Nullable
    private NotificationManager notificationManager;
    private AbsAdbConnectionManager manager;

    public static final String ACTION_STOP="ACTION-STOP";
    private JsRuntime js= null;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = AdbConnectionManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
        if (Objects.equals(intent.getAction(), ACTION_STOP)) {
            if (js != null) {
                Log.d(TAG,"手动停止服务");
                js.shutdown("用户停止");
            }else {
                Log.d(TAG, "没有什么服务好停止");
            }
            return START_NOT_STICKY;
        }
        String script = intent.getStringExtra("script");
        if (script!=null) {
            if (js != null) {
                Log.d(TAG, "有老服务正在运行，等待停止");
                js.wait().thenAccept(reason->
                        new Handler().postDelayed(() -> startScript(script),1000));
                js.shutdown("用户终止");
            } else {
                Log.d(TAG, "没有老服务正在运行，直接启动新服务");
                startScript(script);
            }
        }
        return START_NOT_STICKY;
    }

    private void startScript(String script) {
        Log.d(TAG, "启动前台服务，展示图标");
        createNotification();
        Log.d(TAG, "创建运行实例");
        js = new JsRuntime(this);
        js.wait().thenAccept(r->{
            Log.d(TAG, "服务运行结束，关闭前台服务图标");
            stopForeground(true);
            Log.d(TAG, "清理运行实例");
            js = null;
        });
        js.go(script);
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

