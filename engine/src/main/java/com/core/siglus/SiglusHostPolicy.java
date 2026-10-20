package com.core.siglus;

import com.core.gl.GraphicsFilterChain;

/** Select once at launch: disabled or unusable chains retain the upstream host. */
final class SiglusHostPolicy {
    static GraphicsFilterChain requestedChain(boolean enabled, String encoded) {
        if (!enabled) return null;
        GraphicsFilterChain chain = GraphicsFilterChain.parse(encoded);
        return chain != null && chain.hasActive() ? chain : null;
    }
}
