package `fun`.wqiang.swiper

import org.json.JSONObject

class GreetingDataModel(val connected: Boolean,
                        var pairPort: String,
                        val maxVolumn:Int, val currentVolumn:Int,
                        val scripts: List<JSONObject>,
                        val onClickItem: (item: JSONObject) -> Unit = {},
                        val onDeletedItem: (item: JSONObject) -> Unit = {},
                        val onShowDialog: () -> Unit = {},
                        val onPair: (port: Int, pairCode: String) -> Unit = { _, _ -> },
                        val setVolumn: (int: Int) -> Unit = {},
                        val readSettingSpeak: () -> Boolean = { false },
                        val saveSettingSpeak: (b: Boolean) -> Unit = {})
