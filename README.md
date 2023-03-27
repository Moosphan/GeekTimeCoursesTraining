# GeekTime Android Courses Training
Android senior engineer courses training.
> 极客时间《Android开发高手课》课后练习整理。

## Course Catalog

| 章节     | 课后练习 repo 地址           | 总结&复盘 | 完成时间 |
| -------- | ---------------------------- | --------- | -------- |
| Chapter1 | [崩溃优化](./breakpadLib)    |           | 23/03/10 |
| Chapter2 | [内存优化](./bitmapAnalyzer) |           | 23/03/15 |
| Chapter3 | [卡顿优化](./processTracker) |           | 23/03/25 |
|          |                              |           |          |

> *PS：此处章节按照优化领域划分，并未与实际课程章节保持一致。*

## Branch Knowledge

> 以下是由每个章节学习过程中延伸出的知识点关键字整理汇总，方便查漏补缺。

#### 1. 崩溃优化

- Java 崩溃：在 Java 代码中，出现了未捕获异常，导致程序异常退出。

- Native 崩溃：在 Native 代码中访问非法地址，也可能是地址对齐出现了问题，或者发生了程序主动 abort，这些都会产生相应的 signal 信号，导致程序异常退出。

- 如何捕获 native 崩溃信息

  难点：保证客户端在极端条件下可以生成崩溃日志

  方式：

  1. Breakpad
  2. 第三方平台（Bugly、啄木鸟、FireBase）

- 如何衡量系统稳定性：异常率（即包含崩溃、ANR等异常情况，并不是简单崩溃率）

- 崩溃采集的信息

  1. 崩溃发生的进程、线程、崩溃类型、堆栈、时间等基本信息
  2. 设备及系统信息（机型、系统、厂商、CPU、ABI、是否为模拟器，是否 root，是否开启 xposed 等）
  3. 内存信息
  4. 资源信息（文件句柄、线程数、JNI引用列表等）
  5. 业务信息（崩溃发生的业务模块、具体的 Activity/Fragment、操作路径、埋点信息等）
  6. 其他信息（电量、网络、磁盘等）

- 崩溃分析

  1. 确认重点（根据采集信息分析发生环境）
  2. 寻找共性
  3. 问题复现
  4. 系统问题 Hook 等

> 崩溃攻防是一个长期的过程，我们希望尽可能地提前预防崩溃的发生，将它消灭在萌芽阶段。这可能涉及我们应用的整个开发流程，包括人员的培训、编译检查、静态扫描工作，还有规范的测试、灰度、发布流程等。

#### 2. 内存优化

> *可参考：https://mp.weixin.qq.com/s/KtGfi5th-4YHOZsEmTOsjg*

- Java 内存分析、Allocation Tracker、MAT、Profiler

- Native 内存分析、AddressSanitize （[ASan](https://source.android.com/docs/core/tests/debug/native-memory?hl=zh-cn)）、Malloc

- Bitmap 内存分配发展史（3.0、3.0-7.0、8.0开始）

- **内存优化治理**

  1. 设备分级

     - device-year-class划分，低端机合理降级
     - 缓存统一管理和监控
     - 合理优化进程模型，减少常驻、低端机特殊处理
     - 针对不同设备级别适配不同安装包大小，低端机推出轻量版

  2. Bitmap 优化

     - 统一图片库，收拢图片调用
     - 统一监控大图、重复图及所有图片占用总内存情况

  3. 内存泄漏

     即GC没有回收未使用的内存，常发生于短生命周期短对象被较长生命周期对象直接或间接以强引用持有。

     - Java 内存泄漏（LeakCanary）
     - native 内存泄漏（针对无法重编 so 的情况，使用了 PLT Hook 拦截库的内存分配函数实现 native hook 并重定向自己实现；针对可重编的 so 情况，通过 GCC 的“-finstrument-functions”参数给所有函数插桩来重定向）

  4. 内存监控

     - PSS、Java 堆、图片总内存情况定时采集
     - 内存情况分析和指标计算（内存异常率&触顶率）
     - 测试环境下监控 GC 状态（阻塞式 GC 次数和耗时）

  5. 线程监控

     OutOfMemoryError 这种异常根本原因在于申请不到足够的内存造成的，直接的原因是在创建线程时初始 stack size 的时候，分配不到内存导致的。这个异常是在 /art/runtime/thread.cc 中线程初始化的时候 throw 出来的。

     测试环境或灰度版本中可以定时 dump 出所有线程并监控是否超出阙值，及时预警。

     > *在默认的情况下一般初始化一个线程需要 mmap 1M 左右的内存空间，在 32bit 的应用中有 4g 的 vmsize，实际能使用的有 3g+，按这种估算，一个进程最大能创建的线程数可达 3000+，当然这是理想的情况，在 linux 中对每个进程可创建的线程数也有一定的限制（/proc/pid/limits）而实际测试中，我们也发现不同厂商对这个限制也有所不同，而且当超过系统进程线程数限制时，同样会抛出这个类型的 OOM。*

#### 3. 卡顿优化

