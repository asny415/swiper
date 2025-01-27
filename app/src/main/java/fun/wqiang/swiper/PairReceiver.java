package fun.wqiang.swiper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class PairReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SwiperPair",  "onReceive " + intent.getAction());
        if ("fun.wqiang.swiper.pair".equals(intent.getAction())) {
            String code = intent.getStringExtra("code");
            int port = intent.getIntExtra("port", 5555);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(context);
                try {
                    boolean pairingStatus = manager.pair("127.0.0.1", port,
                            code == null ? "" : code);
                    Log.d("SwiperPair",  "code: " + code + ", port: " + port + ", Paired: " + pairingStatus);
                } catch (Exception e) {
                    Log.d("SwiperPair",  "Exception: " + e.toString() + ", code: " + code + ", port: " + port + ", Paired: false");
                    throw new RuntimeException(e);
                }
             });
        }
    }
}
