package `fun`.wqiang.swiper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.CompletableFuture

const val TAG = "TTSHelper"
class TTSHelper {
    companion object {
        @JvmStatic
    fun initTTS(context: Context, onTTSDone: (String) -> Unit): CompletableFuture<TextToSpeech> {
        val future: CompletableFuture<TextToSpeech> = CompletableFuture()
        val pm = PrefereManager(context)

        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status: Int ->
           if (status == TextToSpeech.SUCCESS) {
               // 设置语言（例如中文）
               val result: Int = tts!!.setLanguage(Locale.CHINESE)
               if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                   // 语言数据缺失或不支持，提示用户下载
                   Log.e("TTS", "语言不支持")
               } else {
                   Log.d(TAG, "设置声音属性")
                   tts!!.setAudioAttributes(
                       AudioAttributes.Builder()
                           .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // 用途
                           .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // 内容类型
                           .build()
                   )
                   // 配置音频管理器以使用扬声器
                   val audioManager =
                       context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                   audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                   audioManager.setSpeakerphoneOn(true)
                   tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                       override fun onStart(s: String) {
                       }

                       override fun onDone(s: String) {
                           onTTSDone(s)
                       }
                       @Deprecated("Deprecated in Java")
                       override fun onError(s: String) {
                       }
                   })
                   val vol = pm.getVolumn()
                   if (vol != -1) {
                       audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, vol, 0)
                   }
                   future.complete(tts)
               }
           } else {
               Log.e("TTS", "初始化失败")
           }
       }
            return future
    }

    }
}