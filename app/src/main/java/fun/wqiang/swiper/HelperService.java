package fun.wqiang.swiper;

import static io.github.muntashirakon.adb.LocalServices.SHELL;

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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private TextToSpeech tts;
    private AudioManager audioManager;
    private JavaScriptIsolate jsenv=null;


    @Nullable
    private AdbStream adbShellStream;
    private final MutableLiveData<CharSequence> base64png = new MutableLiveData<>();
    private final MutableLiveData<CharSequence> currentPkg = new MutableLiveData<>();
    private NotificationManager notificationManager;
    private final Runnable outputGenerator = () -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(adbShellStream.openInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String s;
            boolean inbase64 = false;
            while ((s = reader.readLine()) != null) {
                if (s.length() != 76) {
                    Log.d(TAG, "outputGenerator: " + s);
                }
                if (s.startsWith("package:")) {
                    Log.d(TAG, "find package name:" + s);
                    String packageName = s.substring(8);
                    currentPkg.postValue(packageName.trim());
                }
                if (Objects.equals(s, ">>>")) {
                    inbase64 = true;
                } else if (Objects.equals(s, "<<<")) {
                    inbase64 = false;
                    base64png.postValue(sb.toString());
                    sb.setLength(0);
                } else if (inbase64) {
                    sb.append(s).append("\n");
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

    public void execute(String command) {
        Log.d("ADB", "execute command: " + command);
        executor.submit(() -> {
            try {
                if (adbShellStream == null || adbShellStream.isClosed()) {
                    AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplication());
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
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        jsHelper = ((App) getApplication()).getJsHelper();
        base64png.observeForever(screenCaptureObserver);
        currentPkg.observeForever(packageCheckObserver);
        createNotification();
        initTTS();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // 设置语言（例如中文）
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 语言数据缺失或不支持，提示用户下载
                    Log.e("TTS", "语言不支持");
                } else {
                    Log.d(TAG, "设置声音属性");
                    tts.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // 用途
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // 内容类型
                                    .build()
                    );
                    // 配置音频管理器以使用扬声器
                    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioManager.setSpeakerphoneOn(true);

                }
            } else {
                Log.e("TTS", "初始化失败");
            }
        });
    }

    private void say(String text){
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void goEvent(Event event, JSONObject params) {
        Log.d("TEST", "goEvent: " + event.toString() + " " + params.toString());
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

    // 获取当前屏幕，进行OCR文字识别，调用目标函数，根据结果调度，处理异常
    private void goEvtStart() {
        if (!script.isEmpty()) {
            executePackageCheck();
        }
    }

    private void executePackageCheck() {
        execute("dumpsys window | grep mCurrentFocus | awk -F'/' '{print $1}' | awk '{print $3}'| sed 's/^/package:/'");
    }

    private final Observer<CharSequence> packageCheckObserver = output -> {
        String pkg = output.toString();
        if (targetpkg.isEmpty() || pkg.equals(targetpkg)) {
            goEvent(Event.CaptureScreen, new JSONObject());
        } else {
            Log.d(TAG, "wrong package: " + pkg + " " + targetpkg);
            goEvent(Event.WaitPackage, new JSONObject());
        }
    };
    private final Observer<CharSequence> screenCaptureObserver = output -> processScreenCapture(output.toString());
    public static Bitmap decodeBase64ToBitmap(String base64String) {
        Log.d(TAG, "decodeBase64ToBitmap, base64String length: " + base64String.length());
        try {
            // 1. Decode the Base64 string to a byte array
            byte[] imageBytes = Base64.decode(base64String, Base64.DEFAULT);

            // 2. Create a Bitmap from the byte array
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
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
                say("未定义界面");
                new Handler().postDelayed(this::stopSelf, 3000);
                return;
            }
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
                try {
                    say(reason);
                    int ms = params.getInt("ms");
                    return CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(ms);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "Error speaking:", e);
                }
                break;
            case "finish":
                safeQuit(true);
                return CompletableFuture.completedFuture(null);
        }
        return null;
    }

    private void executeScreenCapture() {
        execute("echo \">>>\";screencap -p | base64; echo \"<<<\"");
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
        base64png.removeObserver(screenCaptureObserver);
        currentPkg.removeObserver(packageCheckObserver);
        clearAllNotifications();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (jsenv != null) {
            jsenv.close();
        }
    }

    private void goEvtConnect2ADB() {
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
                    say("无法连接到ADB");
                    goEvent(Event.Sleep, new JSONObject().put("ms", 5000));
                } else {
                    goEvent(Event.Start, new JSONObject());
                }
            } catch (Exception e) {
                say("连接到ADB异常");
                try {
                    goEvent(Event.Sleep, new JSONObject().put("ms", 5000));
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
                Log.d("TEST", "Error connecting to ADB", e);
            }

        });
    }

    private void clearAllNotifications() {
        notificationManager.cancelAll();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Objects.equals(intent.getAction(), ACTION_STOP)) {
            safeQuit(false);
            return START_NOT_STICKY;
        }
        script = intent.getStringExtra("script");
        if (jsenv != null) {
            jsenv.close();
        }
        jsenv = jsHelper.newJsIsolate();
        initRuntime();
        goEvent(Event.Start, new JSONObject());
        return START_NOT_STICKY;
    }

    private void safeQuit(boolean succ) {
        script = "";
        if (succ) {
            say("任务完成");
        } else {
            say("任务中止");
        }
        clearAllNotifications();
        new Handler().postDelayed(this::stopSelf, 3000);
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
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "滑动通知",
                NotificationManager.IMPORTANCE_HIGH
        );
        notificationManager.createNotificationChannel(channel);
    }
}

