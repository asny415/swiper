package `fun`.wqiang.swiper

import android.content.Context

class PrefereManager(val context: Context) {
    fun saveVolumn(vol: Int) {
        val sharedPreferences = context.getSharedPreferences("Swiper", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("volumn", vol)
        editor.apply()
    }

    fun readSettingSpeak(): Boolean {
        val sharedPreferences = context.getSharedPreferences("Swiper", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("speak", true)
    }

    fun saveSettingSpeak(value: Boolean) {
        val sharedPreferences = context.getSharedPreferences("Swiper", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("speak", value)
        editor.apply()
    }

    fun getVolumn(): Int {
        val sharedPreferences = context.getSharedPreferences("Swiper", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("volumn", -1)
    }

}