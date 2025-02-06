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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import `fun`.wqiang.swiper.ui.theme.SwiperTheme
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.DismissDirection
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Surface
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var viewModel: MainViewModel? = null
    private var au: ActivityUtils? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionLauncher()
        viewModel = MainViewModel(application)
        au = ActivityUtils(this, viewModel!!)
        au!!.handleReceivedFile(intent)
        setContent {
            val gvm =au!!.getGreetingDataModel()
            SwiperApp(gvm)
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

    override fun onDestroy() {
        super.onDestroy()
        au!!.tts!!.shutdown()
    }
    override fun onResume() {
        super.onResume()
        viewModel!!.refreshAllScripts()
        if (viewModel!!.connected.value == false || viewModel!!.connected.value == null) {
            Log.d("MainActivity", "onResume: ${viewModel!!.connected.value}")
            viewModel!!.autoConnect()
        } else {
            Log.d("MainActivity", "onResume: ${viewModel!!.connected.value}")
        }
        val intent = Intent(this, HelperService::class.java)
        intent.setAction(HelperService.ACTION_STOP)
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwiperApp(gvm: GreetingDataModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val localContext = LocalContext.current
    val items = listOf("Home",  "Settings")
    val icons = listOf(Icons.Default.Home, Icons.Default.Settings)
    val (selectedItem, setSelectedItem) = remember { mutableStateOf(items[0]) }
    SwiperTheme  {
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
            ModalNavigationDrawer(drawerState = drawerState,drawerContent = {
            ModalDrawerSheet {
                // 抽屉头部
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                ) {
                    Text(localContext.getString(R.string.app_name), style = typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("一站式脚本管理工具", style = typography.bodyLarge)
                }

                // 抽屉项列表
                items.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(item) },
                        selected = item == selectedItem,
                        onClick = {
                            setSelectedItem(item)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            }){
                Scaffold(modifier = Modifier.fillMaxSize(),topBar = {
                    TopAppBar(
                        title = { Text( if (selectedItem == "Home") localContext.getString(R.string.app_name) else selectedItem) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                            }
                        }
                    )
                }){
                        paddingValues ->if (selectedItem == "Home")
                Greeting(gvm, modifier = Modifier.fillMaxSize().padding(paddingValues)) else
                    Setting(gvm, modifier = Modifier.fillMaxSize().padding(paddingValues))
                }
            }
        }
    }
}

@Composable
fun VolumeControl(gvm: GreetingDataModel) {
    val maxVolume = gvm.maxVolumn
    var volume by remember { mutableIntStateOf(
        gvm.currentVolumn
    ) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = when {
                    volume == 0 -> Icons.Default.Clear
                    volume < maxVolume / 2 -> Icons.Default.Add
                    else -> Icons.Default.ArrowDropDown
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "媒体音量",
                style = typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(volume / maxVolume * 100)}%",
                style = typography.bodyMedium
            )
        }

        Slider(
            value = volume.toFloat(),
            onValueChange = {
                volume = it.toInt()
                gvm.setVolumn(it.toInt())
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = maxVolume - 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
fun Setting(gvm: GreetingDataModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 音量控制卡片
        SettingCard(title = "声音设置") {
            VolumeControl(gvm)
        }

        // 其他设置项示例
        SettingCard(title = "通知设置") {
            SwitchSettingItem(
                title = "通知声音",
                description = "开启通知提示语音",
                checked = gvm.readSettingSpeak(),
                onChange = {gvm.saveSettingSpeak(it)}
            )
        }
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(checked) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = typography.bodyLarge
            )
            Text(
                text = description,
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                onChange(it)
            }
        )
    }
}

@Composable
fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = typography.titleMedium,
                color = colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
fun Greeting(vm:GreetingDataModel, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    Box(contentAlignment = Alignment.Center, modifier=modifier.fillMaxSize()){
        if (vm.connected) {
            Column(modifier = Modifier.fillMaxSize().align(Alignment.TopEnd)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    SwiperApp(GreetingDataModel(connected = true, pairPort = "1234",
        currentVolumn = 10,
        maxVolumn = 100,
        scripts = List(1){listOf("""{
            |"name":"支付宝视频脚本",
            |"package": "test.test.test",
            |"description":"这是一个测试脚本",
            |"icon":""
            |}""".trimMargin())}.flatten().map { script -> JSONObject(script) }))
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