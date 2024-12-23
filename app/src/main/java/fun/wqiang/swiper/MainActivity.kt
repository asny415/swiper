package `fun`.wqiang.swiper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import `fun`.wqiang.swiper.ui.theme.SwiperTheme
import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var viewModel:MainViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = MainViewModel(application)
        requestPermissionLauncher()
        setContent {
            SwiperTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        viewModel,
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
        viewModel?.checkNotifyReady()
        viewModel?.autoConnect()
    }
}

@Composable
fun Greeting(viewModel: MainViewModel?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    viewModel?.connected?.observe(context as ComponentActivity) {
        connected = it
    }
    viewModel?.running?.observe(context as ComponentActivity) {
        running = it
    }
    Box(contentAlignment = Alignment.Center, modifier=modifier.fillMaxSize()){
        Column {
            if (connected && !running) {
                Button(onClick = {
                    val intent = Intent(context, HelperService::class.java)
                    context.startService(intent)
                    finishAffinity(context as ComponentActivity)
                }) {
                    Text("开启服务")
                }
            }
            else if (running) {
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
                        viewModel?.watchPairingPort()?.observe(context as ComponentActivity) { port ->
                            pairPort = if (port != -1) "$port" else ""

                        }
                        viewModel?.getPairingPort()
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
                                OutlinedTextField(value = pairPort, onValueChange = {pairPort = it}, label={Text("端口号")},keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                Spacer(modifier = Modifier.padding(16.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = {
                                        if (pairCode.isNotEmpty() && pairPort.isNotEmpty()) {
                                            val port = pairPort.toInt()
                                            viewModel?.pair(port, pairCode)
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
        Greeting(null)
    }
}