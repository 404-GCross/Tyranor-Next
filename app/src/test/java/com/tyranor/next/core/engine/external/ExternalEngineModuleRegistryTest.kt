package com.tyranor.next.core.engine.external

import com.tyranor.next.core.engine.EngineType
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalEngineModuleRegistryTest {
    @Test
    fun resolvesRenPyByEngineAndAliases() {
        assertSame(RenPyExternalEngineModule, ExternalEngineModuleRegistry.moduleForEngine(EngineType.RENPY))
        assertSame(RenPyExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.renpy"))
        assertSame(RenPyExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("external.renpy"))
        assertSame(RenPyExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias(RenPyExternalEngineModule.packageName))
        assertTrue(ExternalEngineModuleRegistry.isExternalEngine(EngineType.RENPY))
        assertTrue(RenPyExternalEngineModule.installUrl.orEmpty().endsWith("/RenPy-Plugin.apk"))
    }

    @Test
    fun resolvesRpgMakerByEngineAndAliases() {
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForEngine(EngineType.RPGMAKER))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.rpgmaker"))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.rpgmxp"))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.rpgmvx"))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.rpgmvxace"))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias("internal.mkxp-z"))
        assertSame(RpgMakerExternalEngineModule, ExternalEngineModuleRegistry.moduleForAlias(RpgMakerExternalEngineModule.packageName))
        assertTrue(ExternalEngineModuleRegistry.isExternalEngine(EngineType.RPGMAKER))
        assertTrue(RpgMakerExternalEngineModule.installUrl.orEmpty().contains("RPGMPlugin"))
    }
}
