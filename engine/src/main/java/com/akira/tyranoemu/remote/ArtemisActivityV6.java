package com.akira.tyranoemu.remote;

public final class ArtemisActivityV6 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
   // 官方拆解 Rev.3294（含 E-mote）：作为 V6 单独加载，避免覆盖现有 V1/V2/V3/V4/V5。
   loadResolvedEngineLibrary("artemis-v6");
 }
}
