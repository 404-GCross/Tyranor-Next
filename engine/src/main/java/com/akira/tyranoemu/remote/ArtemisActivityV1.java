package com.akira.tyranoemu.remote;

public final class ArtemisActivityV1 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
   // bootstrap loader 在 super.onCreate() 阶段已 dlopen 真实库并转发 ANativeActivity_onCreate
   // 启动引擎主体；此处再 System.load 同一 so，使 ART 将其实native 方法（OnFinishVideo 等）
   // 登记到 native library 表，否则 Java native 调用会 UnsatisfiedLinkError。
   loadResolvedEngineLibrary("artemis");
 }
}
