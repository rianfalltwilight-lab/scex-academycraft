package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * 无线虚能矩阵菜单 —— 矩阵核心槽 + 约束金属板槽。
 */
public class MatrixMenu extends AcademyMenu {
    private final ContainerData machineData;
    private final String initialSsid;
    private final String ownerLabel;
    private final boolean canEdit;

    public MatrixMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.MATRIX_MENU.get(), windowId, inv, data, true);

        if (!inv.player.level().isClientSide()
                && inv.player.level().getBlockEntity(pos) instanceof MatrixBlockEntity be) {
            initialSsid = be.getSSID();
            ownerLabel = ownerLabel(be);
            canEdit = be.canManage(inv.player);
            machineData = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        case 0 -> be.isInitialized() ? 1 : 0;
                        case 1 -> be.getCapacity();
                        case 2 -> (int) be.getBandwidth();
                        case 3 -> (int) be.getRange();
                        case 4 -> (be.hasPasswordConfigured() ? 1 : 0)
                                | (be.hasInitializationMaterials() ? 2 : 0);
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 5; }
            };
        } else {
            initialSsid = data != null && data.readableBytes() > 0
                    ? data.readUtf(NetworkInputLimits.SSID) : "";
            ownerLabel = data != null && data.readableBytes() > 0 ? data.readUtf(64) : "";
            canEdit = data != null && data.readableBytes() > 0 && data.readBoolean();
            machineData = new SimpleContainerData(5);
        }
        addDataSlots(machineData);

        // Preserve the final 1.12.2 ContainerMatrix order: three plate slots
        // are menu/container slots 0..2, followed by the core at slot 3.
        int[][] platePos = {{78, 11}, {53, 60}, {104, 60}};
        for (int i = 0; i < 3; i++) {
            addAcademySlot(new Slot(container, i, platePos[i][0], platePos[i][1]) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public int getMaxStackSize(ItemStack stack) { return 1; }
                @Override public boolean mayPickup(Player player) { return canEdit; }
                @Override
                public boolean mayPlace(ItemStack item) {
                    return canEdit && item.is(AcademyItems.CONSTRAINT_PLATE.get());
                }
            });
        }

        addAcademySlot(new Slot(container, MatrixBlockEntity.CORE_SLOT, 78, 36) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
            @Override public boolean mayPickup(Player player) { return canEdit; }
            @Override
            public boolean mayPlace(ItemStack item) {
                return canEdit && (item.is(AcademyItems.MAT_CORE_0.get())
                        || item.is(AcademyItems.MAT_CORE_1.get())
                        || item.is(AcademyItems.MAT_CORE_2.get()));
            }
        });
    }

    public boolean isInitialized() { return machineData.get(0) != 0; }
    public boolean isOperational() { return isInitialized() && hasInitializationMaterials(); }
    public int getCapacity() { return Math.max(0, machineData.get(1)); }
    public int getBandwidth() { return Math.max(0, machineData.get(2)); }
    public int getRange() { return Math.max(0, machineData.get(3)); }
    public boolean hasPasswordConfigured() { return (machineData.get(4) & 1) != 0; }
    public boolean hasInitializationMaterials() { return (machineData.get(4) & 2) != 0; }
    public String getInitialSsid() { return initialSsid; }
    public String getOwnerLabel() { return ownerLabel; }
    public boolean canEdit() { return canEdit; }

    /** Writes the bounded client opening snapshot separately from live data slots. */
    public static void writeOpeningData(FriendlyByteBuf buffer, BlockPos pos,
                                        MatrixBlockEntity matrix, Player viewer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(matrix.getSSID(), NetworkInputLimits.SSID);
        buffer.writeUtf(ownerLabel(matrix), 64);
        buffer.writeBoolean(matrix.canManage(viewer));
    }

    private static String ownerLabel(MatrixBlockEntity matrix) {
        if (matrix == null || matrix.getOwnerUUID() == null) return "";
        if (matrix.getLevel() instanceof ServerLevel level) {
            var online = level.getServer().getPlayerList().getPlayer(matrix.getOwnerUUID());
            if (online != null) return online.getGameProfile().getName();
            var profileCache = level.getServer().getProfileCache();
            if (profileCache != null) {
                var cached = profileCache.get(matrix.getOwnerUUID());
                if (cached.isPresent()) return cached.get().getName();
            }
        }
        return matrix.getOwnerUUID().toString();
    }
}
