package com.akira.tyranoemu.remote;

public final class ArtemisActivityV4 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
   // yrrw_1 提取的 Artemis 构建：作为 V4 单独加载，避免覆盖现有 V1/V2/V3。
   loadResolvedEngineLibrary("artemis-v4");
 }
}
