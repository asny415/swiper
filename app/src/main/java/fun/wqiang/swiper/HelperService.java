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
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbInputStream;
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
import java.util.function.Consumer;

enum Event {
    Start,
    Sleep,
    ConnectADB
}

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
                Log.d("TEST", "GEt somehting:" + s);
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
        goEvent(Event.Start, new JSONObject());
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
         executeScreenCapture();
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
        });
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

