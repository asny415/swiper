package `fun`.wqiang.swiper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.javascriptengine.JavaScriptSandbox
import `fun`.wqiang.swiper.ui.theme.SwiperTheme
import org.json.JSONObject
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

class MainActivity : ComponentActivity() {
    private var viewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionLauncher()
        viewModel = MainViewModel(application)
        viewModel!!.jsSupported = JavaScriptSandbox.isSupported()
        setContent {
            SwiperTheme {
                var connected by remember { mutableStateOf(false) }
                var running by remember { mutableStateOf(false) }
                var pairPort by remember { mutableStateOf("") }
                var scripts by remember { mutableStateOf(listOf<JSONObject>()) }
                viewModel!!.connected.observe(this) {
                    Log.d("TEST", "connected: $it")
                    connected = it
                }
                viewModel!!.running.observe(this) {
                    running = it
                }
                viewModel!!.watchPairingPort().observe(this) { port ->
                    pairPort = if (port != -1) "$port" else ""
                }
                viewModel!!.watchScripts().observe(this) { scripts = it }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        vm = GreetingDataModel(connected, pairPort, scripts = scripts,
                            onClickItem ={ item ->
                                val intent = Intent(this, HelperService::class.java)
                                intent.putExtra("script", item.getString("code"))
                                startService(intent)
                                viewModel!!.showNotification(this)
                                viewModel!!.startPackage(this, item.getString("pkg"))
                            },
                            onShowDialog = {
                            viewModel!!.getPairingPort()
                        }, onPair = { port, pairCode ->
                            viewModel!!.pair(port, pairCode)
                        }),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->
        }
    @SuppressLint("InlinedApi")
    fun requestPermissionLauncher() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            // 权限未授予，申请权限
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel!!.checkNotifyReady()
        viewModel!!.autoConnect()
    }
}

@Composable
fun Greeting(vm:GreetingDataModel, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    Box(contentAlignment = Alignment.Center, modifier=modifier.fillMaxSize()){
        Column {
            if (vm.connected) {
                LazyColumn(modifier=modifier.fillMaxSize()) {
                    items(vm.scripts) { item ->
                        MyListItem(item, onClick = {
                            vm.onClickItem(item)
                        })
                    }
                }
            } else {
                Button(onClick = {
                    showDialog = true
                }) {
                    Text("ADB 配对")
                }
                if (showDialog) {
                    Dialog(
                        onDismissRequest = { showDialog=false },
                    ) {
                        vm.onShowDialog()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
                            // 对话框内容
                            Column{
                                OutlinedTextField(value = pairCode, onValueChange = {pairCode = it}, label={Text("验证码")},keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(value = vm.pairPort, onValueChange = {vm.pairPort = it}, label={Text("端口号")},keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                Spacer(modifier = Modifier.padding(16.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = {
                                        if (pairCode.isNotEmpty() && vm.pairPort.isNotEmpty()) {
                                            val port = vm.pairPort.toInt()
                                            vm.onPair(port, pairCode)
                                            showDialog = false
                                        }
                                    }) {
                                        Text("配对")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SwiperTheme {
        Greeting(GreetingDataModel(connected = true, pairPort = "1234", scripts = listOf("""{
            |"name":"支付宝视频脚本",
            |"package": "test.test.test",
            |"description":"这是一个测试脚本",
            |"icon":""
            |}""".trimMargin()).map { script -> JSONObject(script) }))
    }
}

fun base64ToImageBitmap(base64String: String): ImageBitmap {
    return try {
        val decodedBytes: ByteArray = Base64.decode(base64String, Base64.DEFAULT)
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        base64ToImageBitmap("iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==")
    }
}

@Composable
fun MyListItem(item: JSONObject, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(16.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = base64ToImageBitmap(item.getString("icon")),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = item.getString("name"), style = MaterialTheme.typography.titleMedium)
            Text(text = item.getString("description"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}