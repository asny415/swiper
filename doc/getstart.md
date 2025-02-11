# 如何开始使用

## 安装

## 打开Wi-Fi Debug

对于 Android 12 之前的机器，直接使用下面的命令

```shell
adb tcpip 5555
```

请不要更改默认的 5555 端口号.

对于 Android 12 之后的版本，需要进行配对

通过 adb 命令配对手机程序的方法是：

```shell
adb shell am broadcast -a "fun.wqiang.swiper.pair" -n fun.wqiang.swiper/.PairReceiver --es "code" "629840" --ei "port" 37719
```

请按需修改对应的端口号和配对码

## 写自己的第一个脚本

```JS
const module = {
  name: "支付宝视频无脑刷",
  description: "没有屏幕OCR，不费电，但是有点无脑",
  icon: getApplicationIcon("com.eg.android.AlipayGphone"),
  async go() {
    try {
      await launchPackage("com.eg.android.AlipayGphone");
      await delay(5000);
      while (true) {
        const [pkg, activity] = await checkRunning();
        console.log(`running: ${pkg} ${activity}`);
        if (pkg == "com.eg.android.AlipayGphone") {
          await swipeUp({ duration: 1000 });
        }
        await delay(3000 + Math.random() * 3000);
      }
    } catch (err) {
      await say(`${err}`);
    }
  },
};
```

## 脚本运行与上传

通过下面的命令将脚本直接保存在手机上，注意需将命令中的 "./alipay.js" 按照你的实际情况替换。

```shell
adb -d shell am start -a android.intent.action.SEND -n fun.wqiang.swiper/.MainActivity -e "script" "'$(cat ./alipay.js)'"
```

通过下面的命令直接在手机上运行脚本，注意需将命令中的 "./alipay.js" 按照你的实际情况替换。

```shell
adb -d shell am start -a android.intent.action.SEND -n fun.wqiang.swiper/.MainActivity -e "script" "'$(cat ./alipay.js)'" -e "run" 1
```
