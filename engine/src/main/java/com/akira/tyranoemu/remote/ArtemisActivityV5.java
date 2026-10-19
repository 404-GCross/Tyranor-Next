package com.akira.tyranoemu.remote;

public final class ArtemisActivityV5 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
   // ar-test 提取的 Artemis Rev.3288 构建：作为 TyranorNext V3 单独加载。
   loadResolvedEngineLibrary("artemis-v5");
 }
}
