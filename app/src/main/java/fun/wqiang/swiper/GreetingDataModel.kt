package `fun`.wqiang.swiper

class GreetingDataModel(val connected: Boolean, val running: Boolean,
                        var pairPort: String,
                        val onShowDialog: () -> Unit = {}, val onPair: (port: Int, pairCode: String) -> Unit = { _, _ -> }) {
}