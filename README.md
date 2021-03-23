# theta-plugin-spp-roverc

このソフトウェアは、 Apache 2.0ライセンスで配布されている製作物が含まれています。<br>
This software includes the work that is distributed in the Apache License 2.0.


## Overview
このリポジトリは、C&R研究所さまから出版されている「THETAプラグインで電子工作」という書籍<br>
https://www.c-r.com/book/detail/1389

「CHAPTER 06 物体認識で動くメカナムホイール車 ～事例3」のプロジェクトファイル一式を公開しています。


動作している様子は以下画像をクリックして動画をご参照ください。


[![](https://img.youtube.com/vi/-LzXA4qnmjo/0.jpg)](https://www.youtube.com/watch?v=-LzXA4qnmjo)


プロジェクトに関する詳しい説明は、書籍をご参照ください。

## Supplement

- 2021/03/23: サービスに関するファイルの置き場所について<br>[補足Qiita記事の該当箇所](https://qiita.com/KA-2/items/b6d261d9b8bf584ebfe4#%E6%B3%A8%E6%84%8F%E3%82%B5%E3%83%BC%E3%83%93%E3%82%B9%E3%81%AE%E7%BD%AE%E3%81%8D%E5%A0%B4%E6%89%80%E3%81%AB%E3%81%A4%E3%81%84%E3%81%A6)を参照してください。このapk単独であればこのままでも動作しますが、同じサービスのファイル構成を持つ別プロジェクトのapkのサービスは動作できません。
　
- 2021/03/23: Wait処理が必要なケースが１つ漏れていました<br>
[EnableBluetoothClassicTask.javaの 61～62行目](https://github.com/theta-skunkworks/theta-plugin-spp-roverc/blob/main/app/src/main/java/com/theta360/pluginapplication/task/EnableBluetoothClassicTask.java#L61-L62)のコードの間に1行フラグを立てる処理が必要でした。<br>以下のように修正してください。

```EnableBluetoothClassicTask.java
        if (bluetoothPower.equals("OFF")) {
            //The processing that was missing in the following sample program was added.
            bluetoothRebootWait = true;
            Log.d(TAG,"set _bluetoothPower=ON");
```

この行がなかったため、Buletooth OFF（Z1のOLED上部にBluetoothマークが無い状態）から本プラグインを起動するとペアリングが行えませんでした。
この行を追加することによって、上記ケースでも正常に動作します。（[補足Qiita記事の該当箇所はこちら](https://qiita.com/KA-2/items/b6d261d9b8bf584ebfe4#theta%E3%83%97%E3%83%A9%E3%82%B0%E3%82%A4%E3%83%B3%E5%9B%BA%E6%9C%89%E4%BA%8B%E9%A0%85%E3%81%AE%E5%87%A6%E7%90%86)）


## Development Environment

### Camera
* RICOH THETA V Firmware ver.3.50.1 and above
* RICOH THETA Z1 Firmware ver.1.60.1 and above

### SDK/Library
* [RICOH THETA Plug-in SDK ver.2.0.10](https://github.com/ricohapi/theta-plugin-sdk)

### Development Software
* Android Studio 4.0.1/4.1.1
* gradle ver.5.1.1


## License

```
Copyright 2018 Ricoh Company, Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contact
![Contact](img/contact.png)

