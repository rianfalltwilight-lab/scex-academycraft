package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.energy.impl.NodeConn;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public abstract class BaseNodeMenu extends AcademyMenu {
    // Container-set-data payloads are signed 16-bit values on the wire. Split
    // the two potentially large energy values into words; 50,000/200,000 must
    // never wrap negative or appear as 3,392 in the standard/advanced GUI.
    private static final int DATA_COUNT = 9;
    private final ContainerData nodeData;
    private final String initialNodeName;
    private final String ownerLabel;
    private final boolean canEditNode;

    public BaseNodeMenu(MenuType<?> menuType, int windowId, Inventory inv, FriendlyByteBuf data, boolean hasInventory) {
        super(menuType, windowId, inv, data, hasInventory);
        if (pos != null && inv.player.level() instanceof ServerLevel level
                && level.getBlockEntity(pos) instanceof BaseNodeBlockEntity node) {
            initialNodeName = boundedNodeName(node.getNodeName());
            ownerLabel = ownerLabel(node);
            canEditNode = node.canManage(inv.player);
            nodeData = new ContainerData() {
                @Override
                public int get(int index) {
                    WiWorldData worldData = WiWorldData.getNonCreate(level);
                    NodeConn connection = worldData == null ? null
                            : worldData.getExistingNodeConnection(node);
                    return switch (index) {
                        case 0 -> connection == null ? 0 : Math.max(0, connection.getLoad());
                        case 1 -> connection == null ? Math.max(0, node.getCapacity())
                                : Math.max(0, connection.getCapacity());
                        case 2 -> MenuDataWords.low(boundedInt(node.getEnergy()));
                        case 3 -> MenuDataWords.high(boundedInt(node.getEnergy()));
                        case 4 -> MenuDataWords.low(boundedInt(node.getMaxEnergy()));
                        case 5 -> MenuDataWords.high(boundedInt(node.getMaxEnergy()));
                        case 6 -> boundedInt(node.getBandwidth());
                        case 7 -> boundedInt(node.getRange());
                        case 8 -> node.isConnected() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override public void set(int index, int value) {}
                @Override public int getCount() { return DATA_COUNT; }
            };
        } else {
            initialNodeName = data != null && data.readableBytes() > 0
                    ? data.readUtf(NetworkInputLimits.NODE_NAME)
                    : "Unnamed";
            ownerLabel = data != null && data.readableBytes() > 0
                    ? data.readUtf(64) : "";
            canEditNode = data != null && data.readableBytes() > 0 && data.readBoolean();
            nodeData = new SimpleContainerData(DATA_COUNT);
        }
        addDataSlots(nodeData);

        addAcademySlot(new Slot(container, 0, 42, 10) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return canEditNode && EnergyItemHelper.isEnergyItem(item);
            }

            @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                return canEditNode;
            }
        });

        addAcademySlot(new Slot(container, 1, 42, 80) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return canEditNode && EnergyItemHelper.isEnergyItem(item);
            }

            @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                return canEditNode;
            }
        });
    }

    public int getNodeLoad() { return Math.max(0, nodeData.get(0)); }
    public int getNodeCapacity() { return Math.max(0, nodeData.get(1)); }
    public int getNodeEnergy() { return Math.max(0, MenuDataWords.join(nodeData.get(2), nodeData.get(3))); }
    public int getNodeMaxEnergy() { return Math.max(1, MenuDataWords.join(nodeData.get(4), nodeData.get(5))); }
    public int getNodeBandwidth() { return Math.max(0, nodeData.get(6)); }
    public int getNodeRange() { return Math.max(0, nodeData.get(7)); }
    public boolean isConnected() { return nodeData.get(8) != 0; }
    public String getInitialNodeName() { return initialNodeName; }

    /** Current public block-entity mirror; the opening snapshot is only a fallback. */
    public String getCurrentNodeName() {
        if (pos != null && inv.player.level().getBlockEntity(pos) instanceof BaseNodeBlockEntity node) {
            return boundedNodeName(node.getNodeName());
        }
        return initialNodeName;
    }
    public String getOwnerLabel() { return ownerLabel; }
    public boolean canEditNode() { return canEditNode; }

    /** Writes the complete, bounded client opening snapshot for all node tiers. */
    public static void writeOpeningData(FriendlyByteBuf buffer, BlockPos pos,
                                        BaseNodeBlockEntity node,
                                        net.minecraft.world.entity.player.Player viewer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(boundedNodeName(node.getNodeName()), NetworkInputLimits.NODE_NAME);
        buffer.writeUtf(ownerLabel(node), 64);
        buffer.writeBoolean(node.canManage(viewer));
    }

    private static String ownerLabel(BaseNodeBlockEntity node) {
        if (node == null || node.getOwnerUUID() == null) return "";
        if (node.getLevel() instanceof ServerLevel level) {
            var online = level.getServer().getPlayerList().getPlayer(node.getOwnerUUID());
            if (online != null) return online.getGameProfile().getName();
            var profileCache = level.getServer().getProfileCache();
            if (profileCache != null) {
                var cached = profileCache.get(node.getOwnerUUID());
                if (cached.isPresent()) return cached.get().getName();
            }
        }
        return node.getOwnerUUID().toString();
    }

    private static String boundedNodeName(String name) {
        if (name == null) return "Unnamed";
        return name.length() <= NetworkInputLimits.NODE_NAME
                ? name
                : name.substring(0, NetworkInputLimits.NODE_NAME);
    }

    private static int boundedInt(double value) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
