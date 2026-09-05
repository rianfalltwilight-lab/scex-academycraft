package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class AcademyMenu extends AbstractContainerMenu {

    /** 背包栏坐标常量 */
    /** Legacy 1.0.7 TechUIContainer origin.  The bundled UI texture is the
     * original asset, so moving the slots to vanilla's usual x=8 visibly
     * shifts every item and hit box two pixels to the right of its frame. */
    public static final int INV_X = 6;
    public static final int INV_Y = 105;
    public static final int HOTBAR_Y = 163;

    public final Inventory inv;
    public final AcademyMenuContainer container = new AcademyMenuContainer(this);
    public BlockPos pos;

    /** 翻页时是否禁用槽位交互(机器页=0 激活,无线页=1 禁用) */
    private boolean slotsActive = true;
    private final BlockEntity boundEntity;
    private com.mohistmc.academy.network.MenuActionToken.Session actionSession;

    /** UUIDs travel as eight signed-short container data words plus a ready marker. */
    private void initializeActionSession() {
        actionSession = inv.player.level().isClientSide()
                ? new com.mohistmc.academy.network.MenuActionToken.Session()
                : new com.mohistmc.academy.network.MenuActionToken.Session(java.util.UUID.randomUUID());
        addDataSlots(new net.minecraft.world.inventory.ContainerData() {
            @Override public int get(int index) { return actionSession.word(index); }
            @Override public void set(int index, int value) { actionSession.receiveWord(index, value); }
            @Override public int getCount() {
                return com.mohistmc.academy.network.MenuActionToken.Session.WORD_COUNT;
            }
        });
    }

    public boolean actionSessionReady() { return actionSession.ready(); }
    public com.mohistmc.academy.network.MenuActionToken nextActionToken() {
        return actionSession.next(containerId);
    }

    /** Each viewer has its own stream. Closing/reopening, death, dimension
     * changes and replacing the block invalidate that viewer's authority. */
    public boolean acceptAction(com.mohistmc.academy.network.MenuActionToken token,
                                net.minecraft.server.level.ServerPlayer player) {
        return player == inv.player && player.containerMenu == this && player.isAlive()
                && stillValid(player) && actionSession.accept(token, containerId);
    }
    public AcademyMenu(MenuType<?> menuType, int windowId, Inventory inv, FriendlyByteBuf data, boolean hasInventory) {
        super(menuType, windowId);
        this.inv = inv;
        if (data != null)
            this.pos = data.readBlockPos();
        this.boundEntity = pos == null ? null : inv.player.level().getBlockEntity(pos);
        if (hasInventory) {
            for (int k = 0; k < 3; ++k) {
                for (int i1 = 0; i1 < 9; ++i1) {
                    this.addSlot(pagedSlot(inv, i1 + k * 9 + 9, INV_X + i1 * 18, INV_Y + k * 18));
                }
            }

            for (int l = 0; l < 9; ++l) {
                this.addSlot(pagedSlot(inv, l, INV_X + l * 18, HOTBAR_Y));
            }
        }
        container.reloadItems();
        initializeActionSession();
    }

    /** 返回方块位置(与 Return 框架的 getPos 一致) */
    public BlockPos getPos() {
        return pos;
    }

    public void setSlotsActive(boolean active) {
        this.slotsActive = active;
    }

    public boolean areSlotsActive() {
        return slotsActive;
    }

    private Slot pagedSlot(Inventory inv, int index, int x, int y) {
        return new Slot(inv, index, x, y) {
            @Override
            public boolean isActive() {
                return slotsActive;
            }
        };
    }

    public Slot addAcademySlot(Slot slot) {
        // 包装为响应 slotsActive 的槽位
        Slot paged = new Slot(slot.container, slot.getSlotIndex(), slot.x, slot.y) {
            @Override
            public boolean isActive() {
                return slotsActive;
            }

            @Override
            public boolean mayPlace(ItemStack item) {
                // 手持为空时允许悬停高亮（findSlot 依赖 mayPlace(EMPTY)==true）
                return item.isEmpty() || slot.mayPlace(item);
            }

            @Override
            public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                return slot.mayPickup(player);
            }

            /** Preserve per-machine slot limits (for example the one-fan slot
             * and the one-core/one-plate matrix slots).  The old wrapper used
             * the vanilla default of 64, which allowed a whole stack to enter
             * slots that are explicitly single-item in AcademyCraft 1.0.7. */
            @Override
            public int getMaxStackSize() {
                return slot.getMaxStackSize();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return slot.getMaxStackSize(stack);
            }
        };
        addSlot(paged);
        container.addSlot(paged);
        return paged;
    }


    @Override
    public boolean stillValid(Player p_38874_) {
        return container.stillValid(p_38874_);
    }

    @Override
    public void slotsChanged(Container p_38868_) {
        AcademyContainerBlockEntity blockEntity = container.getBlockEntity(this);
        if (blockEntity != null) {
            blockEntity.setItems(container.items);
        }
        super.slotsChanged(p_38868_);
    }

    /**
     * 获取机器槽位数量（玩家背包之前的槽位）
     */
    protected int getMachineSlotCount() {
        return container.getContainerSize();
    }

    protected int getPlayerSlotStart() { return 0; }
    protected int getPlayerSlotEnd() { return Math.min(36, slots.size()); }
    protected int getMachineSlotStart() { return getPlayerSlotEnd(); }
    protected int getMachineSlotEnd() { return Math.min(slots.size(), getMachineSlotStart() + getMachineSlotCount()); }

    @Override
    public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        ItemStack itemstack = ItemStack.EMPTY;
        if (p_38942_ < 0 || p_38942_ >= slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(p_38942_);
        if (slot != null && slot.hasItem() && slot.mayPickup(p_38941_)) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int machineStart = getMachineSlotStart();
            int machineEnd = getMachineSlotEnd();
            if (p_38942_ >= machineStart && p_38942_ < machineEnd) {
                if (!this.moveItemStackTo(itemstack1, getPlayerSlotStart(), getPlayerSlotEnd(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, machineStart, machineEnd, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    BlockEntity boundEntity() { return boundEntity; }

    public static class AcademyMenuContainer implements Container, StackedContentsCompatible {

        private final AcademyMenu menu;
        private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);

        public AcademyMenuContainer(AcademyMenu menu) {
            this.menu = menu;
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            reloadItems();
            for (ItemStack stack : items) if (!stack.isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int p_18941_) {
            reloadItems();
            return p_18941_ < 0 || items.size() <= p_18941_ ? ItemStack.EMPTY : items.get(p_18941_);
        }

        @Override
        public ItemStack removeItem(int p_18942_, int p_18943_) {
            reloadItems();
            ItemStack stack = p_18942_ < 0 || p_18942_ >= items.size()
                    ? ItemStack.EMPTY : ContainerHelper.removeItem(items, p_18942_, p_18943_);
            if (!stack.isEmpty()) saveItems();
            return stack;
        }

        public void saveItems() {
            AcademyContainerBlockEntity blockEntity = getBlockEntity(this.menu);
            if (blockEntity != null) {
                blockEntity.setItems(items);
            }
        }

        public void reloadItems() {
            AcademyContainerBlockEntity blockEntity = getBlockEntity(this.menu);
            if (blockEntity != null) {
                items = blockEntity.getItems();
            }
        }

        public AcademyContainerBlockEntity getBlockEntity(AcademyMenu menu) {
            if (menu != null && menu.pos != null) {
                BlockEntity entity = menu.inv.player.level().getBlockEntity(menu.pos);
                if (entity == menu.boundEntity() && entity instanceof AcademyContainerBlockEntity blockEntity && !blockEntity.isRemoved()) {
                    return blockEntity;
                }
            }
            return null;
        }

        @Override
        public ItemStack removeItemNoUpdate(int p_18951_) {
            reloadItems();
            if (p_18951_ < 0 || p_18951_ >= items.size()) return ItemStack.EMPTY;
            ItemStack result = ContainerHelper.takeItem(items, p_18951_);
            if (!result.isEmpty()) saveItems();
            return result;
        }

        @Override
        public void setItem(int p_18944_, ItemStack p_18945_) {
            reloadItems();
            if (p_18944_ >= 0 && items.size() > p_18944_) {
                items.set(p_18944_, p_18945_.copy());
                saveItems();
            }
        }

        @Override
        public void setChanged() {
            AcademyContainerBlockEntity blockEntity = getBlockEntity(this.menu);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player p_18946_) {
            boolean loaded = menu.pos != null && p_18946_.level().isLoaded(menu.pos);
            BlockEntity current = menu.pos == null ? null
                    : p_18946_.level().getBlockEntity(menu.pos);
            boolean valid = loaded && menu.boundEntity() != null && getBlockEntity(this.menu) != null
                    && p_18946_.distanceToSqr(menu.pos.getX() + 0.5, menu.pos.getY() + 0.5, menu.pos.getZ() + 0.5) <= 64.0
                    && p_18946_.level().mayInteract(p_18946_, menu.pos);
            if (!valid && Boolean.getBoolean("academy.machineVisualGate")) {
                com.mojang.logging.LogUtils.getLogger().error(
                        "Machine gate menu invalid: menu={} pos={} loaded={} distance={} mayInteract={} bound={} current={} boundRemoved={}",
                        menu.getClass().getSimpleName(), menu.pos, loaded,
                        menu.pos == null ? -1 : p_18946_.distanceToSqr(menu.pos.getX() + .5,
                                menu.pos.getY() + .5, menu.pos.getZ() + .5),
                        menu.pos != null && p_18946_.level().mayInteract(p_18946_, menu.pos),
                        menu.boundEntity() == null ? "null" : menu.boundEntity().getClass().getSimpleName()
                                + "@" + System.identityHashCode(menu.boundEntity()),
                        current == null ? "null" : current.getClass().getSimpleName()
                                + "@" + System.identityHashCode(current),
                        menu.boundEntity() != null && menu.boundEntity().isRemoved());
            }
            return valid;
        }

        @Override
        public void clearContent() {
            reloadItems();
            for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
            saveItems();
        }

        @Override
        public void fillStackedContents(StackedContents p_40281_) {
            for (ItemStack item : items) {
                p_40281_.accountSimpleStack(item);
            }
        }

        public void addSlot(Slot slot) {
            reloadItems();
            int wanted = slot.getSlotIndex() + 1;
            if (items.size() < wanted) {
                NonNullList<ItemStack> grown = NonNullList.withSize(wanted, ItemStack.EMPTY);
                for (int i = 0; i < items.size(); i++) grown.set(i, items.get(i));
                items = grown;
            }
        }
    }


}
