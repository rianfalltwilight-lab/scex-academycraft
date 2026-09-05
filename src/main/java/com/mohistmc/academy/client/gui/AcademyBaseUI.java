package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.AcademyNode;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.ConnectNodeToMatrixPacket;
import com.mohistmc.academy.network.DisconnectFromNodePacket;
import com.mohistmc.academy.network.DisconnectNodeFromMatrixPacket;
import com.mohistmc.academy.network.MatrixNetworkListSyncPacket;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.network.NodeListSyncPacket;
import com.mohistmc.academy.network.RequestMatrixNetworksPacket;
import com.mohistmc.academy.network.RequestNodesPacket;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import com.mohistmc.academy.world.menu.AcademyMenu;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class AcademyBaseUI<T extends AcademyMenu> extends AbstractContainerScreen<T> {

    // ==================== UI 状态枚举 ====================

    /** 无线 UI 面板状态 */
    public enum WirelessState {
        /** 默认背包页面 */
        DEFAULT,
        /** 节点列表面板（发电机/耗能设备 连接节点用） */
        WIFI,
        /** 节点连接矩阵网络的页面（1.0.7 WirelessPage.nodePage） */
        NODE
    }

    // ==================== 常量 ====================

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 187;

    private static final ResourceLocation PARENT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/parent/parent_background.png");
    private static final ResourceLocation UI_INVENTORY = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_inventory.png");
    private static final ResourceLocation DEV_PARENT_LEFT = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/parent/parent_background_developerleft.png");
    private static final ResourceLocation DEV_PARENT_MACHINE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/parent/parent_background_developermachine.png");
    private static final ResourceLocation DEV_PARENT_RIGHT = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/parent/parent_background_developerright.png");
    private static final ResourceLocation DEV_UI_LEFT = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_developerleft.png");
    private static final ResourceLocation DEV_UI_RIGHT = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_developerright.png");

    private static final ResourceLocation IC_INV = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_inv.png");
    private static final ResourceLocation IC_WIRELESS = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_wireless.png");
    private static final ResourceLocation IC_TONODE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_tonode.png");
    private static final ResourceLocation IC_TOMATRIX = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_tomatrix.png");
    private static final ResourceLocation IC_MATRIX = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_matrix.png");
    private static final ResourceLocation IC_UNCONNECTED = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_unconnected.png");
    private static final ResourceLocation IC_CONNECTED = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_connected.png");
    private static final ResourceLocation IC_KEY = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_key.png");
    private static final ResourceLocation ELEMENT_BG_300_32 = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/element/element_background300x32.png");
    private static final ResourceLocation ELEMENT_BG_300_32_I = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/element/element_background300x32_input.png");
    private static final ResourceLocation BTN_ARROW_UP = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/button/button_arrowupb.png");
    private static final ResourceLocation BTN_ARROW_DOWN = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/button/button_arrowdownb.png");

    // ==================== 静态节点列表缓存（从 server 同步） ====================

    /** 最近一次收到的节点列表 NBT */
    private static CompoundTag pendingNodeData = null;
    /** 最近一次收到的矩阵网络列表 NBT */
    private static CompoundTag pendingMatrixNetworkData = null;

    /**
     * 由 NodeListSyncPacket 调用，在渲染线程安全读取。
     */
    public static void receiveNodeList(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AcademyBaseUI<?> screen) || screen.menu.pos == null) return;
        if (!data.contains("machinePos") || !data.contains("containerId")
                || data.getLong("machinePos") != screen.menu.pos.asLong()
                || data.getInt("containerId") != screen.menu.containerId) return;
        pendingNodeData = data.copy();
    }

    /** Correlated receiver for the node-to-Matrix page. */
    public static void receiveMatrixNetworkList(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AcademyBaseUI<?> screen) || screen.menu.pos == null) return;
        if (!data.contains("nodePos") || !data.contains("containerId")
                || data.getLong("nodePos") != screen.menu.pos.asLong()
                || data.getInt("containerId") != screen.menu.containerId) return;
        pendingMatrixNetworkData = data.copy();
    }

    /** 获取缓存的节点列表，若没有则返回空列表 */
    private static List<NodeEntry> getCachedNodes() {
        List<NodeEntry> result = new ArrayList<>();
        if (pendingNodeData == null) return result;
        if (!pendingNodeData.contains("nodes")) return result;
        ListTag list = pendingNodeData.getList("nodes", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            String name = tag.getString("name");
            boolean needAuth = tag.getBoolean("needAuth");
            BlockPos pos = tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null;
            int load = tag.contains("load") ? tag.getInt("load") : 0;
            int capacity = tag.contains("capacity") ? tag.getInt("capacity") : 0;
            result.add(new NodeEntry(name, needAuth, pos, load, capacity));
        }
        return result;
    }

    /** 获取当前已连接的节点索引（-1 表示未连接） */
    private static int getConnectedIndex() {
        if (pendingNodeData == null || !pendingNodeData.contains("connectedIndex")) return -1;
        return pendingNodeData.getInt("connectedIndex");
    }

    private static List<NetworkEntry> getCachedMatrixNetworks() {
        List<NetworkEntry> result = new ArrayList<>();
        if (pendingMatrixNetworkData == null || !pendingMatrixNetworkData.contains("networks")) {
            return result;
        }
        ListTag list = pendingMatrixNetworkData.getList("networks", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            result.add(new NetworkEntry(tag.getString("ssid"), tag.getBoolean("needAuth"),
                    tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null,
                    tag.getInt("load"), tag.getInt("capacity")));
        }
        return result;
    }

    private static int getConnectedMatrixNetworkIndex() {
        return pendingMatrixNetworkData != null && pendingMatrixNetworkData.contains("connectedIndex")
                ? pendingMatrixNetworkData.getInt("connectedIndex") : -1;
    }

    /** 清空缓存（在请求新列表前调用） */
    public static void clearNodeCache() {
        pendingNodeData = null;
        pendingMatrixNetworkData = null;
    }

    /** 节点列表条目 */
    private record NodeEntry(String name, boolean needAuth, BlockPos pos, int load, int capacity) {}
    private record NetworkEntry(String name, boolean needAuth, BlockPos pos, int load, int capacity) {}

    // ==================== 实例字段 ====================

    public final Inventory inv;
    protected WirelessState wirelessState = WirelessState.DEFAULT;
    /** 记录初始面板类型，用于侧边栏切换时恢复到正确面板 */
    private final WirelessState initialState;
    private boolean renderBg = true;
    private boolean renderWireless = true;
    public int activeNode = -1;
    /** 用户是否主动点击侧边栏切换面板（机器页=false，无线/节点页=true） */
    protected boolean panelActive = false;
    private int waitPass = -1;
    private StringBuilder inputPass = new StringBuilder();
    private boolean nodesRequested = false;
    private boolean nodeResponseReceived = false;
    private long nodeRefreshAt = -1;
    /** Retry a request whose response was lost instead of leaving the page on
     * the scanning message forever.  Forty ticks is comfortably beyond the
     * server's ten-tick request limiter without generating packet spam. */
    private long nodeRequestDeadline = -1;
    private boolean matrixNetworksRequested = false;
    private boolean matrixNetworkResponseReceived = false;
    private boolean matrixNetworkAccessDenied = false;
    private long matrixNetworkRefreshAt = -1;
    private long matrixNetworkRequestDeadline = -1;

    // 缓存的服务端节点列表（实例级）
    private final List<NodeEntry> serverNodes = new ArrayList<>();
    private final List<NetworkEntry> serverMatrixNetworks = new ArrayList<>();
    private static final int NODES_PER_PAGE = 8;
    /** Offset into the available-node list after filtering the active node. */
    private int nodePageOffset = 0;
    private int activeMatrixNetwork = -1;
    private int matrixNetworkPageOffset = 0;
    private int waitMatrixPassword = -1;
    private final StringBuilder matrixPasswordInput = new StringBuilder();
    private boolean reserveSideInfoArea = true;

    public AcademyBaseUI(T t, Inventory inv, Component title, WirelessState initialState) {
        super(t, inv, title);
        // 关键：必须与 GUI 纹理尺寸一致，否则槽位渲染 topPos 与背景纹理错位
        // (AbstractContainerScreen 默认 imageHeight=166, 而纹理按 187 渲染, 差 10.5px)
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inv = inv;
        this.initialState = initialState;
        this.wirelessState = initialState;
    }

    /** Developer and other custom full-canvas screens manage their own origin. */
    protected final void setReserveSideInfoArea(boolean reserve) {
        this.reserveSideInfoArea = reserve;
    }

    /**
     * Absolute rectangles occupied by AcademyCraft widgets outside vanilla's
     * {@link AbstractContainerScreen} image rectangle.
     *
     * <p>The 1.0.7 layout deliberately puts its page selector to the left and
     * its information card to the right of the 176px inventory.  Recipe
     * overlays only know about {@code imageWidth}; without these rectangles
     * JEI starts its ingredient grid on top of the node name, energy graph and
     * machine status.  Keep the geometry in the screen so render, hit testing
     * and optional integrations all use the same fallback side.</p>
     */
    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> areas = new ArrayList<>(2);
        int sidebarLeft = getSidebarLeft();
        int sidebarTop = getSidebarTop();
        int sidebarHeight = renderWireless ? 38 : 18;
        if (sidebarLeft < width && sidebarLeft + 18 > 0
                && sidebarTop < height && sidebarTop + sidebarHeight > 0) {
            areas.add(new Rect2i(sidebarLeft, sidebarTop, 18, sidebarHeight));
        }

        // A wider screen such as MatrixGui already includes its right panel in
        // imageWidth, so it needs no duplicate external exclusion rectangle.
        if (this.imageWidth == GUI_WIDTH && hasJeiSideInfoArea()) {
            int panelX = InfoArea.resolvePanelX(this.leftPos);
            if (panelX != Integer.MIN_VALUE) {
                areas.add(new Rect2i(panelX, this.topPos + InfoArea.Y,
                        InfoArea.W, GUI_HEIGHT - InfoArea.Y));
            }
        }
        return List.copyOf(areas);
    }

    /** Screens without the legacy information card can opt out of its JEI area. */
    protected boolean hasJeiSideInfoArea() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        if (this.imageWidth == GUI_WIDTH) {
            // Slots, machine art, the sidebar and every auxiliary-page hit box
            // must share this origin.  At common GUI scales the whole legacy
            // composition is centred, not just its 176px inventory page.
            this.leftPos = RegularMachineLayout.machineLeft(this.width, reserveSideInfoArea);
        }
    }

    public void setRenderBg(boolean renderBg) {
        this.renderBg = renderBg;
    }

    public void setRenderWireless(boolean wireless) {
        this.renderWireless = wireless;
    }

    /** Server-confirmed direct entry used by the legacy developer wireless button. */
    protected final void openInitialWirelessPanel() {
        switchState(initialState);
        panelActive = true;
        menu.setSlotsActive(false);
        cancelAllInputModes();
        nodesRequested = false;
        nodeRequestDeadline = -1;
        nodeResponseReceived = false;
        clearNodeCache();
    }

    /**
     * Isolated client-gate input hook.  Calling the real mouse handler keeps
     * sidebar geometry and slot activation under test; the system property is
     * absent in normal clients, so this cannot become a player-facing API.
     */
    public final boolean clickWirelessSidebarForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || !renderWireless) return false;
        return mouseClicked(getSidebarLeft() + 9.0, getSidebarTop() + 29.0, 0);
    }

    /** Click the first visible, password-free node through its production hit box. */
    public final boolean clickFirstNodeForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || !panelActive
                || wirelessState != WirelessState.WIFI) return false;
        List<Integer> available = availableNodeIndices();
        if (available.isEmpty()) return false;
        int row = 0;
        NodeEntry first = serverNodes.get(available.getFirst());
        if (first.needAuth()) return false;
        return mouseClicked(leftPos + 145.0, topPos + 71.0 + row * 13.0, 0);
    }

    /**
     * Exercise the complete protected-node production path: click the rendered
     * row, feed characters through the screen input handler, then submit with
     * Enter.  This exists only in the self-terminating real-client gate.
     */
    public final boolean connectFirstProtectedNodeForVisualGate(String password) {
        if (!Boolean.getBoolean("academy.machineVisualGate") || !panelActive
                || wirelessState != WirelessState.WIFI || password == null) return false;
        List<Integer> available = availableNodeIndices();
        int pageEnd = Math.min(available.size(), nodePageOffset + NODES_PER_PAGE);
        for (int pageIndex = nodePageOffset; pageIndex < pageEnd; pageIndex++) {
            int nodeIndex = available.get(pageIndex);
            if (!serverNodes.get(nodeIndex).needAuth()) continue;
            int row = pageIndex - nodePageOffset;
            if (!mouseClicked(leftPos + 145.0, topPos + 71.0 + row * 13.0, 0)
                    || waitPass != nodeIndex) return false;
            for (int index = 0; index < password.length(); index++) {
                if (!charTyped(password.charAt(index), 0)) return false;
            }
            return keyPressed(257, 0, 0);
        }
        return false;
    }

    /** Click the first visible, password-free Matrix network through its production hit box. */
    public final boolean clickFirstMatrixNetworkForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || !panelActive
                || wirelessState != WirelessState.NODE) return false;
        List<Integer> available = availableMatrixNetworkIndices();
        if (available.isEmpty()) return false;
        int row = 0;
        NetworkEntry first = serverMatrixNetworks.get(available.getFirst());
        if (first.needAuth()) return false;
        return mouseClicked(leftPos + 145.0, topPos + 71.0 + row * 13.0, 0);
    }

    public final int visibleNodesForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") ? serverNodes.size() : 0;
    }

    public final int visibleMatrixNetworksForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") ? serverMatrixNetworks.size() : 0;
    }

    public final boolean hasActiveNodeForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") && activeNode >= 0;
    }

    public final boolean hasActiveMatrixNetworkForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") && activeMatrixNetwork >= 0;
    }

    // ==================== 辅助方法 ====================

    /** 取消所有输入状态 */
    private void cancelAllInputModes() {
        waitPass = -1;
        inputPass = new StringBuilder();
        waitMatrixPassword = -1;
        matrixPasswordInput.setLength(0);
    }

    // ==================== 渲染 ====================

    /**
     * Draw the two layers used by every regular 1.0.7 machine page.  The
     * {@code ui_*} images are transparent overlays, not self-contained GUI
     * backgrounds; rendering one by itself produces the blank/broken screens
     * reported in game.  Keep this in one method so every machine uses the
     * same scale, origin and shader reset.
     */
    protected final void renderStandardMachinePanel(GuiGraphics graphics, ResourceLocation overlay) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Anchor to AbstractContainerScreen's actual origin. Most machines use
        // imageWidth=176, for which this is identical to centring. Wider logical
        // screens (the Matrix inventory plus its right control panel) can now
        // reserve their complete width without shifting slots away from art.
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, PARENT_BACKGROUND);
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, UI_INVENTORY);
        if (overlay != null) {
            RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, overlay);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /**
     * Draw the original 400x187 developer-machine composition without
     * stretching its 278px right-hand texture into the regular 176px panel.
     * The menu remains centred on the 176px right work area, so its existing
     * player and machine slots line up at full-canvas x=120 and beyond.
     */
    protected final void renderDeveloperMachinePanel(GuiGraphics graphics) {
        // Developer screens explicitly place leftPos at the 176px menu origin
        // inside page_developer.xml. Deriving the canvas from that same origin
        // keeps texture, slots, hit boxes and the external selector aligned at
        // GUI scales where the 420px total composition only just fits.
        int fullLeft = this.leftPos - RegularMachineLayout.DEVELOPER_MENU_X;
        int top = this.topPos;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.render(109, GUI_HEIGHT, fullLeft + 4, top, graphics, DEV_PARENT_LEFT);
        RenderUtils.render(109, GUI_HEIGHT, fullLeft + 4, top, graphics, DEV_UI_LEFT);
        RenderUtils.render(109, GUI_HEIGHT, fullLeft + 4, top, graphics, DEV_PARENT_MACHINE);
        RenderUtils.render(278, GUI_HEIGHT, fullLeft + 118, top, graphics, DEV_PARENT_RIGHT);
        RenderUtils.render(278, GUI_HEIGHT, fullLeft + 118, top, graphics, DEV_UI_RIGHT);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** A slot-safe compact developer shell for logical viewports below 440px. */
    protected final void renderCompactDeveloperPanel(GuiGraphics graphics, boolean showInventory) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, PARENT_BACKGROUND);
        if (showInventory) {
            RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, UI_INVENTORY);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /**
     * Advanced developer keeps its modern two material slots and player
     * inventory while using the original 400x187 developer canvas.  The
     * official right-hand texture is only a transparent skill/developer
     * shell; drawing this official inventory layer makes every live menu slot
     * visible and prevents invisible container hit boxes.
     */
    protected final void renderDeveloperInventoryOverlay(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, graphics, UI_INVENTORY);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    @Override
    public void renderBg(GuiGraphics var1, float var2, int var3, int var4) {
        /*
         * Machine screens own their complete background in renderBackground().
         * Keep this hook empty: AbstractContainerScreen invokes it from the
         * standard background pass, and drawing a second panel here would cover
         * the machine texture (and used to make slots appear offset/dim).
         */
    }

    @Override
    public void render(GuiGraphics stack, int mouseX, int mouseY, float p_97798_) {
        long gameTime = inv.player.level().getGameTime();
        if (nodeRefreshAt >= 0 && gameTime >= nodeRefreshAt) {
            nodeRefreshAt = -1;
            nodesRequested = false;
            nodeRequestDeadline = -1;
            serverNodes.clear();
            activeNode = -1;
        }
        if (nodesRequested && !nodeResponseReceived && nodeRequestDeadline >= 0
                && gameTime >= nodeRequestDeadline) {
            nodesRequested = false;
            nodeRequestDeadline = -1;
        }
        if (matrixNetworkRefreshAt >= 0 && gameTime >= matrixNetworkRefreshAt) {
            matrixNetworkRefreshAt = -1;
            matrixNetworksRequested = false;
            matrixNetworkRequestDeadline = -1;
            serverMatrixNetworks.clear();
            activeMatrixNetwork = -1;
        }
        if (matrixNetworksRequested && !matrixNetworkResponseReceived
                && matrixNetworkRequestDeadline >= 0 && gameTime >= matrixNetworkRequestDeadline) {
            matrixNetworksRequested = false;
            matrixNetworkRequestDeadline = -1;
        }

        // Machines discover standalone nodes. Nodes use the separate 1.0.7
        // nodePage flow below to discover initialized Matrix networks.
        if (this.wirelessState == WirelessState.WIFI && this.panelActive
                && !this.nodesRequested && this.menu.pos != null) {
            this.nodesRequested = true;
            this.nodeRequestDeadline = gameTime + 40;
            clearNodeCache();
            PacketDistributor.sendToServer(new RequestNodesPacket(this.menu.pos));
        }
        if (this.wirelessState == WirelessState.NODE && this.panelActive
                && canManageNodeTopology()
                && !this.matrixNetworksRequested && this.menu.pos != null) {
            this.matrixNetworksRequested = true;
            this.matrixNetworkRequestDeadline = gameTime + 40;
            pendingMatrixNetworkData = null;
            PacketDistributor.sendToServer(new RequestMatrixNetworksPacket(this.menu.pos));
        }

        // 从静态缓存读取节点数据
        if (this.wirelessState == WirelessState.WIFI && pendingNodeData != null) {
            this.serverNodes.clear();
            this.serverNodes.addAll(getCachedNodes());
            int ci = getConnectedIndex();
            // Always apply the authoritative index, including -1.  Keeping
            // the previous index after a disconnect made the UI show a stale
            // connected node and sent subsequent clicks to the wrong row.
            this.activeNode = ci >= 0 && ci < this.serverNodes.size() ? ci : -1;
            clampNodePageOffset();
            this.nodeResponseReceived = true;
            this.nodeRequestDeadline = -1;
            clearNodeCache();
        }
        if (this.wirelessState == WirelessState.NODE && pendingMatrixNetworkData != null) {
            this.serverMatrixNetworks.clear();
            this.serverMatrixNetworks.addAll(getCachedMatrixNetworks());
            int ci = getConnectedMatrixNetworkIndex();
            this.activeMatrixNetwork = ci >= 0 && ci < this.serverMatrixNetworks.size() ? ci : -1;
            clampMatrixNetworkPageOffset();
            this.matrixNetworkResponseReceived = true;
            this.matrixNetworkAccessDenied = pendingMatrixNetworkData.getBoolean("accessDenied");
            this.matrixNetworkRequestDeadline = -1;
            pendingMatrixNetworkData = null;
        }

        // Let AbstractContainerScreen run the standard single-pass lifecycle
        // on the inventory page.  An auxiliary wireless page is a replacement
        // page in 1.0.7, not a translucent overlay: bypass the subclass machine
        // background, labels, widgets and slots there so they cannot bleed
        // through the wireless texture.
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (panelActive) {
            super.renderBackground(stack, mouseX, mouseY, p_97798_);
        } else {
            super.render(stack, mouseX, mouseY, p_97798_);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ====== 左侧图标按钮 ======

        int sidebarLeft = getSidebarLeft();
        int sidebarTop = getSidebarTop();

        // 背包图标
        float invAlpha = this.isHoveringButton(sidebarLeft, sidebarTop, 18, 18, mouseX, mouseY)
                || !panelActive ? 1 : 0.8f;
        RenderSystem.setShaderColor(1, 1, 1, invAlpha);
        RenderUtils.render(18, 18, sidebarLeft, sidebarTop, stack, IC_INV);

        if (this.renderWireless) {
            // 无线/WIFI 图标（节点方块和发电机统一使用同一个图标）
            float sidebarAlpha = this.isHoveringButton(sidebarLeft, sidebarTop + 20, 18, 18, mouseX, mouseY)
                    || (panelActive && (wirelessState == WirelessState.WIFI || wirelessState == WirelessState.NODE)) ? 1 : 0.8f;
            RenderSystem.setShaderColor(1, 1, 1, sidebarAlpha);
            RenderUtils.render(18, 18, sidebarLeft, sidebarTop + 20, stack, IC_WIRELESS);

            // ====== WIFI 面板 ======
            if (this.wirelessState == WirelessState.WIFI && panelActive) {
                renderWifiPanel(stack, mouseX, mouseY);
            }

            // ====== NODE 面板 ======
            if (this.wirelessState == WirelessState.NODE && panelActive) {
                renderNodePanel(stack, mouseX, mouseY);
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** 渲染 WIFI 节点列表面板 */
    private void renderWifiPanel(GuiGraphics stack, int mouseX, int mouseY) {
        // WIFI 面板背景
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, stack, PARENT_BACKGROUND);
        // page_wireless.xml uses icon_tonode for ordinary machines; only
        // WirelessPage.nodePage replaces it with icon_tomatrix.
        renderPanelElement(stack, -(GUI_WIDTH / 2) + 20, 10, 18, 18, IC_TONODE);
        renderPanelElement(stack, 0, 37, 160, 16, ELEMENT_BG_300_32);
        renderPanelElement(stack, -(160 / 2) + 16, 39, 11, 11, IC_MATRIX);

        String connectedName = activeNode != -1 && activeNode < serverNodes.size()
                ? "已连接"
                : "未连接";
        RenderUtils.renderText(stack, connectedName, this.leftPos + 13, this.topPos + 30);
        RenderUtils.renderText(stack, "可用", this.leftPos + 13, this.topPos + 55);

        if (serverNodes.isEmpty()) {
            RenderUtils.renderText(stack, nodeResponseReceived ? "附近没有可用节点" : "正在扫描附近节点…",
                    this.leftPos + 28, this.topPos + 78);
            if (nodeResponseReceived) RenderUtils.renderText(stack, "放置无线节点即可连接；矩阵仅用于扩展网络",
                    this.leftPos + 13, this.topPos + 94);
        }

        // 上下翻页按钮（确保Blend开启，因为箭头纹理是alpha-mask纯白纹理）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float upAlpha = this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 5, this.topPos + 65, 15, 15, mouseX, mouseY) ? 1 : 0.8f;
        RenderSystem.setShaderColor(1, 1, 1, upAlpha);
        renderPanelElement(stack, (160 / 2) - 5, 65, 15, 15, BTN_ARROW_UP);

        float downAlpha = this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 5, this.topPos + 65 + (7 * 13), 15, 15, mouseX, mouseY) ? 1 : 0.8f;
        RenderSystem.setShaderColor(1, 1, 1, downAlpha);
        renderPanelElement(stack, (160 / 2) - 5, 65 + (7 * 13), 15, 15, BTN_ARROW_DOWN);

        // 当前连接状态
        float disconnectAlpha = this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 16, this.topPos + 39, 15, 15, mouseX, mouseY) ? 1 : 0.8f;
        RenderSystem.setShaderColor(1, 1, 1, disconnectAlpha);
        if (activeNode != -1) {
            renderPanelElement(stack, (160 / 2) - 16, 39, 11, 11, IC_CONNECTED);
            RenderSystem.disableBlend();
            String nodeName = activeNode < serverNodes.size() ? serverNodes.get(activeNode).name() : "Node" + activeNode;
            RenderUtils.renderText(stack, nodeName, this.leftPos + 32, this.topPos + 41);
        } else {
            renderPanelElement(stack, (160 / 2) - 16, 39, 11, 11, IC_UNCONNECTED);
            RenderSystem.disableBlend();
            RenderUtils.renderText(stack, "未连接", this.leftPos + 32, this.topPos + 41);
        }

        // 可用节点列表。保留服务端原始索引，使翻页后密码输入和连接
        // 操作仍精确指向列表中的同一个节点。
        List<Integer> available = availableNodeIndices();
        int pageEnd = Math.min(available.size(), nodePageOffset + NODES_PER_PAGE);
        for (int pageIndex = nodePageOffset; pageIndex < pageEnd; pageIndex++) {
            int availIndex = pageIndex - nodePageOffset;
            int i = available.get(pageIndex);
            NodeEntry node = serverNodes.get(i);
            RenderSystem.setShaderColor(1, 1, 1, 0.7f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (node.needAuth()) {
                renderPanelElement(stack, -5, 62 + (availIndex * 13), 150, 16, ELEMENT_BG_300_32_I);
                // Background first: drawing it after the password glyph could
                // erase the glyph on resource packs with an opaque row asset.
                renderPanelElement(stack, -8, 65 + (availIndex * 13), 11, 11, IC_KEY);
            } else {
                renderPanelElement(stack, -5, 62 + (availIndex * 13), 150, 16, ELEMENT_BG_300_32);
            }
            renderPanelElement(stack, -(160 / 2) + 16 - 4, 65 + (availIndex * 13), 11, 11, IC_MATRIX);

            float cnAlpha = this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 16 - 6, this.topPos + 65 + (availIndex * 13), 15, 15, mouseX, mouseY) ? 1 : 0.7f;
            RenderSystem.setShaderColor(1, 1, 1, cnAlpha);
            renderPanelElement(stack, (160 / 2) - 16 - 6, 65 + (availIndex * 13), 11, 11, IC_UNCONNECTED);
            RenderSystem.disableBlend();
            RenderUtils.renderText(stack, node.name(), this.leftPos + 32 - 4, this.topPos + 67 + (availIndex * 13));

            if (waitPass == i) {
                String masked = "*".repeat(inputPass.length());
                int pwX = this.leftPos + GUI_WIDTH / 2 - this.font.width(masked) / 2;
                RenderUtils.renderText(stack, masked, pwX, this.topPos + 67 + (availIndex * 13));
            }
        }
    }

    /**
     * Render the original nodePage variant: current Matrix network plus the
     * initialized networks discoverable by this node. Node identity editing
     * lives in BaseNodeGui's persistent right-side InfoArea, as in 1.0.7.
     */
    private void renderNodePanel(GuiGraphics stack, int mouseX, int mouseY) {
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, this.leftPos, this.topPos, stack, PARENT_BACKGROUND);
        renderPanelElement(stack, -(GUI_WIDTH / 2) + 20, 10, 18, 18, IC_TOMATRIX);
        renderPanelElement(stack, 0, 37, 160, 16, ELEMENT_BG_300_32);
        renderPanelElement(stack, -(160 / 2) + 16, 39, 11, 11, IC_MATRIX);

        RenderUtils.renderText(stack, "已连接网络", this.leftPos + 13, this.topPos + 30);
        RenderUtils.renderText(stack, "可用网络", this.leftPos + 13, this.topPos + 55);

        if (matrixNetworkAccessDenied) {
            RenderUtils.renderText(stack, "仅所有者或管理员可管理节点网络",
                    this.leftPos + 18, this.topPos + 78);
        } else if (serverMatrixNetworks.isEmpty()) {
            RenderUtils.renderText(stack, matrixNetworkResponseReceived
                            ? "附近没有已初始化矩阵" : "正在扫描矩阵网络…",
                    this.leftPos + 28, this.topPos + 78);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean disconnectHover = isHoveringButton(this.leftPos + 144, this.topPos + 39,
                15, 15, mouseX, mouseY);
        RenderSystem.setShaderColor(1, 1, 1, disconnectHover ? 1.0f : 0.8f);
        if (activeMatrixNetwork >= 0 && activeMatrixNetwork < serverMatrixNetworks.size()) {
            renderPanelElement(stack, (160 / 2) - 16, 39, 11, 11, IC_CONNECTED);
            RenderSystem.disableBlend();
            RenderUtils.renderText(stack, serverMatrixNetworks.get(activeMatrixNetwork).name(),
                    this.leftPos + 32, this.topPos + 41);
        } else {
            renderPanelElement(stack, (160 / 2) - 16, 39, 11, 11, IC_UNCONNECTED);
            RenderSystem.disableBlend();
            RenderUtils.renderText(stack, "未连接", this.leftPos + 32, this.topPos + 41);
        }

        float upAlpha = isHoveringButton(this.leftPos + 155, this.topPos + 65,
                15, 15, mouseX, mouseY) ? 1.0f : 0.8f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, upAlpha);
        renderPanelElement(stack, (160 / 2) - 5, 65, 15, 15, BTN_ARROW_UP);
        float downAlpha = isHoveringButton(this.leftPos + 155,
                this.topPos + 65 + (7 * 13), 15, 15, mouseX, mouseY) ? 1.0f : 0.8f;
        RenderSystem.setShaderColor(1, 1, 1, downAlpha);
        renderPanelElement(stack, (160 / 2) - 5, 65 + (7 * 13), 15, 15, BTN_ARROW_DOWN);

        List<Integer> available = availableMatrixNetworkIndices();
        int pageEnd = Math.min(available.size(), matrixNetworkPageOffset + NODES_PER_PAGE);
        for (int pageIndex = matrixNetworkPageOffset; pageIndex < pageEnd; pageIndex++) {
            int row = pageIndex - matrixNetworkPageOffset;
            int networkIndex = available.get(pageIndex);
            NetworkEntry network = serverMatrixNetworks.get(networkIndex);
            RenderSystem.setShaderColor(1, 1, 1, 0.7f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (network.needAuth()) {
                renderPanelElement(stack, -5, 62 + row * 13, 150, 16, ELEMENT_BG_300_32_I);
                renderPanelElement(stack, -8, 65 + row * 13, 11, 11, IC_KEY);
            } else {
                renderPanelElement(stack, -5, 62 + row * 13, 150, 16, ELEMENT_BG_300_32);
            }
            renderPanelElement(stack, -(160 / 2) + 16 - 4, 65 + row * 13, 11, 11, IC_MATRIX);
            boolean connectHover = isHoveringButton(this.leftPos + 138,
                    this.topPos + 65 + row * 13, 15, 15, mouseX, mouseY);
            RenderSystem.setShaderColor(1, 1, 1, connectHover ? 1.0f : 0.7f);
            renderPanelElement(stack, (160 / 2) - 16 - 6, 65 + row * 13, 11, 11, IC_UNCONNECTED);
            RenderSystem.disableBlend();
            RenderUtils.renderText(stack, network.name(), this.leftPos + 28, this.topPos + 67 + row * 13);
            if (waitMatrixPassword == networkIndex) {
                String masked = "*".repeat(matrixPasswordInput.length());
                RenderUtils.renderText(stack, masked,
                        this.leftPos + GUI_WIDTH / 2 - font.width(masked) / 2,
                        this.topPos + 67 + row * 13);
            }
        }
    }

    /** 渲染右侧能量信息面板（InfoArea 直方图） */
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        if (this.menu == null || this.menu.pos == null || this.inv == null) return;

        BlockEntity be = this.inv.player.level().getBlockEntity(this.menu.pos);
        if (be == null) return;

        InfoArea info = new InfoArea();
        if (be instanceof IFEnergyStorage storage) {
            info.histogram(InfoArea.histEnergy(storage.getEnergyStored(), Math.max(1, storage.getMaxEnergyStored())));
            info.property("速率", getEnergyRateText(be));
        } else if (be instanceof MetalFomerBlockEntity former) {
            info.histogram(InfoArea.histEnergy(former.getEnergy(), Math.max(1, former.getMaxEnergy())));
            info.property("模式", former.getMode().name());
        } else if (be instanceof BaseNodeBlockEntity nodeBe) {
            info.histogram(InfoArea.histEnergy(nodeBe.getEnergy(), Math.max(1, nodeBe.getMaxEnergy())));
            info.property("带宽", (int) nodeBe.getBandwidth() + " IF/t");
            info.property("范围", (int) nodeBe.getRange() + " 格");
        } else {
            return;
        }
        info.draw(graphics, this.leftPos, this.topPos);
    }

    private String getEnergyRateText(BlockEntity be) {
        if (be instanceof SolarGenBlockEntity solarBe) {
            return switch (solarBe.getStatus()) {
                case STRONG -> "3 IF/t";
                case WEAK -> "0.6 IF/t";
                case STOPPED -> "0 IF/t";
            };
        } else if (be instanceof WindGenBaseBlockEntity windBe) {
            return windBe.isValidMain() ? "1 IF/t" : "0 IF/t";
        } else if (be instanceof WindGenMainBlockEntity) {
            return "1 IF/t";
        }
        return "0 IF/t";
    }

    public boolean isHoveringButton(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ==================== 绘制工具 ====================

    /** 渲染 alpha-mask 图标(纯白剪影纹理),支持 alpha 透明度 */
    protected void drawIcon(GuiGraphics gg, ResourceLocation icon, int x, int y, int size, float alpha, int srcSize) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        gg.blit(icon, x, y, size, size, 0, 0, srcSize, srcSize, srcSize, srcSize);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** 渲染 alpha-mask 图标并染色(RGB 用于给白色剪影着色) */
    protected void drawIcon(GuiGraphics gg, ResourceLocation icon, int x, int y, int size,
                            float r, float g, float b, float alpha, int srcSize) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, alpha);
        gg.blit(icon, x, y, size, size, 0, 0, srcSize, srcSize, srcSize, srcSize);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int p_97750_) {
        int sidebarLeft = getSidebarLeft();
        int sidebarTop = getSidebarTop();
        if (this.isHoveringButton(sidebarLeft, sidebarTop, 18, 18, mouseX, mouseY)) {
            switchState(WirelessState.DEFAULT);
            panelActive = false;
            this.menu.setSlotsActive(true);
        }
        if (this.renderWireless) {
            // 侧边栏图标：优先根据当前 wirelessState 决定，回退到 initialState
            if (this.isHoveringButton(sidebarLeft, sidebarTop + 20, 18, 18, mouseX, mouseY)) {
                WirelessState targetState;
                if (this.wirelessState == WirelessState.NODE || this.wirelessState == WirelessState.WIFI) {
                    targetState = this.wirelessState;
                } else {
                    targetState = this.initialState;
                }
                if (targetState == WirelessState.NODE && !canManageNodeTopology()) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                                "message.academy.node.owner_only"), true);
                    }
                    return true;
                }
                switchState(targetState);
                panelActive = true;
                this.menu.setSlotsActive(false);
                // Do not discard a response merely because the page was
                // closed and reopened.  The previous implementation forced a
                // second scan inside the server's 10-tick limiter and could
                // leave this page waiting forever after that request was
                // rejected.  A topology mutation calls requestNodeRefresh().
            }

            if (panelActive) {
                switch (this.wirelessState) {
                    case WIFI -> handleWifiClick(mouseX, mouseY);
                    case NODE -> handleNodeClick(mouseX, mouseY);
                    case DEFAULT -> {}
                }
            }
        }
        // 机器页（面板未激活）时交给容器处理槽位点击，无线/节点页拦截
        if (!this.panelActive)
            return super.mouseClicked(mouseX, mouseY, p_97750_);
        return true;
    }

    /** WIFI 面板点击处理 */
    private void handleWifiClick(double mouseX, double mouseY) {
        if (!menu.actionSessionReady()) return;
        // 断开当前连接
        if (this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 16, this.topPos + 39, 15, 15, mouseX, mouseY)) {
            if (activeNode != -1 && this.menu.pos != null) {
                PacketDistributor.sendToServer(new DisconnectFromNodePacket(menu.nextActionToken(), this.menu.pos));
                requestNodeRefresh();
                cancelAllInputModes();
            }
        }

        int guiLeft = this.leftPos;
        int guiTop = this.topPos;
        int arrowX = guiLeft + 155;
        if (this.isHoveringButton(arrowX, guiTop + 65, 15, 15, mouseX, mouseY)) {
            nodePageOffset = Math.max(0, nodePageOffset - NODES_PER_PAGE);
            waitPass = -1;
            inputPass = new StringBuilder();
            return;
        }
        if (this.isHoveringButton(arrowX, guiTop + 65 + (7 * 13), 15, 15, mouseX, mouseY)) {
            nodePageOffset = Math.min(maxNodePageOffset(), nodePageOffset + NODES_PER_PAGE);
            waitPass = -1;
            inputPass = new StringBuilder();
            return;
        }

        // 点击当前页中的节点连接
        List<Integer> available = availableNodeIndices();
        int pageEnd = Math.min(available.size(), nodePageOffset + NODES_PER_PAGE);
        for (int pageIndex = nodePageOffset; pageIndex < pageEnd; pageIndex++) {
            int availIndex = pageIndex - nodePageOffset;
            int i = available.get(pageIndex);
            NodeEntry node = serverNodes.get(i);
            if (this.isHoveringButton(this.leftPos + (160 / 2) * 2 - 16 - 6, this.topPos + 65 + (availIndex * 13), 15, 15, mouseX, mouseY)) {
                if (node.needAuth()) {
                    waitPass = i;
                    inputPass = new StringBuilder();
                } else if (this.menu.pos != null && node.pos() != null) {
                    PacketDistributor.sendToServer(new ConnectToNodePacket(menu.nextActionToken(), this.menu.pos, node.pos(), java.util.Optional.empty()));
                    requestNodeRefresh();
                }
                // Entry hit boxes deliberately have a little vertical padding
                // and overlap by two pixels. A click must still target exactly
                // one row rather than sending two competing link requests.
                return;
            }
            if (node.needAuth() && this.isHoveringButton(
                    RegularMachineLayout.centeredElementX(this.leftPos, 150, -5),
                    this.topPos + 62 + (availIndex * 13), 150, 16, mouseX, mouseY)) {
                waitPass = i;
                inputPass = new StringBuilder();
                return;
            }
        }
    }

    /** Node-to-Matrix page click handling. */
    private void handleNodeClick(double mouseX, double mouseY) {
        if (!menu.actionSessionReady()) return;
        if (!canManageNodeTopology()) return;
        if (isHoveringButton(this.leftPos + 144, this.topPos + 39,
                15, 15, mouseX, mouseY)) {
            if (activeMatrixNetwork >= 0 && menu.pos != null) {
                PacketDistributor.sendToServer(new DisconnectNodeFromMatrixPacket(menu.nextActionToken(), menu.pos));
                requestMatrixNetworkRefresh();
                cancelAllInputModes();
            }
            return;
        }
        if (isHoveringButton(this.leftPos + 155, this.topPos + 65,
                15, 15, mouseX, mouseY)) {
            matrixNetworkPageOffset = Math.max(0, matrixNetworkPageOffset - NODES_PER_PAGE);
            waitMatrixPassword = -1;
            matrixPasswordInput.setLength(0);
            return;
        }
        if (isHoveringButton(this.leftPos + 155, this.topPos + 65 + 7 * 13,
                15, 15, mouseX, mouseY)) {
            matrixNetworkPageOffset = Math.min(maxMatrixNetworkPageOffset(),
                    matrixNetworkPageOffset + NODES_PER_PAGE);
            waitMatrixPassword = -1;
            matrixPasswordInput.setLength(0);
            return;
        }

        List<Integer> available = availableMatrixNetworkIndices();
        int pageEnd = Math.min(available.size(), matrixNetworkPageOffset + NODES_PER_PAGE);
        for (int pageIndex = matrixNetworkPageOffset; pageIndex < pageEnd; pageIndex++) {
            int row = pageIndex - matrixNetworkPageOffset;
            int networkIndex = available.get(pageIndex);
            NetworkEntry network = serverMatrixNetworks.get(networkIndex);
            boolean connectIcon = isHoveringButton(this.leftPos + 138,
                    this.topPos + 65 + row * 13, 15, 15, mouseX, mouseY);
            boolean passwordRow = network.needAuth() && isHoveringButton(
                    RegularMachineLayout.centeredElementX(this.leftPos, 150, -5),
                    this.topPos + 62 + row * 13, 150, 16, mouseX, mouseY);
            if (!connectIcon && !passwordRow) continue;
            if (network.needAuth()) {
                waitMatrixPassword = networkIndex;
                matrixPasswordInput.setLength(0);
            } else if (menu.pos != null && network.pos() != null) {
                PacketDistributor.sendToServer(new ConnectNodeToMatrixPacket(menu.nextActionToken(),
                        menu.pos, network.pos(), java.util.Optional.empty()));
                requestMatrixNetworkRefresh();
            }
            return;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.wirelessState == WirelessState.DEFAULT || !panelActive) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Escape 键：取消输入状态
        if (keyCode == 256) {
            if (waitPass != -1) {
                waitPass = -1;
                inputPass = new StringBuilder();
                return true;
            }
            if (waitMatrixPassword != -1) {
                waitMatrixPassword = -1;
                matrixPasswordInput.setLength(0);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Do not discard a password draft if the session has not synchronized.
        if ((keyCode == 257 || keyCode == 335) && !menu.actionSessionReady()) return true;

        // ====== NODE 面板输入 ======
        if (this.wirelessState == WirelessState.NODE) {
            if (keyCode == 259) {
                if (!matrixPasswordInput.isEmpty()) {
                    matrixPasswordInput.deleteCharAt(matrixPasswordInput.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (waitMatrixPassword >= 0 && waitMatrixPassword < serverMatrixNetworks.size()) {
                    NetworkEntry target = serverMatrixNetworks.get(waitMatrixPassword);
                    if (menu.pos != null && target.pos() != null) {
                        PacketDistributor.sendToServer(new ConnectNodeToMatrixPacket(menu.nextActionToken(), menu.pos,
                                target.pos(), java.util.Optional.of(matrixPasswordInput.toString())));
                        requestMatrixNetworkRefresh();
                    }
                    waitMatrixPassword = -1;
                    matrixPasswordInput.setLength(0);
                }
                return true;
            }
            return true;
        }

        // ====== WIFI 面板输入 ======
        if (this.wirelessState == WirelessState.WIFI) {
            if (keyCode == 259) { // Backspace
                if (!inputPass.isEmpty()) {
                    inputPass.deleteCharAt(inputPass.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (waitPass != -1 && waitPass < serverNodes.size()) {
                    NodeEntry targetNode = serverNodes.get(waitPass);
                    if (this.menu.pos != null && targetNode.pos() != null) {
                        PacketDistributor.sendToServer(new ConnectToNodePacket(menu.nextActionToken(),
                                this.menu.pos, targetNode.pos(),
                                java.util.Optional.of(inputPass.toString())
                        ));
                        requestNodeRefresh();
                    }
                    waitPass = -1;
                    inputPass = new StringBuilder();
                }
                return true;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (!panelActive || Character.isISOControl(codePoint)) return super.charTyped(codePoint, modifiers);
        if (wirelessState == WirelessState.NODE && waitMatrixPassword != -1) {
            if (matrixPasswordInput.length() < NetworkInputLimits.PASSWORD) {
                matrixPasswordInput.append(codePoint);
            }
            return true;
        }
        if (wirelessState == WirelessState.WIFI && waitPass != -1) {
            if (inputPass.length() < NetworkInputLimits.PASSWORD) inputPass.append(codePoint);
            return true;
        }
        return true;
    }

    /** 切换面板状态 */
    private void switchState(WirelessState newState) {
        if (this.wirelessState != newState) {
            this.wirelessState = newState;
            cancelAllInputModes();
        }
    }

    private void requestNodeRefresh() {
        // Wait past the server request limiter; do not claim success before the
        // correlated authoritative response arrives.
        nodeRefreshAt = inv.player.level().getGameTime() + 11;
        nodesRequested = true;
        nodeRequestDeadline = -1;
        clearNodeCache();
        nodeResponseReceived = false;
    }

    private void requestMatrixNetworkRefresh() {
        matrixNetworkRefreshAt = inv.player.level().getGameTime() + 11;
        matrixNetworksRequested = true;
        matrixNetworkRequestDeadline = -1;
        pendingMatrixNetworkData = null;
        matrixNetworkResponseReceived = false;
        matrixNetworkAccessDenied = false;
    }

    private boolean canManageNodeTopology() {
        return !(menu instanceof BaseNodeMenu nodeMenu) || nodeMenu.canEditNode();
    }

    /** Original server-list indices visible to the user, excluding the node
     * already connected to this machine.  Keeping original indices makes a
     * password prompt stable while paging. */
    private List<Integer> availableNodeIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < serverNodes.size(); i++) {
            if (i != activeNode) result.add(i);
        }
        return result;
    }

    private int maxNodePageOffset() {
        int count = availableNodeIndices().size();
        return count <= NODES_PER_PAGE ? 0 : ((count - 1) / NODES_PER_PAGE) * NODES_PER_PAGE;
    }

    private void clampNodePageOffset() {
        nodePageOffset = Math.max(0, Math.min(nodePageOffset, maxNodePageOffset()));
        nodePageOffset -= nodePageOffset % NODES_PER_PAGE;
    }

    private List<Integer> availableMatrixNetworkIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < serverMatrixNetworks.size(); i++) {
            if (i != activeMatrixNetwork) result.add(i);
        }
        return result;
    }

    private int maxMatrixNetworkPageOffset() {
        int count = availableMatrixNetworkIndices().size();
        return count <= NODES_PER_PAGE ? 0
                : ((count - 1) / NODES_PER_PAGE) * NODES_PER_PAGE;
    }

    private void clampMatrixNetworkPageOffset() {
        matrixNetworkPageOffset = Math.max(0,
                Math.min(matrixNetworkPageOffset, maxMatrixNetworkPageOffset()));
        matrixNetworkPageOffset -= matrixNetworkPageOffset % NODES_PER_PAGE;
    }

    /** Draw an element whose x offset follows RenderUtils.renderCenterTop's
     * legacy semantics, but relative to this screen's authoritative origin. */
    private void renderPanelElement(GuiGraphics graphics, int centerOffset, int yOffset,
                                    int drawWidth, int drawHeight, ResourceLocation texture) {
        int x = RegularMachineLayout.centeredElementX(this.leftPos, drawWidth, centerOffset);
        RenderUtils.render(drawWidth, drawHeight, x, this.topPos + yOffset, graphics, texture);
    }

    /** Screens with a wider visual canvas may place the common page selector
     * outside that canvas while retaining the same render/hit-test origin. */
    protected int getSidebarLeft() { return this.leftPos - 20; }
    protected int getSidebarTop() { return this.topPos; }
}
