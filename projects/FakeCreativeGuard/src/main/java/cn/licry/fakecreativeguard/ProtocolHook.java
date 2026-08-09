package cn.licry.fakecreativeguard;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class ProtocolHook {
    private ProtocolHook() {
    }

    static void enable(final FakeCreativeGuard plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Client.SET_CREATIVE_SLOT
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                final Player player = event.getPlayer();
                if (player == null) {
                    event.setCancelled(true);
                    return;
                }

                // 真正合法的创造包必须同时满足：
                // 1) 服务端当前确实是 CREATIVE；2) 玩家具有本插件显式 bypass 权限。
                // 只靠客户端伪装/临时包无法获得 bypass 权限。
                boolean legal = player.getGameMode() == GameMode.CREATIVE && plugin.isAllowedCreative(player);
                if (!legal) {
                    event.setCancelled(true); // 第一时间丢弃包，不能让 NMS/PlotSquared 再处理
                    plugin.onIllegalCreativePacket(player, "SET_CREATIVE_SLOT");
                }
            }
        });
    }

    static void disable(FakeCreativeGuard plugin) {
        ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
    }
}
