package com.ajg.inventorysortify;

import com.ajg.inventorysortify.network.SortPayload;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(InventorySortify.MOD_ID)
public class InventorySortify {
    public static final String MOD_ID = "inventorysortify";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InventorySortify(IEventBus modBus) {
        modBus.addListener(InventorySortify::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional so the client can still join servers without the mod (sorting is then unavailable there).
        event.registrar("1").optional()
                .playToServer(SortPayload.TYPE, SortPayload.STREAM_CODEC, SortPayload::handle);
    }
}
