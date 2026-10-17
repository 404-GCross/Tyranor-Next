# SDL2 运行库由 System35 与 System4 共用（libSDL2x + app32）

System35 与 System4 共用同一份重命名的 SDL2 2.32.10（原生库 `libSDL2x.so`，Java 层 `org.libsdl.app32`），与 ONS 插件的 SDL2 2.26.3（`libSDL2.so` / `org.libsdl.app`）并存。原因是现有 `org.libsdl.app` 的 `onCreate` 会硬校验 `nativeGetVersion()`，与 2.32.10 原生库必然冲突，且 ONS 插件自带同名 `libSDL2.so`；若为 System4 另建一份 SDL2，APK 会同时携带两份 SDL2 与两套 Java 层（约 +1.5 MB）。
