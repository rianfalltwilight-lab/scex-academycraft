package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.skill.AbilityInterferenceRules;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.AbilityInterferer;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative Ability Interferer machine rebuilt from the 1.0.7 tile. */
public final class AbilityInterfererBlockEntity extends AcademyContainerBlockEntity
        implements IWirelessReceiver, IFEnergyStorage {
    public static final int BATTERY_SLOT = 0;
    public static final int MAX_WHITELIST = 32;
    public static final int MAX_PLAYER_NAME = 16;
    private static final int BANDWIDTH = 50;

    public record WhitelistEntry(UUID id, String name) {}

    private int energy;
    private int range = AbilityInterferenceRules.MIN_RANGE;
    private boolean enabled;
    private UUID owner;
    private String ownerName = "";
    private final LinkedHashMap<UUID, String> whitelist = new LinkedHashMap<>();

    /** Paid pulse state is runtime-only and must never survive save/reload. */
    private long activeUntil = -1;
    private long nextPaymentTick = Long.MIN_VALUE;

    public AbilityInterfererBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.ABILITY_INTERFERER.get(), pos, state);
        setItems(NonNullList.withSize(1, ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        boolean changed = chargeFromBattery();
        long now = level.getGameTime();

        if (!enabled) {
            AbilityInterferenceService.remove(this);
            if (changed) setChanged();
            return;
        }

        if (nextPaymentTick == Long.MIN_VALUE) nextPaymentTick = now;
        if (now >= nextPaymentTick) {
            int cost = AbilityInterferenceRules.pulseCost(range);
            if (energy < cost) {
                setEnabled(false);
                return;
            }
            energy -= cost;
            activeUntil = now + AbilityInterferenceRules.PAYMENT_INTERVAL_TICKS - 1L;
            nextPaymentTick = now + AbilityInterferenceRules.PAYMENT_INTERVAL_TICKS;
            AbilityInterferenceService.publish(this, activeUntil);
            changed = true;
        }
        if (changed) setChanged();
    }

    private boolean chargeFromBattery() {
        ItemStack battery = getItems().get(BATTERY_SLOT);
        if (!EnergyItemHelper.isEnergyItem(battery) || energy >= getMaxEnergyStored()) return false;
        int accepted = EnergyItemHelper.extractEnergy(battery,
                Math.min(BANDWIDTH, getMaxEnergyStored() - energy), false);
        if (accepted <= 0) return false;
        energy += accepted;
        return true;
    }

    public int getRange() {
        return range;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPulseActive(long now) {
        return enabled && activeUntil >= now;
    }

    public void setEnabled(boolean wanted) {
        if (enabled == wanted) return;
        enabled = wanted;
        activeUntil = -1;
        nextPaymentTick = wanted && level != null ? level.getGameTime() : Long.MIN_VALUE;
        AbilityInterferenceService.remove(this);
        updateModelState();
        syncConfig();
    }

    public boolean setRange(int wanted) {
        if (wanted < AbilityInterferenceRules.MIN_RANGE || wanted > AbilityInterferenceRules.MAX_RANGE) {
            return false;
        }
        if (range == wanted) return true;
        range = wanted;
        // A larger range may not borrow the cheaper pulse already paid for.
        activeUntil = -1;
        nextPaymentTick = enabled && level != null ? level.getGameTime() : Long.MIN_VALUE;
        AbilityInterferenceService.remove(this);
        syncConfig();
        return true;
    }

    private void updateModelState() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(AbilityInterferer.STATUS)) {
            int wanted = enabled ? 1 : 0;
            if (state.getValue(AbilityInterferer.STATUS) != wanted) {
                level.setBlock(worldPosition, state.setValue(AbilityInterferer.STATUS, wanted),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    private void syncConfig() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Called only from the successful player placement path. */
    public void assignOwnerOnPlacement(Player player) {
        if (owner != null || player == null) return;
        assignOwner(player);
    }

    /** Command-placed and pre-owner saves require an administrator migration. */
    public boolean claimLegacyOwnerIfAbsent(ServerPlayer player) {
        if (!MachineOwnership.canClaimLegacy(owner, player)) return false;
        assignOwner(player);
        return true;
    }

    /** Package-private seam used only after an administrator authorizes a migration command. */
    void assignOwnerForMigration(ServerPlayer player) {
        if (owner == null && player != null) assignOwner(player);
    }

    private void assignOwner(Player player) {
        owner = player.getUUID();
        ownerName = sanitizeName(player.getGameProfile().getName());
        whitelist.put(owner, ownerName);
        syncConfig();
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean canManage(ServerPlayer player) {
        return MachineOwnership.canManage(owner, player);
    }

    public boolean isWhitelisted(UUID player) {
        return player != null && (player.equals(owner) || whitelist.containsKey(player));
    }

    public boolean addWhitelist(GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null
                || whitelist.size() >= MAX_WHITELIST && !whitelist.containsKey(profile.getId())) return false;
        String name = sanitizeName(profile.getName());
        if (name.isEmpty()) return false;
        whitelist.put(profile.getId(), name);
        syncConfig();
        return true;
    }

    public boolean removeWhitelist(UUID id) {
        if (id == null || id.equals(owner)) return false;
        if (whitelist.remove(id) == null) return false;
        syncConfig();
        return true;
    }

    public List<WhitelistEntry> getWhitelistEntries() {
        List<WhitelistEntry> result = new ArrayList<>(whitelist.size());
        for (Map.Entry<UUID, String> entry : whitelist.entrySet()) {
            result.add(new WhitelistEntry(entry.getKey(), entry.getValue()));
        }
        result.sort(Comparator.comparing((WhitelistEntry entry) -> !entry.id().equals(owner))
                .thenComparing(WhitelistEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return AbilityInterferenceRules.MAX_ENERGY;
    }

    @Override
    public void setEnergy(int energy) {
        this.energy = MachineStateSanitizer.clampAmount(energy, getMaxEnergyStored());
        setChanged();
    }

    @Override
    public double getRequiredEnergy() {
        return Math.max(0, getMaxEnergyStored() - energy);
    }

    @Override
    public double injectEnergy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return amount;
        double accepted = Math.min(amount, getMaxEnergyStored() - energy);
        if (accepted > 0) setEnergy(energy + (int) Math.floor(accepted));
        return amount - Math.floor(accepted);
    }

    @Override
    public double pullEnergy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        int pulled = Math.min(energy, (int) Math.floor(amount));
        if (pulled > 0) setEnergy(energy - pulled);
        return pulled;
    }

    @Override
    public double getBandwidth() {
        return BANDWIDTH;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energy = MachineStateSanitizer.clampAmount(tag.getInt("interfererEnergy"), getMaxEnergyStored());
        range = AbilityInterferenceRules.clampRange(tag.getInt("interfererRange"));
        enabled = tag.getBoolean("interfererEnabled");
        owner = tag.hasUUID("interfererOwner") ? tag.getUUID("interfererOwner") : null;
        ownerName = sanitizeName(tag.getString("interfererOwnerName"));
        whitelist.clear();
        ListTag entries = tag.getList("interfererWhitelist", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size() && whitelist.size() < MAX_WHITELIST; index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.hasUUID("id")) continue;
            String name = sanitizeName(entry.getString("name"));
            if (!name.isEmpty()) whitelist.put(entry.getUUID("id"), name);
        }
        if (owner != null) whitelist.put(owner, ownerName);

        ItemStack battery = getItems().get(BATTERY_SLOT);
        if (!battery.isEmpty()) {
            if (!EnergyItemHelper.isEnergyItem(battery)) getItems().set(BATTERY_SLOT, ItemStack.EMPTY);
            else if (battery.getCount() > 1) battery.setCount(1);
        }
        // Paid intervals are intentionally not deserialized.
        activeUntil = -1;
        nextPaymentTick = Long.MIN_VALUE;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("interfererEnergy", energy);
        tag.putInt("interfererRange", range);
        tag.putBoolean("interfererEnabled", enabled);
        if (owner != null) tag.putUUID("interfererOwner", owner);
        if (!ownerName.isEmpty()) tag.putString("interfererOwnerName", ownerName);
        ListTag entries = new ListTag();
        getWhitelistEntries().stream().limit(MAX_WHITELIST).forEach(value -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", value.id());
            entry.putString("name", value.name());
            entries.add(entry);
        });
        tag.put("interfererWhitelist", entries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putInt("interfererEnergy", energy);
        tag.putInt("interfererRange", range);
        tag.putBoolean("interfererEnabled", enabled);
        return tag;
    }

    @Override
    public void setRemoved() {
        AbilityInterferenceService.remove(this);
        super.setRemoved();
    }

    private static String sanitizeName(String value) {
        if (value == null) return "";
        String stripped = value.strip();
        return stripped.substring(0, Math.min(stripped.length(), MAX_PLAYER_NAME));
    }
}
