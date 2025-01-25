package `fun`.wqiang.swiper

import org.json.JSONObject

class GreetingDataModel(val connected: Boolean,
                        var pairPort: String,
                        val scripts: List<JSONObject>,
                        val onClickItem: (item: JSONObject) -> Unit = {},
                        val onShowDialog: () -> Unit = {}, val onPair: (port: Int, pairCode: String) -> Unit = { _, _ -> }) {
}