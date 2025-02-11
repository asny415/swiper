package `fun`.wqiang.swiper

import androidx.compose.runtime.MutableState
import org.json.JSONObject
import java.util.concurrent.CompletableFuture

class GreetingDataModel(val connected: Boolean,
                        var pairPort: String,
                        val selectedPage: MutableState<String>,
                        val maxVolumn:Int,
                        val currentVolumn:Int,
                        val showImportDialog: MutableState<Boolean>,
                        var importDialogResult: MutableState<CompletableFuture<Boolean>>,
                        val scripts: List<JSONObject>,
                        val onClickItem: (item: JSONObject) -> Unit = {},
                        val onDeletedItem: (item: JSONObject) -> Unit = {},
                        val onShowDialog: () -> Unit = {},
                        val onPair: (port: Int, pairCode: String) -> Unit = { _, _ -> },
                        val setVolumn: (int: Int) -> Unit = {},
                        val readSettingSpeak: () -> Boolean = { false },
                        val readSettingAllowImport: () -> Boolean = { false },
                        val saveSettingSpeak: (b: Boolean) -> Unit = {},
                        val saveSettingAllowImport: (b: Boolean) -> Unit = {},)
