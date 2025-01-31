package fun.wqiang.swiper;

import static io.github.muntashirakon.adb.LocalServices.SHELL;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

enum Event {
    Start,
    Sleep,
    ConnectADB
}

public class HelperService extends Service {
    private static final String TAG = "HelperService";
    public static final int NOTIFICATION_ID = 1;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFuture;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;

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

    private String script="";
    private JsHelper jsHelper = null;
    private JSONObject ctx = new JSONObject();

    public void execute(String command) {
        Log.d("ADB", "execute command: " + command);
        executor.submit(() -> {
            try {
                if (command.equals("clear")) {
                    clearEnabled = true;
                    return;
                }
                if (adbShellStream == null || adbShellStream.isClosed()) {
                    AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplication());
                    adbShellStream = manager.openStream(SHELL);
                    new Thread(outputGenerator).start();
                }
                try (OutputStream os = adbShellStream.openOutputStream()) {
                    os.write(String.format("%1$s\n", command).getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                if (Objects.equals(e.getMessage(), "Not connected to ADB.")) {
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
        commandOutput.observeForever(screenCaptureObserver);
        initTTS();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // 设置语言（例如中文）
                    int result = tts.setLanguage(Locale.CHINESE);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 语言数据缺失或不支持，提示用户下载
                        Log.e("TTS", "语言不支持");
                    } else {
                        tts.setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // 用途
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // 内容类型
                                        .build()
                        );
                    }
                } else {
                    Log.e("TTS", "初始化失败");
                }
            }
        });
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
        }
    }

    // 获取当前屏幕，进行OCR文字识别，调用目标函数，根据结果调度，处理异常
    private void goEvtStart() {
        if (!script.isEmpty()) {
            executeScreenCapture();
        }
    }

    private final Observer<CharSequence> screenCaptureObserver = output -> {
        processScreenCapture(output.toString());
    };
    public static Bitmap decodeBase64ToBitmap(String base64String) {
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
    private void processScreenCapture(String string) {
        String[] lines = string.split("\n");
        if (lines.length == 0 || !lines[lines.length -1].contains("$") ) return;
        ArrayList<String> base64Lines = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains("$") && !line.contains("|")) {
                base64Lines.add(line);
            }
        }
        if (base64Lines.isEmpty()){
            return;
        }
        String base64 = String.join("", base64Lines);
        execute("clear");
        Log.d("TEST", "Image base64 is:" + base64.length());
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        img2text(bitmap).thenAccept(jsonObject -> {
           Log.d("TEST", "GOT JSON:" + jsonObject.toString());
            try {
                ctx.put("width", jsonObject.getInt("width"));
                ctx.put("height", jsonObject.getInt("height"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            JSONArray nodes;
            try {
                nodes = jsonObject.getJSONArray("results");
            } catch (JSONException e) {
                Log.e("TEST", "Error getting nodes:", e);
                throw new RuntimeException(e);
            }
            String runs  =  jsHelper.executeJavaScript(script + "\n" + "JSON.stringify(logic("+ctx.toString()+","+nodes.toString()+"))");
            Log.d("TEST", "logic result:"+runs);
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
                } catch (JSONException e) {
                }
                Objects.requireNonNull(runOpt(opt.getString("opt"), reason, opt.getJSONObject("params"))).get();
            }
            goEvent(Event.Start, new JSONObject());
        } catch (JSONException | ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error running logic:", e);
            throw new RuntimeException(e);
        }
        obj.remove("opts");
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
        if (opt.equals("sleep")) {
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
        } else if (opt.equals("click")) {
            try {
                int x = (int) params.getDouble("x");
                int y = (int) params.getDouble("y");
                execute("input tap " + x + " " + y);
                return CompletableFuture.completedFuture(null);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else if (opt.equals("swipe")) {
            try {
                int x1 = (int) params.getDouble("x1");
                int y1 = (int) params.getDouble("y1");
                int x2 = (int) params.getDouble("x2");
                int y2 = (int) params.getDouble("y2");
                int duration = (int) params.getDouble("duration");
                execute("swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " +duration);
                return CompletableFuture.completedFuture(null);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else if (opt.equals("say")) {
            try {
                tts.speak(reason, TextToSpeech.QUEUE_FLUSH, null, null);
                int ms = params.getInt("ms");
                return CompletableFuture.runAsync(()->{
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "Error speaking:", e);
            }
        } else if (opt.equals("finish")) {
            tts.speak("今日任务完成", TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "听到说话了吗？");
            script = "";
            return CompletableFuture.completedFuture(null);
        }
        return null;
    }

    private void executeScreenCapture() {
        execute("clear");
        execute("screencap -p | base64");
    }

    private void goEvtSleep(JSONObject params) {
        long delay;
        try {
            delay = params.getLong("ms");
        } catch (JSONException e) {
            delay = 1000;
        }
        scheduler.schedule(()->{
            goEvent(Event.Start, new JSONObject());
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        commandOutput.removeObserver(screenCaptureObserver);
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
                    showToast("无法连接到ADB");
                    goEvent(Event.Sleep, new JSONObject().put("ms", 5000));
                } else {
                    goEvent(Event.Start, new JSONObject());
                }
            } catch (Exception e) {
                showToast("连接到ADB异常");
                try {
                    goEvent(Event.Sleep, new JSONObject().put("ms", 5000));
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
                Log.d("TEST", "Error connecting to ADB", e);
            }

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("TEST", String.format("************ start command with action: %s %b", intent.getAction(), scheduledFuture == null));
        script = intent.getStringExtra("script");
        initRuntime();
        goEvent(Event.Start, new JSONObject());
        return START_NOT_STICKY;
    }

    private void initRuntime() {
        ctx=new JSONObject();
        String pkg =  jsHelper.executeJavaScript(script + "\n" + "pkg");
        Log.d("TEST", "GOT PACKAGE on service: " + pkg);
    }

    private CompletableFuture<JSONObject> img2text(Bitmap bitmap) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        TextRecognizer textRecognizer =
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        if (bitmap != null) {
            // 创建 InputImage 对象
            JSONObject result = new JSONObject();
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            // 识别文本
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        // 处理识别结果
                        String resultText = text.getText();
                        try {
                            result.put("width", image.getWidth());
                            result.put("height", image.getHeight());
                            JSONArray results = new JSONArray();
                            result.put("results", results);
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

    private void showToast(final String message) {
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }


}

