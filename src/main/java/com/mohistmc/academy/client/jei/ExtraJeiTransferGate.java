package com.mohistmc.academy.client.jei;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.gametest.ExtraRecipeManifest;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mohistmc.academy.world.menu.MetalFomerMenu;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

/**
 * Explicitly enabled integrated-client JEI transfer acceptance, separate from page rendering.
 * Fixtures supply the survival player's inventory and open actual server menus. All recipe
 * inputs subsequently move only through JEI's registered handler and its real network packet.
 * API-driven transfer is not a physical mouse-click claim; output execution has separate GTs.
 */
@JeiPlugin
@OnlyIn(Dist.CLIENT)
public final class ExtraJeiTransferGate implements IModPlugin {
    public static final String PROPERTY = "academy.extraJeiTransferGate";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "extra_jei_transfer_gate");
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (Boolean.getBoolean(PROPERTY)) NeoForge.EVENT_BUS.addListener(new Driver(runtime)::tick);
    }

    private enum Stage { WORLD, PREPARE, WAIT_PREPARE, MENU, PAGE, WAIT_DRY_CHECK, WAIT_TRANSFER, WAIT_SERVER_CHECK, CAPTURE, FINISHED }
    private enum Kind { CRAFTING, FUSOR, METAL }
    private record Spec(String id, Kind kind, Map<Integer, ItemStack> slots, List<ItemStack> supply, boolean missingRedstone) {}

    private static final class Driver {
        private final IJeiRuntime runtime;
        private final List<String> recipes;
        private final List<String> evidence = new ArrayList<>();
        private Stage stage = Stage.WORLD;
        private int ticks, age, worldTicks, index, transferred;
        private Spec spec;
        private IRecipeLayoutDrawable<?> layout;
        private AbstractContainerScreen<?> menuScreen;
        private volatile boolean busy, captureDone;
        private volatile String failure, serverLine;
        private volatile int serverContainerId;
        private String captureName, handlerName;
        private boolean captureRequested;

        Driver(IJeiRuntime runtime) {
            this.runtime = runtime;
            // Exercise all three registered menu families before the remaining recipes.
            var ordered = new LinkedHashSet<String>();
            ordered.add("academy:extra_paper_plane");
            ordered.add("academy:extra_fuse_crystal_low");
            ordered.add("academy:extra_etched_cobblestone");
            ordered.addAll(ExtraRecipeManifest.ALL);
            recipes = List.copyOf(ordered);
        }

        void tick(ClientTickEvent.Post ignored) {
            if (stage == Stage.FINISHED) return;
            Minecraft mc = Minecraft.getInstance();
            ticks++; age++;
            try {
                require(ticks < 20 * 360, "overall timeout");
                if (failure != null) throw new IllegalStateException(failure);
                if (stage == Stage.WORLD) {
                    if (mc.level == null || mc.player == null || mc.getOverlay() != null || mc.getSingleplayerServer() == null) return;
                    if (++worldTicks < 40) return;
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("ISOLATED-ACCEPTANCE")),
                            "isolated game directory requires ISOLATED-ACCEPTANCE marker");
                    require(!Boolean.getBoolean("academy.extraJeiGate") && !Boolean.getBoolean("academy.machineVisualGate")
                            && !Boolean.getBoolean("academy.extraSkillVisualGate"), "run transfer gate separately from other GUI drivers");
                    log("fixture=isolated integrated survival player; supply inventory and open menu only; no server placement into recipe slots");
                    log("transfer=JEI registered IRecipeTransferHandler, maxTransfer=false, doTransfer=true; real JEI client-to-server packet; no physical mouse-click claim");
                    log("fusor observation fixture=full 8000mB tank and zero energy; stops automatic liquid-unit conversion/recipe execution so slots36/37/38 remain observable");
                    enter(Stage.PREPARE); return;
                }
                require(mc.level != null && mc.player != null && mc.getSingleplayerServer() != null, "integrated world/player vanished");
                require(age < 20 * 30, "stage timeout " + stage + " recipe=" + (spec == null ? index : spec.id()));
                switch (stage) {
                    case PREPARE -> {
                        boolean negative = index == recipes.size();
                        String id = negative ? "academy:extra_fuse_crystal_low" : recipes.get(index);
                        // The server fixture below owns the ordered close/open sequence.
                        layout = createLayout(id);
                        spec = createSpec(id, layout, negative);
                        serverLine = null;
                        server(mc, player -> prepare(player, spec));
                        enter(Stage.WAIT_PREPARE);
                    }
                    case WAIT_PREPARE -> { if (!busy) enter(Stage.MENU); }
                    case MENU -> {
                        if (age < 15 || !correctMenu(mc.player.containerMenu, spec.kind())
                                || mc.player.containerMenu.containerId != serverContainerId
                                || !(mc.screen instanceof AbstractContainerScreen<?> screen)
                                || screen.getMenu() != mc.player.containerMenu
                                || inventoryCount(mc.player.getInventory().items) != inventoryCount(spec.supply())) return;
                        menuScreen = screen;
                        showLayout();
                        enter(Stage.PAGE);
                    }
                    case PAGE -> {
                        if (age < 12) return;
                        require(mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."), "actual JEI recipe page absent");
                        IRecipeTransferError dryError = transfer(mc, false);
                        if (spec.missingRedstone()) require(dryError != null, "one redstone unexpectedly passed JEI dry-run for two-redstone recipe");
                        else require(dryError == null, "JEI dry-run rejected recipe: " + error(dryError));
                        log("DRY_RUN " + spec.id() + " missingRedstone=" + spec.missingRedstone() + " result=" + error(dryError)
                                + " handler=" + handlerName + " screen=" + mc.screen.getClass().getName());
                        server(mc, player -> assertBeforeTransfer(player, spec));
                        enter(Stage.WAIT_DRY_CHECK);
                    }
                    case WAIT_DRY_CHECK -> {
                        if (busy) return;
                        IRecipeTransferError result = transfer(mc, true);
                        if (spec.missingRedstone()) require(result != null, "one-redstone actual transfer unexpectedly accepted");
                        else require(result == null, "JEI actual transfer rejected recipe: " + error(result));
                        log("TRANSFER_CALL " + spec.id() + " doTransfer=true maxTransfer=false result=" + error(result) + " handler=" + handlerName);
                        mc.setScreen(menuScreen);
                        enter(Stage.WAIT_TRANSFER);
                    }
                    case WAIT_TRANSFER -> {
                        if (age < 20) return; // Allow the actual network packet and container updates to cross threads.
                        server(mc, player -> {
                            if (spec.missingRedstone()) {
                                assertBeforeTransfer(player, spec);
                                serverLine = "EXTRA_JEI_TRANSFER_BOUNDARY_PASS one-redstone-for-two-redstone-recipe rejected; inventory and all machine slots unchanged";
                            } else serverLine = assertTransferred(player, spec);
                        });
                        enter(Stage.WAIT_SERVER_CHECK);
                    }
                    case WAIT_SERVER_CHECK -> {
                        if (busy) return;
                        require(serverLine != null, "server verdict absent");
                        log(serverLine);
                        if (!spec.missingRedstone()) transferred++;
                        captureName = String.format(java.util.Locale.ROOT, "extra-jei-transfer-%02d-%s%s.png", index + 1,
                                spec.id().substring(spec.id().indexOf(':') + 1), spec.missingRedstone() ? "-missing-redstone" : "");
                        captureRequested = false; captureDone = false;
                        enter(Stage.CAPTURE);
                    }
                    case CAPTURE -> {
                        if (age < 6) return;
                        require(mc.screen == menuScreen, "actual target menu screen was replaced before transfer capture");
                        if (!captureRequested) {
                            require(!Files.exists(mc.gameDirectory.toPath().resolve("screenshots").resolve(captureName)), "capture already exists; use fresh isolated directory");
                            captureRequested = true;
                            Screenshot.grab(mc.gameDirectory, captureName, mc.getMainRenderTarget(), message -> captureDone = true);
                            return;
                        }
                        if (!captureDone) return;
                        require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(captureName)), "screenshot callback did not create capture");
                        log("CAPTURE " + captureName + " menu=" + menuScreen.getMenu().getClass().getName());
                        index++;
                        if (index > recipes.size()) {
                            require(transferred == ExtraRecipeManifest.ALL.size(), "not all 29 recipes transferred");
                            finish(mc, "PASS", null);
                        } else enter(Stage.PREPARE);
                    }
                    default -> { }
                }
            } catch (Throwable problem) {
                LOGGER.error("Extra JEI transfer gate failed at {} recipe {}", stage, spec == null ? index : spec.id(), problem);
                finish(mc, "FAIL", problem.toString());
            }
        }

        private IRecipeLayoutDrawable<?> createLayout(String id) {
            if (ExtraRecipeManifest.CRAFTING.contains(id)) return lookup(RecipeTypes.CRAFTING, id);
            if (ExtraRecipeManifest.IMAG_FUSOR.contains(id)) return lookup(AcademyJeiPlugin.IMAG_FUSING, id);
            return lookup(AcademyJeiPlugin.METAL_FORMING, id);
        }

        private <R extends RecipeHolder<?>> IRecipeLayoutDrawable<R> lookup(RecipeType<R> type, String id) {
            var manager = runtime.getRecipeManager();
            R recipe = manager.createRecipeLookup(type).get().filter(holder -> holder.id().equals(ResourceLocation.parse(id)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("JEI recipe absent " + id));
            var category = manager.getRecipeCategory(type);
            require(category != null, "JEI category absent " + id);
            return manager.createRecipeLayoutDrawable(category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
                    .orElseThrow(() -> new IllegalStateException("JEI layout absent " + id));
        }

        private Spec createSpec(String id, IRecipeLayoutDrawable<?> drawable, boolean negative) {
            var inputs = drawable.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
            var expected = new LinkedHashMap<Integer, ItemStack>();
            Kind kind;
            if (ExtraRecipeManifest.CRAFTING.contains(id)) {
                kind = Kind.CRAFTING;
                require(inputs.size() == 9, "JEI crafting layout must expose 3x3 input slots; got " + inputs.size());
                for (int i = 0; i < 9; i++) expected.put(i + 1, inputs.get(i).getItemStacks().findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY));
            } else if (ExtraRecipeManifest.IMAG_FUSOR.contains(id)) {
                kind = Kind.FUSOR;
                require(inputs.size() == 2, "fusor display must expose exactly crystal and liquid input");
                var recipe = (ImagFusorRecipe) ((RecipeHolder<?>) drawable.getRecipe()).value();
                ItemStack crystal = inputs.get(0).getItemStacks().findFirst().orElseThrow().copy();
                ItemStack liquid = inputs.get(1).getItemStacks().findFirst().orElseThrow().copy();
                require(crystal.getCount() == recipe.inputCount() && recipe.input().test(crystal), "JEI crystal display count/item disagrees with recipe");
                require(liquid.getCount() == (recipe.phaseLiquid() + 999) / 1000, "JEI phase-liquid display count disagrees with recipe");
                expected.put(36, liquid); expected.put(37, ItemStack.EMPTY); expected.put(38, crystal);
                expected.put(39, ItemStack.EMPTY); expected.put(40, ItemStack.EMPTY);
                if (negative) require(crystal.is(Items.REDSTONE) && crystal.getCount() == 2, "negative case must require exactly two redstone");
            } else {
                kind = Kind.METAL;
                require(inputs.size() == 1, "metal former must have exactly one recipe input");
                var recipe = (MetalFormingRecipe) ((RecipeHolder<?>) drawable.getRecipe()).value();
                ItemStack input = inputs.getFirst().getItemStacks().findFirst().orElseThrow().copy();
                require(input.getCount() == recipe.getInputCount(), "JEI metal input count disagrees with recipe");
                expected.put(36, input); expected.put(37, ItemStack.EMPTY); expected.put(38, ItemStack.EMPTY);
            }
            var supply = new ArrayList<ItemStack>();
            for (ItemStack stack : expected.values()) {
                if (stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                if (negative && copy.is(Items.REDSTONE)) copy.setCount(1);
                supply.add(copy);
            }
            return new Spec(id, kind, Map.copyOf(expected), List.copyOf(supply), negative);
        }

        private void showLayout() { showLayout(layout); }
        private <R> void showLayout(IRecipeLayoutDrawable<R> current) {
            runtime.getRecipesGui().showRecipes(current.getRecipeCategory(), List.of(current.getRecipe()), List.of());
        }

        private IRecipeTransferError transfer(Minecraft mc, boolean perform) { return transfer(mc, layout, perform); }
        private <R> IRecipeTransferError transfer(Minecraft mc, IRecipeLayoutDrawable<R> current, boolean perform) {
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(mc.player.containerMenu, current.getRecipeCategory())
                    .orElseThrow(() -> new IllegalStateException("registered JEI transfer handler absent " + spec.id()));
            handlerName = handler.getClass().getName();
            return handler.transferRecipe(mc.player.containerMenu, current.getRecipe(), current.getRecipeSlotsView(), mc.player, false, perform);
        }

        private void prepare(ServerPlayer player, Spec current) {
            var level = player.serverLevel();
            // Keep close/open on this server task and its ordered outbound connection. A client
            // close packet queued in PREPARE could arrive after openMenu and close the new menu:
            // vanilla handleContainerClose does not check the packet container id.
            player.closeContainer();
            player.getInventory().clearContent();
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO);
            BlockPos pos = new BlockPos(96 + index * 8, 180, 96);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) {
                level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            var state = switch (current.kind()) {
                case CRAFTING -> Blocks.CRAFTING_TABLE.defaultBlockState();
                case FUSOR -> AcademyBlocks.IMAG_FUSOR.get().defaultBlockState();
                case METAL -> AcademyBlocks.METAL_FORMER.get().defaultBlockState();
            };
            level.setBlock(pos, state, 3);
            if (current.kind() == Kind.FUSOR) {
                var fusor = (ImagFusorBlockEntity) level.getBlockEntity(pos);
                require(fusor != null && fusor.getEnergy() == 0, "fresh fusor missing or powered");
                int filled = fusor.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), fusor.getMaxFluid()), IFluidHandler.FluidAction.EXECUTE);
                require(filled == 8000, "could not freeze liquid conversion with full fixture tank");
            }
            for (ItemStack stack : current.supply()) require(player.getInventory().add(stack.copy()), "fixture inventory could not accept supply");
            player.getInventory().setChanged();
            player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + 2.5, 180, 0);
            var provider = state.getMenuProvider(level, pos);
            require(provider != null, "fixture block has no real menu provider");
            if (current.kind() == Kind.CRAFTING) player.openMenu(provider);
            else player.openMenu(provider, pos);
            require(correctMenu(player.containerMenu, current.kind()), "server opened wrong menu: " + menuDiagnostic(player, current));
            require(player.containerMenu.stillValid(player), "fresh server menu is invalid: " + menuDiagnostic(player, current));
            serverContainerId = player.containerMenu.containerId;
            player.containerMenu.broadcastChanges();
            assertBeforeTransfer(player, current);
        }

        private String menuDiagnostic(ServerPlayer player, Spec current) {
            var menu = player.containerMenu;
            return "recipe=" + current.id() + " expectedKind=" + current.kind()
                    + " expectedContainerId=" + serverContainerId + " actualMenu=" + menu.getClass().getName()
                    + " actualContainerId=" + menu.containerId + " valid=" + menu.stillValid(player)
                    + " playerPosition=" + player.position() + " dimension=" + player.serverLevel().dimension().location();
        }

        private void assertBeforeTransfer(ServerPlayer player, Spec current) {
            var menu = player.containerMenu;
            require(correctMenu(menu, current.kind()) && menu.containerId == serverContainerId && menu.stillValid(player), "server menu changed/invalid: " + menuDiagnostic(player, current));
            for (int slot : current.slots().keySet()) require(menu.getSlot(slot).getItem().isEmpty(), "JEI dry-run/negative transfer changed slot " + slot);
            require(menu.getCarried().isEmpty(), "carried stack changed during dry-run/negative transfer");
            require(multiset(player.getInventory().items).equals(multiset(current.supply())), "dry-run/negative transfer changed supplied inventory");
        }

        private String assertTransferred(ServerPlayer player, Spec current) {
            var menu = player.containerMenu;
            require(correctMenu(menu, current.kind()) && menu.containerId == serverContainerId && menu.stillValid(player), "transfer target menu changed/invalid: " + menuDiagnostic(player, current));
            var detail = new StringBuilder();
            current.slots().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ItemStack actual = menu.getSlot(entry.getKey()).getItem();
                require(equalStack(actual, entry.getValue()), "server slot " + entry.getKey() + " expected " + describe(entry.getValue()) + " got " + describe(actual));
                detail.append(entry.getKey()).append('=').append(describe(actual)).append(';');
            });
            require(player.getInventory().items.stream().allMatch(ItemStack::isEmpty), "JEI failed exact inventory debit; remainder=" + multiset(player.getInventory().items));
            require(menu.getCarried().isEmpty(), "JEI transfer unexpectedly used carried stack");
            if (current.kind() == Kind.CRAFTING) {
                var grid = new ArrayList<ItemStack>();
                for (int slot = 1; slot <= 9; slot++) grid.add(menu.getSlot(slot).getItem().copy());
                var input = CraftingInput.of(3, 3, grid);
                var actualRecipe = player.serverLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, player.serverLevel()).orElseThrow();
                require(actualRecipe.id().equals(ResourceLocation.parse(current.id())), "transferred crafting grid matches wrong recipe " + actualRecipe.id());
                require(equalStack(menu.getSlot(0).getItem(), actualRecipe.value().assemble(input, player.registryAccess())), "crafting result preview differs from matched recipe");
            } else if (current.kind() == Kind.FUSOR) {
                var fusor = (ImagFusorBlockEntity) player.serverLevel().getBlockEntity(((ImagFusorMenu) menu).getPos());
                require(fusor != null && fusor.getFluidAmount() == 8000 && fusor.getEnergy() == 0 && fusor.getProcessingTime() == 0, "fusor fixture advanced during transfer observation");
            }
            return "EXTRA_JEI_TRANSFER_PASS " + current.id() + " menu=" + menu.getClass().getSimpleName() + " containerId=" + menu.containerId + " slots=" + detail + " inventoryRemaining=0 carried=empty";
        }

        private void server(Minecraft mc, Consumer<ServerPlayer> action) {
            require(!busy, "overlapping server fixture/check actions"); busy = true;
            UUID playerId = mc.player.getUUID(); var integrated = mc.getSingleplayerServer();
            require(integrated != null, "integrated server absent");
            integrated.execute(() -> {
                try {
                    ServerPlayer player = integrated.getPlayerList().getPlayer(playerId);
                    require(player != null, "server player absent"); action.accept(player);
                } catch (Throwable problem) { failure = problem.toString(); LOGGER.error("JEI transfer server operation failed", problem); }
                finally { busy = false; }
            });
        }

        private void enter(Stage next) { stage = next; age = 0; }
        private void log(String line) { evidence.add(line); LOGGER.info("{}", line); }
        private void finish(Minecraft mc, String status, String reason) {
            stage = Stage.FINISHED;
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("academy-extra-jei-transfer-result.txt"),
                        status + "\nTransferred=" + transferred + "/" + ExtraRecipeManifest.ALL.size() + "\nCompletedCases=" + index
                                + "/" + (recipes.size() + 1) + "\nFailure=" + reason + "\n" + String.join("\n", evidence) + "\n",
                        StandardOpenOption.CREATE_NEW);
            } catch (Exception problem) { LOGGER.error("Cannot write JEI transfer gate result", problem); }
            mc.stop();
        }
    }

    private static boolean correctMenu(AbstractContainerMenu menu, Kind kind) {
        return switch (kind) { case CRAFTING -> menu instanceof CraftingMenu; case FUSOR -> menu instanceof ImagFusorMenu; case METAL -> menu instanceof MetalFomerMenu; };
    }
    private static boolean equalStack(ItemStack actual, ItemStack expected) {
        return actual.isEmpty() && expected.isEmpty() || actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }
    private static int inventoryCount(List<ItemStack> stacks) { return stacks.stream().mapToInt(ItemStack::getCount).sum(); }
    private static Map<String, Integer> multiset(List<ItemStack> stacks) {
        var result = new LinkedHashMap<String, Integer>();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + ":" + stack.getComponents(), stack.getCount(), Integer::sum);
        return result;
    }
    private static String describe(ItemStack stack) { return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount(); }
    private static String error(IRecipeTransferError error) { return error == null ? "none" : error.getClass().getName() + "/" + error.getType() + "/" + error.getTooltip(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
