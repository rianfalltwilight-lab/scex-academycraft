package com.mohistmc.academy.client.gate;

import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.IEnergyItem;
import com.mohistmc.academy.world.item.ExtraItemData;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Default-off, isolated real renderer probe. These are client-side display fixtures:
 * no claim of survival acquisition, server equipment changes or physical keyboard input.
 * Screenshots require a separate human/model visual review; existence alone is not fidelity.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ExtraItemTextureVisualGate {
    public static final String PROPERTY = "academy.extraItemTextureVisualGate";
    private static final List<String> ITEMS = List.of("optical_chip", "lasor_component", "etched_cobblestone",
            "ray_twister", "energy_unit_group", "electricalibur", "avalon", "cp_potion", "lasor_gun",
            "air_jet", "teleporter", "paper_plane", "drop_item_magnet",
            "reso_helmet", "reso_chestplate", "reso_leggings", "reso_boots",
            "imag_helmet", "imag_chestplate", "imag_leggings", "imag_boots",
            "paper_helmet", "paper_chestplate", "paper_leggings", "paper_boots");
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_PARTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static final List<List<Sample>> PAGES = new ArrayList<>();
    private static final List<ArmorStand> ARMOR = new ArrayList<>();
    private static final String[] PAGE_NAMES = {"items", "armor-icons", "states-and-factors", "worn-front", "worn-back"};
    private static final String[] ARMOR_NAMES = {"Resonance", "Paper", "Imag 0 IF", "Imag 500 IF"};
    private static int ticks, page, age, renders;
    private static boolean initialized, captured, finished;
    private static volatile boolean screenshotDone;
    private static volatile String failure;
    private static Gallery screen;
    private record Sample(String label, ItemStack stack, ResourceLocation sprite) {}
    private ExtraItemTextureVisualGate() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(PROPERTY) || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 20 * 180) throw new IllegalStateException("texture gate timeout");
            if (failure != null) throw new IllegalStateException(failure);
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (!initialized) {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("ISOLATED-ACCEPTANCE")))
                    throw new IllegalStateException("isolated game directory marker required");
                if (Files.exists(mc.gameDirectory.toPath().resolve("academy-extra-item-textures-result.txt")))
                    throw new IllegalStateException("new evidence directory required");
                verifyOriginalResources(mc);
                buildSamples(mc);
                buildArmor(mc);
                initialized = true;
                openPage(mc);
                return;
            }
            if (mc.screen != screen) throw new IllegalStateException("gallery screen unexpectedly replaced");
            if (++age < 25 || renders < 3) return;
            String name = String.format("extra-item-textures-%02d-%s.png", page + 1, PAGE_NAMES[page]);
            if (!captured) {
                captured = true; screenshotDone = false;
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> screenshotDone = true);
                return;
            }
            if (!screenshotDone) return;
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)))
                throw new IllegalStateException("screenshot write missing: " + name);
            record("CAPTURE " + name + " realRenderFrames=" + renders + " requiresIndependentVisualReview=true");
            if (++page == PAGE_NAMES.length) { finish("PASS", mc); return; }
            openPage(mc);
        } catch (Throwable problem) { finish("FAIL " + problem, mc); }
    }

    private static void verifyOriginalResources(Minecraft mc) throws Exception {
        var manifestId = ResourceLocation.fromNamespaceAndPath("academy", "extraacc-provenance.json");
        try (var reader = mc.getResourceManager().getResourceOrThrow(manifestId).openAsReader()) {
            var assets = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("assets");
            if (assets.size() != 42) throw new IllegalStateException("expected 42 original PNGs");
            for (var value : assets) {
                var asset = value.getAsJsonObject();
                String destination = asset.get("destination").getAsString();
                var id = ResourceLocation.fromNamespaceAndPath("academy", destination.substring("assets/academy/".length()));
                try (var stream = mc.getResourceManager().getResourceOrThrow(id).open()) {
                    String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                    if (!actual.equalsIgnoreCase(asset.get("sha256").getAsString()))
                        throw new IllegalStateException("loaded resource differs from original: " + id);
                }
                record("RESOURCE_PASS " + id + " sha256=" + asset.get("sha256").getAsString());
            }
        }
    }

    private static ItemStack stack(String id) {
        var key = ResourceLocation.fromNamespaceAndPath("academy", id);
        var item = BuiltInRegistries.ITEM.getOptional(key).orElseThrow(() -> new IllegalStateException("item absent " + key));
        ItemStack result = new ItemStack(item);
        if (item instanceof IEnergyItem energy) energy.setEnergy(result, 0);
        return result;
    }

    private static Sample sample(String label, ItemStack stack, String sprite) {
        return new Sample(label, stack, ResourceLocation.fromNamespaceAndPath("academy", "item/" + sprite));
    }

    private static void buildSamples(Minecraft mc) {
        List<Sample> base = new ArrayList<>();
        for (String id : ITEMS) {
            String texture = id.equals("energy_unit_group") ? "energy_unit_group_0" : id;
            base.add(sample(id, stack(id), "extraacc/" + texture));
        }
        PAGES.add(List.copyOf(base.subList(0, 13)));
        PAGES.add(List.copyOf(base.subList(13, 25)));
        List<Sample> states = new ArrayList<>();
        for (int level = 0; level <= 5; level++) {
            ItemStack unit = stack("energy_unit_group");
            ((IEnergyItem)unit.getItem()).setEnergy(unit, level * 10_000);
            states.add(sample("EUG " + level * 10_000 + " IF", unit, "extraacc/energy_unit_group_" + level));
        }
        for (boolean active : new boolean[]{false, true}) {
            ItemStack ray = stack("ray_twister");
            ExtraItemData.setActive(ray, active);
            states.add(sample("Ray " + (active ? "active" : "off"), ray, "extraacc/ray_twister" + (active ? "_active" : "")));
            ItemStack teleporter = stack("teleporter");
            if (active) ExtraItemData.setTeleport(teleporter, ResourceLocation.withDefaultNamespace("overworld"), 1, 64, 1);
            states.add(sample("Teleporter " + (active ? "bound" : "unset"), teleporter, "extraacc/teleporter" + (active ? "_pos" : "")));
        }
        states.add(sample("factor_aerohand", stack("factor_aerohand"), "factor_aerohand"));
        states.add(sample("factor_telekinesis", stack("factor_telekinesis"), "factor_telekinesis"));
        PAGES.add(List.copyOf(states));
        for (var samples : PAGES) for (Sample value : samples) verifyModel(mc, value);
        for (int transition = 1; transition <= 5; transition++) {
            for (int below = 0; below <= 1; below++) {
                int energy = transition * 10_000 - 5_000 - below;
                int expected = below == 1 ? transition - 1 : transition;
                ItemStack unit = stack("energy_unit_group");
                ((IEnergyItem)unit.getItem()).setEnergy(unit, energy);
                verifyModel(mc, sample("EUG boundary " + energy, unit, "extraacc/energy_unit_group_" + expected));
            }
        }
    }

    private static void verifyModel(Minecraft mc, Sample sample) {
        var model = mc.getItemRenderer().getModel(sample.stack, mc.level, mc.player, 0);
        var actual = model.getParticleIcon().contents().name();
        if (model == mc.getModelManager().getMissingModel() || !actual.equals(sample.sprite))
            throw new IllegalStateException(sample.label + " wrong/missing baked sprite: " + actual + " expected=" + sample.sprite);
        int quads = model.getQuads(null, null, RandomSource.create(0)).size();
        for (Direction face : Direction.values()) quads += model.getQuads(null, face, RandomSource.create(0)).size();
        if (quads == 0) throw new IllegalStateException("no baked item geometry: " + sample.label);
        record("MODEL_PASS " + sample.label + " sprite=" + actual + " bakedQuads=" + quads);
    }

    private static void buildArmor(Minecraft mc) {
        for (int set = 0; set < 4; set++) {
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            stand.setNoGravity(true);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            String family = set == 0 ? "reso" : set == 1 ? "paper" : "imag";
            String texture = set == 0 ? "reso" : set == 1 ? "paper" : set == 2 ? "noenergy" : "energy";
            for (int part = 0; part < 4; part++) {
                ItemStack piece = stack(family + "_" + ARMOR_PARTS[part]);
                if (piece.getItem() instanceof IEnergyItem energy) energy.setEnergy(piece, set == 3 ? 500 : 0);
                stand.setItemSlot(ARMOR_SLOTS[part], piece);
                verifyArmor(mc, stand, piece, ARMOR_SLOTS[part], texture);
                if (set == 2) {
                    ((IEnergyItem)piece.getItem()).setEnergy(piece, 499);
                    verifyArmor(mc, stand, piece, ARMOR_SLOTS[part], "noenergy");
                    ((IEnergyItem)piece.getItem()).setEnergy(piece, 500);
                    verifyArmor(mc, stand, piece, ARMOR_SLOTS[part], "energy");
                    ((IEnergyItem)piece.getItem()).setEnergy(piece, 0);
                }
            }
            // Real held-item layers also exercise the restored third-person model transforms.
            if (set == 0) stand.setItemSlot(EquipmentSlot.MAINHAND, stack("electricalibur"));
            if (set == 1) stand.setItemSlot(EquipmentSlot.MAINHAND, stack("lasor_gun"));
            ARMOR.add(stand);
        }
    }

    private static void verifyArmor(Minecraft mc, ArmorStand wearer, ItemStack stack,
                                    EquipmentSlot slot, String texture) {
        var material = ((ArmorItem)stack.getItem()).getMaterial().value();
        if (material.layers().size() != 1) throw new IllegalStateException("unexpected armor layer count");
        var actual = ClientHooks.getArmorTexture(wearer, stack, material.layers().getFirst(), slot == EquipmentSlot.LEGS, slot);
        var expected = ResourceLocation.fromNamespaceAndPath("academy", "textures/models/armor/extraacc/"
                + texture + "_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png");
        if (!actual.equals(expected) || mc.getResourceManager().getResource(actual).isEmpty())
            throw new IllegalStateException("wrong/missing worn armor texture " + actual + " expected=" + expected);
        record("ARMOR_TEXTURE_PASS " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + actual);
    }

    private static void openPage(Minecraft mc) {
        age = 0; renders = 0; captured = false;
        screen = new Gallery();
        mc.setScreen(screen);
    }

    private static void record(String line) {
        EVIDENCE.add(line);
        LogUtils.getLogger().info("EXTRA_ITEM_TEXTURE {}", line);
    }

    private static void finish(String result, Minecraft mc) {
        if (finished) return;
        finished = true;
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-extra-item-textures-result.txt"),
                    result + "\nfixture=client-only item stacks and equipped ArmorStand renderers; not survival/server gameplay\n"
                            + "renderedPages=" + page + "/5\n" + String.join("\n", EVIDENCE) + "\n",
                    StandardOpenOption.CREATE_NEW);
        } catch (Exception problem) { LogUtils.getLogger().error("Could not persist texture gate result", problem); }
        mc.stop();
    }

    private static final class Gallery extends Screen {
        private Gallery() { super(Component.literal("ExtraAcC original item textures")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }

        @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            if (finished) return;
            try {
                gui.fill(0, 0, width, height, 0xff17202a);
                gui.drawCenteredString(font, "ExtraAcC original textures / " + PAGE_NAMES[page], width / 2, 8, 0xffffff);
                gui.drawCenteredString(font, "Actual Minecraft renderers; isolated display fixtures", width / 2, 21, 0xaec5d8);
                if (page < 3) renderItems(gui);
                else renderArmor(gui, page == 4);
                gui.drawCenteredString(font, "Visual review required. No gameplay/persistence claim.", width / 2, height - 13, 0xaec5d8);
                renders++;
            } catch (Throwable problem) {
                failure = problem.toString();
                gui.drawString(font, "RENDER FAILED: " + problem, 8, 40, 0xff5555, false);
            }
        }

        private void renderItems(GuiGraphics gui) {
            List<Sample> samples = PAGES.get(page);
            int columns = page == 0 ? 5 : 4;
            int rows = (samples.size() + columns - 1) / columns;
            int cellWidth = (width - 20) / columns;
            int cellHeight = (height - 62) / rows;
            float itemScale = Math.max(1, Math.min(3, (cellHeight - 21) / 16F));
            for (int i = 0; i < samples.size(); i++) {
                Sample value = samples.get(i);
                int x = 10 + (i % columns) * cellWidth, y = 36 + (i / columns) * cellHeight;
                gui.fill(x + 2, y + 1, x + cellWidth - 2, y + cellHeight - 2, 0xff243342);
                gui.pose().pushPose();
                gui.pose().translate(x + (cellWidth - 16 * itemScale) / 2, y + 3, 0);
                gui.pose().scale(itemScale, itemScale, 1);
                gui.renderItem(value.stack, 0, 0);
                gui.renderItemDecorations(font, value.stack, 0, 0);
                gui.pose().popPose();
                float textScale = Math.min(1, (cellWidth - 6F) / Math.max(1, font.width(value.label)));
                gui.pose().pushPose();
                gui.pose().translate(x + cellWidth / 2F, y + cellHeight - 13, 0);
                gui.pose().scale(textScale, textScale, 1);
                gui.drawCenteredString(font, value.label, 0, 0, 0xffffff);
                gui.pose().popPose();
            }
        }

        private void renderArmor(GuiGraphics gui, boolean back) {
            int cell = width / 4;
            float scale = Math.min((height - 82) / 2.1F, (cell - 8) * .9F);
            for (int i = 0; i < ARMOR.size(); i++) {
                ArmorStand stand = ARMOR.get(i);
                float yaw = back ? 0 : 180;
                stand.yBodyRot = stand.yBodyRotO = yaw;
                stand.yHeadRot = stand.yHeadRotO = yaw;
                stand.setYRot(yaw);
                gui.drawCenteredString(font, ARMOR_NAMES[i], i * cell + cell / 2, 39, 0xffffff);
                InventoryScreen.renderEntityInInventory(gui, i * cell + cell / 2F, height - 30, scale,
                        new Vector3f(), new Quaternionf().rotationZ((float)Math.PI), null, stand);
            }
        }
    }
}

