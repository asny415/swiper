package `fun`.wqiang.swiper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
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
import org.json.JSONObject
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import `fun`.wqiang.swiper.ui.theme.SwiperTheme
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.DismissDirection
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.SwipeToDismiss
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.rememberDismissState

class MainActivity : ComponentActivity() {
    private var viewModel: MainViewModel? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionLauncher()
        viewModel = MainViewModel(application)
        ActivityUtils(this, viewModel!!).handleReceivedFile(intent)
        setContent {
            SwiperTheme  {
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
                val gvm = GreetingDataModel(connected, pairPort, scripts = scripts,
                    onDeletedItem = { item ->
                        ActivityUtils(this, viewModel!!).unlinkFile(item.getString("filename"))
                    },
                    onClickItem = { item ->
                        viewModel!!.disconnect()
                        val intent = Intent(this, HelperService::class.java)
                        intent.putExtra("script", item.getString("code"))
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        viewModel!!.startPackage(this, item.getString("pkg"))
                    },
                    onShowDialog = {
                        viewModel!!.getPairingPort()
                    }, onPair = { port, pairCode ->
                        viewModel!!.pair(port, pairCode)
                    })
                Scaffold(content = {
                    paddingValues ->
                    Greeting(gvm, modifier = Modifier.padding(paddingValues))
                })
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
        viewModel!!.refreshAllScripts()
        viewModel!!.autoConnect()
        val intent = Intent(this, HelperService::class.java)
        intent.setAction(HelperService.ACTION_STOP)
        startService(intent)
    }
}

@Composable
fun Greeting(vm:GreetingDataModel, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    Box(contentAlignment = Alignment.Center, modifier=modifier.fillMaxWidth()){
        if (vm.connected) {
            Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopEnd)) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(vm.scripts) { item ->
                        MyListItem(item, onClick = {
                            vm.onClickItem(item)
                        }, onDeletedListener = {
                            vm.onDeletedItem(item)
                        })
                    }
                }
            }
        } else {
            Column{
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
        Scaffold { paddingValues->
            Greeting(GreetingDataModel(connected = true, pairPort = "1234", scripts = List(1){listOf("""{
            |"name":"支付宝视频脚本",
            |"package": "test.test.test",
            |"description":"这是一个测试脚本",
            |"icon":""
            |}""".trimMargin())}.flatten().map { script -> JSONObject(script) }), modifier = Modifier.fillMaxWidth().padding(paddingValues))
    }}
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MyListItem(item: JSONObject, onClick: () -> Unit, onDeletedListener: () -> Unit) {
    val dismissState = rememberDismissState()
    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            val color = when (dismissState.dismissDirection) {
                DismissDirection.EndToStart -> Color.Red
                else -> Color.Transparent
            }
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(16.dp),
            contentAlignment = Alignment.CenterEnd
            ) {
            Text("删除")
        }
        },
        dismissContent = {
            ListItem(modifier = Modifier.background(Color.White).clickable { onClick() },
                leadingContent = {
                    Image(
                bitmap = base64ToImageBitmap(item.getString("icon")),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )},
                headlineContent = {Text(item.getString("name"))},
                supportingContent = {Text(item.getString("description"))})
    })

    // 监听滑动状态
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.isDismissed(DismissDirection.EndToStart)) {
            onDeletedListener()
        }
    }
}