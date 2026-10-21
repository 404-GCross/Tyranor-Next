# Artemis Ogg 音频兼容修复（Issue #111）

## 故障与范围

部分压缩版游戏使用最高位为 1 的 Ogg 32 位流序列号。旧 arm64 Artemis 所带
Vorbisfile 在 serial-list 查找时，将该值分别零扩展和符号扩展后作 64 位相等比较，
导致同一流被误判为不同流。实机上 `ov_open_callbacks()` 返回 `-128`，BGM、语音无声；
最高位为 0 的音效可能正常。不能把所有 `-128` 都归因于此问题。

已核验受影响构建：`artemis`、`artemis-compatible`、`artemis-compatible-v2`、`artemis-v4`。
当前 V5/V6 对应 serial 比较已经使用 32 位指令；clean-room 不走这套 Vorbisfile。
修复只匹配 `ArtemisVorbisRepair.kt` 中的完整源文件 SHA-256，未知构建不作猜测修补。

## 实现

- 引擎层 `ArtemisVorbisRepair.prepare()` 在首次加载前生成私有只读副本，修复旧构建中
  serial 查找的起播、解码与 seek 路径：`CMP Xn, Xm` 改为 `CMP Wn, Wm`，仅改变 `sf` 位。
- 四份旧构建共 35 处比较；64 位文件位置、granule position、数组迭代指针比较保持不变。
  表内是 **ELF 文件偏移**，不是运行时虚拟地址；测试确认全部落在可执行 PT_LOAD。
- 源插件与游戏音频不修改，不对运行中的代码页 `mprotect`/写入，也不改变原音频序列号。
- 副本位于 `files/engine_plugins/artemis/derived/vorbis-serial-v1-<sourceSha256>/`，
  不放在可能被安装器设为只读的 `current` 内。完整源/输出哈希和旧指令全部校验后才原子发布。
- 使用 JVM 同步 + 文件锁协调同进程/跨进程准备；先设只读，再经已经打开的描述符写入并 sync。
  缓存损坏拒绝加载，不原位覆盖其他进程可能已映射的库。源库更新对应另一个内容寻址路径。
- `ArtemisLauncherBaseActivity.resolveArtemisEngineLibraryPath()` 固定本 Activity 的库路径，
  C++ bootstrap 的首次 `dlopen` 和随后 Java `System.load` 的 JNI 登记必须使用同一副本。
  仍保留 C++ 与 Java 库名白名单，不从 Intent 接收任意库路径。
- 原有 `CSoundTrack::Read` 无进展递归保护继续保留，它是播放阶段防爆栈措施，不代替本修复。

## 验证

```sh
./gradlew :engine:testDebugUnitTest assembleDebug --no-daemon
```

JVM 测试覆盖四旧库完整补丁输出、源文件不变、缓存复用/损坏拒绝、只读权限、符号链接、
合法系统父目录别名、线程并发、V5/V6 指令语义及 bootstrap/JNI 路径约定。
线程测试不等于独立进程锁的压力测试。

实机集成测试源码：`engine/src/test/cpp/artemis_vorbis_smoke.cpp`，用 NDK arm64 clang++
编译并以调试 App UID 执行。样本由本地用户提供，不随仓库分发：

```sh
artemis_vorbis_smoke /absolute/libartemis.so /absolute/sample.ogg
```

2026-10-19，HONOR Android 16，用户提供的 Meteor World Actor: Badge & Dagger 样本：

- 原 V1：`bgm17_a.ogg`、`cla_90003.ogg` 打开失败 `-128`；低位 serial 的 `bgm17_b.ogg` 正常。
- 修复后 V1/V2/V3/V4，以及未改的 V5/V6：三个样本均打开成功，完整解码得到非静音 PCM，
  两次从头回绕、尾部 PCM seek 均通过。
- 已覆盖安装 Debug 包。真实游戏进程仅映射修复副本，游戏 BGM 运行时混音输出采样
  90/90 样本非零，峰值 2195；调试器解除后 `TracerPid=0`。

上述验证不是所有游戏/所有机型验收，也不包含人工听感或完整 chained-stream 测试。
无声原始日志和调试阶段的 SIGTRAP 必须分开；前期诊断探针造成的退出不属于原游戏故障。
