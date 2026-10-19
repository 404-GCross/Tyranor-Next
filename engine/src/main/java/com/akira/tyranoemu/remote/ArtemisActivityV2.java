package com.akira.tyranoemu.remote;

public final class ArtemisActivityV2 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
   // 见 ArtemisActivityV1：bootstrap loader 负责引擎主体入口，此处 System.load 登记 JNI 符号。
   loadResolvedEngineLibrary("artemis-compatible");
 }
}
