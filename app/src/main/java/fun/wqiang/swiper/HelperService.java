package fun.wqiang.swiper;

import static io.github.muntashirakon.adb.LocalServices.SHELL;

import android.annotation.SuppressLint;
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
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.shiqi.quickjs.JSContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

enum Event {
    Start,
    Sleep,
    CaptureScreen, WaitPackage, ConnectADB
}

public class HelperService extends Service {
    private static final String TAG = "HelperService";
    public static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "my_service_channel";
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private TextToSpeech tts;

    @Nullable
    private AdbStream adbShellStream;
    private final MutableLiveData<CharSequence> base64png = new MutableLiveData<>();
    private final MutableLiveData<CharSequence> currentPkg = new MutableLiveData<>();
    private NotificationManager notificationManager;
    private AbsAdbConnectionManager manager;
    private PrefereManager pm = null;
    private ScheduledFuture<?> pkgCheckTimer, screenCaptureTimer;
    private final Runnable outputGenerator = () -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(adbShellStream.openInputStream()))) {
            String s;
            while ((s = reader.readLine()) != null) {
                if (s.length() != 76) {
                    Log.d(TAG, "outputGenerator: " + s);
                }
                if (s.startsWith("package:")) {
                    String pkgline = s.substring(8).trim();
                    String[] pieces = pkgline.split(" ");
                    String packageName ="unknown";
                    if (pieces.length == 3) {
                        packageName = pieces[2].split("/")[0];
                    }
                    Log.d(TAG, "find package name:" + packageName);
                    currentPkg.postValue(packageName);
                }
                if (s.startsWith("screen:")) {
                    base64png.postValue(new Date().toString());
                }

            }
        } catch (IOException e) {
            Log.e("TEST", "Error reading output", e);
        }
    };

    private String script="";
    private JsHelper jsHelper = null;
    private JSONObject ctx = new JSONObject();
    private String targetpkg = "";
    @org.jetbrains.annotations.Nullable
    public static final String ACTION_STOP="ACTION-STOP";
    private int unknown = 0;
    private JSContext jsenv;
    private final HashMap<String, CompletableFuture<Void>> utterances = new HashMap<>();

    public void execute(String command) {
        Log.d("ADB", "execute command: " + command);
        executor.submit(() -> {
            try {
                if (adbShellStream == null || adbShellStream.isClosed()) {
                    adbShellStream = manager.openStream(SHELL);
                    Log.d(TAG,"new output generator thread started");
                    new Thread(outputGenerator).start();
                }
                try (OutputStream os = adbShellStream.openOutputStream()) {
                    os.write(String.format("%1$s\n", command).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                if (Objects.equals(e.getMessage(), "Not connected to ADB.") || Objects.equals(e.getMessage(), "connect() must be called first")) {
                    Log.e("TEST", "Error ADB", e);
                    goEvent(Event.ConnectADB, new JSONObject());
                } else {
                    Log.e("TEST", "Error executing command", e);
                }
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        manager = AdbConnectionManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        jsHelper = ((App) getApplication()).getJsHelper();
        pm = new PrefereManager(this);
        Log.d(TAG, "onCreate called");
        TTSHelper.initTTS(this, s -> {
            CompletableFuture<Void> future = utterances.get(s);
            if (future != null) {
                future.complete(null);
                utterances.remove(s);
            }
            return null;
        }).thenAccept(textToSpeech -> tts=textToSpeech);
    }

    private CompletableFuture<Void> say(String text) {
        //用户设置不要说话
        if (!pm.readSettingSpeak()) return CompletableFuture.completedFuture(null);
        Log.d(TAG, "say:" + text);
        String utteranceId = new Date().toString();
        utterances.put(utteranceId, new CompletableFuture<>());
        if (tts!=null) {
            int ttsresult = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            Log.d(TAG, "tts result:" + ttsresult+ " for text:" + text);
            if (ttsresult<0) {
                return CompletableFuture.completedFuture(null);
            }
        }
        return utterances.get(utteranceId);
    }

    private void goEvent(Event event, JSONObject params) {
        Log.d("TEST", "goEvent: " + event.toString() + " " + params.toString());

        if (pkgCheckTimer != null) {
            // 有时候因为不预期的逻辑错误，可能导致状态机被多次启动，这种单例检查可以在多个点阻断状态机的多次启动，避免造成重复执行
            pkgCheckTimer.cancel(true);
            pkgCheckTimer = null;
        }
        if (screenCaptureTimer != null) {
            screenCaptureTimer.cancel(true);
            screenCaptureTimer = null;
        }

        switch (event) {
            case Sleep:
                goEvtSleep(params);
                break;
            case Start:
                goEvtStart();
                break;
            case ConnectADB:
                goEvtConnect2ADB();
                break;
            case WaitPackage:
                goEvtSleep(null);
                break;
            case CaptureScreen:
                executeScreenCapture();
                break;
        }
    }

    private void goEvtStart() {
        //如果没有定义Script，什么事情也不做，这样的话执行就中断了
        if (!script.isEmpty()) {
            executePackageCheck();
        }
    }

    private void executePackageCheck() {
        pkgCheckTimer = scheduler.schedule(() -> {
            say("包名检查超时").thenAccept(aVoid -> goEvent(Event.Start, new JSONObject()));
        },10, TimeUnit.SECONDS);
        execute("dumpsys window | grep -E 'mCurrentFocus' | sed 's/^/package:/'");
    }

    private final Observer<CharSequence> packageCheckObserver = output -> {
        Log.d(TAG, "packageCheckObserver:" + output.toString());
        String pkg = output.toString();
        if (targetpkg.isEmpty() || pkg.equals(targetpkg)) {
            goEvent(Event.CaptureScreen, new JSONObject());
        } else {
            Log.d(TAG, "wrong package: " + pkg + " " + targetpkg);
            goEvent(Event.WaitPackage, new JSONObject());
        }
    };
    private final Observer<CharSequence> screenCaptureObserver = output -> processScreenCapture(output.toString());
    @SuppressLint("SdCardPath")
    public static Bitmap decodeBase64ToBitmap(String base64String) {
        Log.d(TAG, "decodeBase64ToBitmap, base64String length: " + base64String.length());
        try {
            return BitmapFactory.decodeFile("/sdcard/swiper_screen_cap.png");
        } catch (IllegalArgumentException e) {
            // Handle the case where the Base64 string is invalid
            System.err.println("Invalid Base64 string: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // Handle other potential exceptions
            System.err.println("Error decoding Base64 to Bitmap: " + e.getMessage());
            return null;
        }
    }
    private void processScreenCapture(String base64) {
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        img2text(bitmap).thenAccept(jsonObject -> {
           Log.d("TEST", "GOT JSON:" + jsonObject.toString());
            try {
                String result = jsonObject.getString("result");
                if (!result.equals("succ")) {
                    Log.e("TEST", "识别失败");
                    goEvent(Event.Start, new JSONObject());
                    return;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            try {
                ctx.put("width", jsonObject.getInt("width"));
                ctx.put("height", jsonObject.getInt("height"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            JSONArray nodes;
            try {
                nodes = jsonObject.getJSONArray("nodes");
            } catch (JSONException e) {
                Log.e("TEST", "Error getting nodes:", e);
                throw new RuntimeException(e);
            }
            String runs  =  jsHelper.executeJavaScript(jsenv, "JSON.stringify(logic("+ctx.toString()+","+nodes+"))");
            Log.d("TEST", "logic result:"+runs+" ctx:`" + ctx.toString()+"`");
            if (runs.isEmpty()) {
                unknown++;
                if (unknown >= 3) {
                    Log.d(TAG, "未定义界面超过3次，退出");
                    say("未定义界面").thenAccept(aVoid -> new Handler().postDelayed(this::stopSelf, 3000));
                } else {
                    Log.d(TAG, "第" + unknown + "次未定义界面，等待 ...");
                    new Handler().postDelayed(()-> goEvent(Event.Start, new JSONObject()), 2000);
                }
                return;
            }
            unknown = 0;
            try {
                runLogic(new JSONObject(runs));
            } catch (JSONException e) {
                Log.e(TAG, "Error running logic:", e);
                throw new RuntimeException(e);
            }
        });
    }

    private void runLogic(JSONObject obj) {
        try {
            JSONArray opts = obj.getJSONArray("opts");
            for (int i = 0; i < opts.length(); i++) {
                JSONObject opt = opts.getJSONObject(i);
                String reason = "";
                try {
                    reason = opt.getString("reason");
                } catch (JSONException ignored) {
                }
                JSONObject params;
                try {
                    params = opt.getJSONObject("params");
                } catch (JSONException e) {
                    params=new JSONObject();
                }
                Objects.requireNonNull(runOpt(opt.getString("opt"), reason, params)).get();
            }
            goEvent(Event.Start, new JSONObject());
        } catch (JSONException | ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error running logic:", e);
            throw new RuntimeException(e);
        }
        obj.remove("opts");
        obj.remove("result");
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();
            try {
                ctx.put(key, obj.get(key));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private CompletableFuture<Void> runOpt(String opt, String reason, JSONObject params) {
        Log.d("TEST", "runOpt: " + opt + " " + reason + " " + params.toString());
        switch (opt) {
            case "sleep":
                try {
                    long ms = params.getLong("ms");
                    return CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(ms);
                            Log.d("TEST", "sleep finish");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            case "click":
                try {
                    int x = (int) params.getDouble("x");
                    int y = (int) params.getDouble("y");
                    execute("input tap " + x + " " + y);
                    return CompletableFuture.completedFuture(null);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            case "back":
                execute("input keyevent 4");
                return CompletableFuture.completedFuture(null);
            case "swipe":
                try {
                    int x1 = (int) params.getDouble("x1");
                    int y1 = (int) params.getDouble("y1");
                    int x2 = (int) params.getDouble("x2");
                    int y2 = (int) params.getDouble("y2");
                    int duration = (int) params.getDouble("duration");
                    execute("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration);
                    return CompletableFuture.completedFuture(null);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            case "say":
                return say(reason);
            case "finish":
                safeQuit(true);
                return CompletableFuture.completedFuture(null);
        }
        return null;
    }

    public void deleteFileIfExists() {
        // 构建文件路径
        @SuppressLint("SdCardPath") String filePath = "/sdcard/swiper_screen_cap.png";
        File file = new File(filePath);
        // 检查文件是否存在并删除
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG,"文件删除成功");
            } else {
                Log.d(TAG,"文件删除失败");
            }
        } else {
            Log.d(TAG,"文件不存在");
        }
    }

    private void executeScreenCapture() {
        screenCaptureTimer = scheduler.schedule(() -> {
            say("截图超时").thenAccept(aVoid -> goEvent(Event.Start, new JSONObject()));
        },10, TimeUnit.SECONDS);
        //如果 /sdcard/swiper_screen_cap.png 存在则删除
        deleteFileIfExists();
        execute("screencap -p /sdcard/swiper_screen_cap.png; echo \"screen:\"");
    }

    private void goEvtSleep(@Nullable JSONObject params) {
        long delay=1000;
        try {
            if (params != null) {
                delay = params.getLong("ms");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error getting delay:", e);
        }
        scheduler.schedule(()-> goEvent(Event.Start, new JSONObject()), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        base64png.removeObserver(screenCaptureObserver);
        currentPkg.removeObserver(packageCheckObserver);
        Log.d(TAG, "unobserve base64 and packname");
        clearAllNotifications();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (jsenv != null) {
            jsenv.close();
        }
        try {
            manager.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void goEvtConnect2ADB() {
        executor.submit(() -> {
            AbsAdbConnectionManager manager;
            try {
                manager = AdbConnectionManager.getInstance(getApplication());
                boolean connected = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.d(TAG, "Version R connect with dynamic port!");
                    try {
                        connected = manager.autoConnect(getApplication(), 5000);
                    } catch (AdbPairingRequiredException e) {
                        return;
                    } catch (Throwable th) {
                       Log.e("TEST", "Error connecting to ADB", th);
                    }
                }
                if (!connected) {
                    Log.d(TAG, "try connect to 5555!");
                    connected = manager.connect(5555);
                }
                Log.d(TAG, "connect result is:" + connected);
                if (!connected) {
                    say("无法连接到ADB").thenAccept(aVoid -> {
                        try {
                            goEvent(Event.Sleep, new JSONObject().put("ms", 2000));
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    goEvent(Event.Sleep, new JSONObject().put("ms", 2000));
                }
            } catch (Exception e) {
                Log.d("TEST", "Error connecting to ADB", e);
                say("连接到ADB异常").thenAccept(aVoid -> {
                    try {
                        goEvent(Event.Sleep, new JSONObject().put("ms", 5000));
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }

        });
    }

    private void clearAllNotifications() {
        notificationManager.cancelAll();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        if (Objects.equals(intent.getAction(), ACTION_STOP)) {
            safeQuit(false);
            return START_NOT_STICKY;
        }
        base64png.observeForever(screenCaptureObserver);
        currentPkg.observeForever(packageCheckObserver);
        Log.d(TAG, "observe base64 and packname");
        createNotification();
        script = intent.getStringExtra("script");
        if (jsenv != null) {
            jsenv.close();
        }
        jsenv = jsHelper.newJsEnv();
        initRuntime();
        goEvent(Event.Start, new JSONObject());
        return START_NOT_STICKY;
    }

    private void safeQuit(boolean succ) {
        if (succ && !script.isEmpty()) {
            say("任务完成").thenAccept(aVoid -> stopSelf());
        } else if (!script.isEmpty()){
            say("任务中止").thenAccept(aVoid -> stopSelf());
        }
        script = "";
    }

    private void initRuntime() {
        ctx=new JSONObject();
        targetpkg =  jsHelper.executeJavaScript(jsenv,script + "\n" + "pkg");
        Log.d("TEST", "GOT PACKAGE on service: " + targetpkg);
    }

    private CompletableFuture<JSONObject> img2text(Bitmap bitmap) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        TextRecognizer textRecognizer =
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        JSONObject result = new JSONObject();
        if (bitmap != null) {
            // 创建 InputImage 对象
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            // 识别文本
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        try {
                            result.put("width", image.getWidth());
                            result.put("height", image.getHeight());
                            JSONArray results = new JSONArray();
                            result.put("result", "succ");
                            result.put("nodes", results);
                            List<Text.TextBlock> textBlocks = text.getTextBlocks();
                            for (Text.TextBlock block : textBlocks) {
                                // 获取文本块中的每一行
                                List<Text.Line> lines = block.getLines();
                                for (Text.Line line : lines) {
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
            try {
                result.put("result", "无法加载图像");
                future.complete(result);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            Log.e("TEST", "无法加载图像");
        }
        return future;
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
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "滑动通知",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }
    }
}

