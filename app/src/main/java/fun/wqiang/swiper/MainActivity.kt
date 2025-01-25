package `fun`.wqiang.swiper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.core.content.ContextCompat
import androidx.javascriptengine.JavaScriptSandbox
import `fun`.wqiang.swiper.ui.theme.SwiperTheme


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
                var pairCode by remember { mutableStateOf("") }
                var pairPort by remember { mutableStateOf("") }
                viewModel!!.connected.observe(this) {
                    connected = it
                }
                viewModel!!.running.observe(this) {
                    running = it
                }
                viewModel!!.watchPairingPort().observe(this) { port ->
                    pairPort = if (port != -1) "$port" else ""
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        vm = GreetingDataModel(connected, running, pairPort, onShowDialog = {
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
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    Box(contentAlignment = Alignment.Center, modifier=modifier.fillMaxSize()){
        Column {
            if (vm.connected && !vm.running) {
                Button(onClick = {
                    val intent = Intent(context, HelperService::class.java)
                    intent.putExtra("script", "function logic(code) { return JSON.stringify({opt:'pass'}); }")
                    context.startService(intent)
                    finishAffinity(context as ComponentActivity)
                }) {
                    Text("开启服务")
                }
            }
            else if (vm.running) {
                Button(onClick = {
                    val intent = Intent(context, HelperService::class.java)
                    context.stopService(intent)
                    finishAffinity(context as ComponentActivity)
                }) {
                    Text("停止服务")
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
        Greeting(GreetingDataModel(connected = false, running = false, pairPort = "1234" ))
    }
}