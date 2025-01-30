package fun.wqiang.swiper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;

public class HelperService extends Service {
    private static final String TAG = "HelperService";
    public static boolean running = false;
    private static final String CHANNEL_ID = "my_service_channel";
    public static final int NOTIFICATION_ID = 1;
    private static final String ACTION_SMART = "ACTION_SMART";
    private static final String ACTION_STOP = "ACTION_STOP";
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFuture;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private AdbStream adbShellStream;
    private volatile boolean clearEnabled;
    private final MutableLiveData<CharSequence> commandOutput = new MutableLiveData<>();
    private NotificationManager notificationManager;
    private final Runnable outputGenerator = () -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(adbShellStream.openInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                if (clearEnabled) {
                    sb.delete(0, sb.length());
                    clearEnabled = false;
                }
                sb.append(s).append("\n");
                commandOutput.postValue(sb);
            }
        } catch (IOException e) {
            Log.e("TEST", "Error reading output", e);
        }
    };
    private Thread runningThread;

    public void execute(String command) {
        executor.submit(() -> {
            try {
                if (adbShellStream == null || adbShellStream.isClosed()) {
                    AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplication());
                    adbShellStream = manager.openStream(LocalServices.SHELL);
                    new Thread(outputGenerator).start();
                }
                if (command.equals("clear")) {
                    clearEnabled = true;
                }
                try (OutputStream os = adbShellStream.openOutputStream()) {
                    os.write(String.format("%1$s\n", command).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                Log.e("TEST", "Error executing command", e);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void taskSingleRound() {
        Log.d("TEST", "taskSingleRound");
        long delay = (long) (3000 + Math.random() * 7000);
        showToast(String.format(Locale.CHINESE, "将于 %d 秒后再次滑动", (int) (delay / 1000)));
        scheduledFuture = scheduler.schedule(() -> {
            execute("clear");
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            double width = displayMetrics.widthPixels;
            double height = displayMetrics.heightPixels;
            int start_x = (int) (Math.random() * 50 + width / 2 - 25);
            int start_y = (int) (Math.random() * height / 4 + height / 2);
            int end_x = (int) (Math.random() * 50 + width / 2 - 25);
            int end_y = (int) (Math.random() * height / 4 + height / 8);
            int duration = (int) (200 + Math.random() * 200);
            execute(String.format(Locale.CHINESE, "input swipe %d %d %d %d %d", start_x, start_y, end_x, end_y, duration));
            taskSingleRound();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void connect2adb() {
        executor.submit(() -> {
            AbsAdbConnectionManager manager;
            try {
                manager = AdbConnectionManager.getInstance(getApplication());
                boolean connected = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        connected = manager.autoConnect(getApplication(), 5000);
                    } catch (AdbPairingRequiredException e) {
                        return;
                    } catch (Throwable th) {
                       Log.e("TEST", "Error connecting to ADB", th);
                    }
                }
                if (!connected) {
                    connected = manager.connect(5555);
                }
                if (!connected) {
                    showToast("无法连接到ADB");
                }
            } catch (Exception e) {
                showToast("连接到ADB异常");
                throw new RuntimeException(e);
            }

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("TEST", String.format("************ start command with action: %s %b", intent.getAction(), scheduledFuture == null));
        String code = intent.getStringExtra("script");
        JsHelper jsHelper = ((App) getApplication()).getJsHelper();
        if (jsHelper != null) {
           String pkg =  jsHelper.executeJavaScript(code + "\n" + "pkg");
           Log.d("TEST", "GOT PACKAGE on service: " + pkg);
        }

        img2text().thenAccept(jsonObject -> {
            Log.d("OCR", jsonObject.toString());
        });

        if (runningThread != null) {
            runningThread.interrupt();
            runningThread = new Thread(new MainRunning(this, code));
        }

        return START_NOT_STICKY;
    }

    private Bitmap loadImageFromAssets(String fileName) {
        try {
            InputStream inputStream = getAssets().open(fileName);
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private CompletableFuture<JSONObject> img2text() {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        TextRecognizer textRecognizer =
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        Bitmap bitmap = loadImageFromAssets("screen.png");
        if (bitmap != null) {
            // 创建 InputImage 对象
            JSONObject result = new JSONObject();
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            // 识别文本
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        // 处理识别结果
                        String resultText = text.getText();
                        Log.d("TEST", "识别结果：" + resultText);
                        try {
                            result.put("width", image.getWidth());
                            result.put("height", image.getHeight());
                            JSONArray results = new JSONArray();
                            result.put("results", results);
                            List<Text.TextBlock> textBlocks = text.getTextBlocks();
                            for (Text.TextBlock block : textBlocks) {
                                // 获取文本块的边界框
                                Rect boundingBox = block.getBoundingBox();
                                if (boundingBox != null) {
                                    Log.d(TAG, "Text Block bounding box: " + boundingBox.toString());
                                }

                                // 获取文本块中的每一行
                                List<Text.Line> lines = block.getLines();
                                for (Text.Line line : lines) {
                                    // 获取每一行的边界框
                                    Rect lineBoundingBox = line.getBoundingBox();
                                    if (lineBoundingBox != null) {
                                        Log.d(TAG, "Line bounding box: " + line.getText() + " " + lineBoundingBox.toString());
                                    }

                                    // 获取每一行的元素（单个字符）
                                    List<Text.Element> elements = line.getElements();
                                    for (Text.Element element : elements) {
                                        // 获取每个字符的边界框
                                        Rect elementBoundingBox = element.getBoundingBox();
                                        if (elementBoundingBox != null) {
                                            JSONObject node = new JSONObject();
                                            node.put("text", element.getText());
                                            JSONObject bbox = new JSONObject();
                                            bbox.put("x", elementBoundingBox.left);
                                            bbox.put("y", elementBoundingBox.top);
                                            bbox.put("width", elementBoundingBox.width());
                                            bbox.put("height", elementBoundingBox.height());
                                            node.put("boundingBox", bbox);
                                            results.put(node);
                                            Log.d(TAG, "Element bounding box: " + element.getText() + " " + elementBoundingBox.toString());
                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // 处理错误
                        Log.e("TEST", "识别失败", e);
                    }).addOnCompleteListener(task -> {
                        Log.e("TEST", "识别结束");
                        bitmap.recycle();
                        textRecognizer.close();
                        future.complete(result);
                    });
        } else {
            Log.e("TEST", "无法加载图像");
        }
        return future;
    }

    private void clearSingleTask() {
        Log.d("TEST", "clearSingleTask");
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        scheduledFuture = scheduler.schedule(() -> {
            showToast("停止滑动");
            scheduledFuture = null;
        }, 3000, TimeUnit.MILLISECONDS);
    }


    @Override
    public IBinder onBind(Intent intent) {
        // 如果您的服务需要绑定到其他组件，请在此返回 IBinder 对象
        // 否则，返回 null
        return null;
    }

    private void showToast(final String message) {
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 在这里处理服务的销毁逻辑，例如停止线程
        // ...
        clearSingleTask();
        // 移除通知
        stopForeground(true);
        running = false;
    }

    private void createNotification() {
        // 创建通知渠道（Android 8.0 及以上版本需要）
        createNotificationChannel();

        Intent play = new Intent(this, HelperService.class);
        play.setAction(HelperService.ACTION_SMART);
        PendingIntent playIntent = PendingIntent.getService(this, 0, play, PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, HelperService.class);
        stop.setAction(HelperService.ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE);

        // 创建通知构建器
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("点我切换运行状态")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(playIntent)
                .setOngoing(true) // 设置通知为常驻
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_delete, "停止服务", stopIntent)
                .build();

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification);
        running = true;
    }

    private void createNotificationChannel() {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "滑动通知",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
    }
}

