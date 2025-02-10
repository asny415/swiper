# Context 全局对象

ctx 是一个全局可以访问的对象，用来和Java交互。

这个文档记录 Context 对象的 API

## Context 对象可用的 API

### getPackageIcon(pkg:String)

返回base64编码的某个pkg的icon，必须是手机上已经安装的pkg，否则返回空

### adb(cmd:String)

通过adb shell执行cmd命令，返回一个promise\<void>
