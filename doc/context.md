# 全局函数

## 在JS中可用的函数列表

### getPackageIcon(pkg:String)

返回base64编码的某个pkg的icon，必须是手机上已经安装的pkg，否则返回空

### async adb(cmd:String):String

通过adb shell执行cmd命令，返回结果取决于命令本身，通常是adb命令执行结果的第一行文字

### getScreenWidth() :int

获取屏幕宽度

### getScreenHeight() :int

获取屏幕高度

### async say(String)

调用系统的TTS功能

### log(string)

打印到Logcat,TAG是 "\_\_JS_LOG\_\_"

### async screenOCR(zone?={x,y,width,height})

调用系统的OCR功能，返回 json 格式的文字识别结果。

可以通过zone参数指定要识别的区域，默认区域是整个屏幕

### async delay(ms)

setTimeout函数的语法糖

### async launchPackage(pkgname)

adb命令语法糖，通过包名启动某个程序

### checkRunning() : (pkgname, activityName)

adb命令语法糖，检查前台正在运行的程序

### async swipeUp({x1,y1,x2,y2,duration})

adb 命令语法糖，无脑上划

### async click(x1, y1)

adb 命令语法糖，点击某个位置

---

## 其他说明

支持 Math.random或者 Math.round等函数，不支持 setInterval命令

console.log 等同于 log 函数
