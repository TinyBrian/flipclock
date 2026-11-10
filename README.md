# Android
AndroidProj
安卓横屏显示的翻页时钟，用了别人的一个库，改了很多，所以有些地方的代码可能比较奇怪
https://github.com/TinyBrian/flipclock

最低支持 Android 4.4（API 19）。老版本通过 AndroidX 兼容定位权限、ICU4J 农历和兼容的日期时间处理实现支持。

时钟默认使用硬件加速；若应用前台运行时异常退出，下次启动会自动切换为软件渲染并保持该设置。清除应用数据可重置回硬件加速模式。

天气信息使用设备定位并通过 Open-Meteo 获取，需授予定位权限并连接网络。天气现象翻译为中文，气温四舍五入；数据依据 CC BY 4.0 许可使用，应用内提供数据来源链接。Open-Meteo 免费 API 仅供非商业用途；商业使用请遵循其服务条款。

时钟数字占背景框的比例可通过 `FlipLayout` 的 `flipTextWidthFill` 和 `flipTextHeightFill` 分别调整，取值范围为大于 0 且不超过 1。例如设置为 `0.90` 表示占可用宽度或高度的 90%。也可以在代码中调用 `setTextFillRatios(widthFill, heightFill)` 动态调整。

应用在前台运行时，时钟跨入新的一小时会通过系统中文语音播报整点；需设备上有可用的中文语音引擎。