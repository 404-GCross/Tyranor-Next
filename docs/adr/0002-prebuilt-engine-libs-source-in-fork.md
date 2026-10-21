# 内置原生引擎库以预构建 .so 入库，源码与修改声明放 fork

内置原生引擎库（System35、System4，FVP 同原则）以预构建 `.so` 入库（`engine/src/main/jniLibs/arm64-v8a/`），引擎源码与修改声明放在本账号 fork，依赖与源码不入主仓，改由仓库外的构建脚本按固定 commit 与 SHA 校验复现。这样既满足 GPL 系许可证对源码可得性的要求、保证第三方删库不影响合规，又避免源码与依赖拖大主仓（System35 原 vendored 目录约 65 MB 已删除），并让每个产物都有可复现的构建路径。FVP 的源码托管现状见 `docs/FVP引擎接入方案.md` §6。
