package com.mohistmc.academy.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.CloseDevLearningSessionPacket;
import com.mohistmc.academy.network.DevLearningResultPacket;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.SkillType;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.BaseFactor;
import com.mohistmc.academy.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class SkillTreeGui extends AcademyScreen {

    private static final ResourceLocation SKILL_BACK = tex("textures/guis/developer/skill_back.png");
    private static final ResourceLocation SKILL_OUTLINE = tex("textures/guis/developer/skill_outline.png");
    private static final ResourceLocation SKILL_SELECTED = tex("textures/guis/developer/skill_view_outline_glow.png");
    private static final ResourceLocation SKILL_VIEW_OUTLINE = tex("textures/guis/developer/skill_view_outline.png");
    private static final ResourceLocation LEGACY_LINE = tex("textures/guis/developer/line.png");
    private static final ResourceLocation BUTTON_LEARN = tex("textures/guis/button/button_learn.png");
    private static final ResourceLocation LEGACY_PARENT_RIGHT = tex("textures/guis/parent/parent_background_developerright.png");
    private static final ResourceLocation LEGACY_PARENT_LEFT = tex("textures/guis/parent/parent_background_developerleft.png");
    private static final ResourceLocation LEGACY_PARENT_MACHINE = tex("textures/guis/parent/parent_background_developermachine.png");
    private static final ResourceLocation LEGACY_UI_RIGHT = tex("textures/guis/ui/ui_developerright.png");
    private static final ResourceLocation LEGACY_UI_LEFT = tex("textures/guis/ui/ui_developerleft.png");
    private static final ResourceLocation LEGACY_UI_LEFT_TREE = tex("textures/guis/ui/ui_developerleft_skilltree.png");
    private static final ResourceLocation LEGACY_TREE_BACKGROUND = tex("textures/guis/effect/effect_developer_background.png");
    private static final ResourceLocation LEGACY_NODE_ICON = tex("textures/guis/icons/icon_node.png");
    private static final ResourceLocation LEGACY_NO_CATEGORY_ICON = tex("textures/guis/icons/icon_nocategory.png");
    private static final ResourceLocation LEGACY_LIST_BUTTON = tex("textures/guis/element/element_background300x32.png");

    /** Exact logical canvas used by page_developer.xml in the final 1.12.2 source. */
    private static final int LEGACY_GUI_WIDTH = 400;
    private static final int LEGACY_GUI_HEIGHT = 187;
    private static final int PADDING = 10;
    private static final int TOP_BAR_HEIGHT = 26;
    private static final int SKILL_HEIGHT = 18;
    private static final int SKILL_GAP = 4;
    private static final int LEVEL_HEADER_HEIGHT = 14;
    private static final int INFO_PANEL_WIDTH = 120;

    private static final int COLOR_SCROLL_BAR = 0x88FFFFFF;

    private int colWidth;
    private int skillWidth;
    private int infoPanelWidth = INFO_PANEL_WIDTH;
    private boolean interactiveLayout;
    private int treeAreaLeft;
    private int treeAreaTop;
    private int treeAreaWidth;
    private int treeAreaHeight;
    private int maxScroll = 0;
    private int scrollOffset = 0;
    private boolean legacyPositionLayout = false;
    private boolean isScrolling = false;
    private final boolean fromTerminal;
    private boolean hoveredBack = false;
    private final boolean readOnly;
    private final DevMachineType devType;
    private int energy;
    private final int maxEnergy;
    private final BlockPos devPos;
    private final UUID sessionNonce;
    private final String linkedNodeName;

    private final List<SkillNode> skillNodes = new ArrayList<>();
    private SkillNode hoveredNode = null;
    private SkillNode selectedNode = null;
    private String pendingSkillId = null;
    private int pendingLevel = -1;
    private long pendingSince = 0;
    private Component serverFeedback = Component.empty();
    private int refreshTicker = 0;
    private boolean sessionSpent = false;
    private int actionLeft, actionTop, actionWidth;
    private int networkActionLeft, networkActionTop, networkActionWidth;
    private boolean detailOpen = false;
    private boolean levelDetailOpen = false;
    private String consoleInput = "";

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, path);
    }

    public SkillTreeGui() { this(false, false, null, 0, 0, null, null, ""); }
    public SkillTreeGui(boolean fromTerminal) { this(fromTerminal, false, null, 0, 0, null, null, ""); }
    public SkillTreeGui(boolean fromTerminal, boolean readOnly) { this(fromTerminal, readOnly, null, 0, 0, null, null, ""); }
    public SkillTreeGui(boolean fromTerminal, boolean readOnly, DevMachineType devType, int energy, int maxEnergy) {
        this(fromTerminal, readOnly, devType, energy, maxEnergy, null, null, "");
    }
    public SkillTreeGui(boolean fromTerminal, boolean readOnly, DevMachineType devType, int energy, int maxEnergy, BlockPos devPos) {
        this(fromTerminal, readOnly, devType, energy, maxEnergy, devPos, null, "");
    }
    public SkillTreeGui(boolean fromTerminal, boolean readOnly, DevMachineType devType, int energy, int maxEnergy, BlockPos devPos, UUID sessionNonce) {
        this(fromTerminal, readOnly, devType, energy, maxEnergy, devPos, sessionNonce, "");
    }
    public SkillTreeGui(boolean fromTerminal, boolean readOnly, DevMachineType devType, int energy, int maxEnergy,
                        BlockPos devPos, UUID sessionNonce, String linkedNodeName) {
        super(Component.translatable("block.academy.dev_normal"));
        this.fromTerminal = fromTerminal;
        this.readOnly = readOnly || devType == null;
        this.devType = devType;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.devPos = devPos;
        this.sessionNonce = sessionNonce;
        this.linkedNodeName = linkedNodeName == null ? "" : linkedNodeName;
    }

    @Override protected void init() { super.init(); recalcLayout(); buildSkillNodes(); }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        recalcLayout();
        buildSkillNodes();
    }

    private void recalcLayout() {
        int availableWidth = Math.max(1, width - 8);
        int availableHeight = Math.max(1, height - 8);
        double scale = Math.min(1.0, Math.min(availableWidth / (double) LEGACY_GUI_WIDTH,
                availableHeight / (double) LEGACY_GUI_HEIGHT));
        this.guiWidth = Math.max(1, (int) Math.round(LEGACY_GUI_WIDTH * scale));
        this.guiHeight = Math.max(1, (int) Math.round(LEGACY_GUI_HEIGHT * scale));
        this.guiLeft = (width - guiWidth) / 2;
        this.guiTop = (height - guiHeight) / 2;
        this.interactiveLayout = scale >= .6;
        if (!interactiveLayout) {
            this.infoPanelWidth = 0;
            this.colWidth = this.skillWidth = 0;
            this.treeAreaLeft = guiLeft;
            this.treeAreaTop = guiTop;
            this.treeAreaWidth = this.treeAreaHeight = 0;
            return;
        }
        this.infoPanelWidth = scaled(109);
        this.colWidth = Math.max(14, scaled(257) / 5);
        this.skillWidth = Math.max(10, colWidth - 2);
        this.treeAreaLeft = this.guiLeft + scaled(128);
        this.treeAreaTop = this.guiTop + scaled(18);
        this.treeAreaWidth = scaled(257);
        this.treeAreaHeight = scaled(139);
    }

    private int scaled(int logical) {
        return (int) Math.round(logical * guiWidth / (double) LEGACY_GUI_WIDTH);
    }

    private int scaled(double logical) {
        return (int) Math.round(logical * guiWidth / (double) LEGACY_GUI_WIDTH);
    }

    @Override public void tick() {
        super.tick();
        refreshAuthoritativeEnergy();
        if (++refreshTicker >= 10 || skillNodes.isEmpty()) {
            refreshTicker = 0;
            String selectedId = selectedNode == null ? null : selectedNode.skill.getId();
            buildSkillNodes();
            selectedNode = selectedId == null ? null : findNode(selectedId);
        }
        Minecraft mc = Minecraft.getInstance();
        if (pendingSkillId != null && mc.player != null) {
            PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (LearnSkillPacket.INDUCTION_ACTION.equals(pendingSkillId) && data.hasAbility()) {
                serverFeedback = Component.literal("§a服务器确认：能力诱导成功，请重新打开开发机继续学习");
                pendingSkillId = null;
                pendingLevel = -1;
                buildSkillNodes();
            } else if (LearnSkillPacket.LEVEL_UP_ACTION.equals(pendingSkillId)
                    && data.getPlayerLevel() > pendingLevel) {
                serverFeedback = Component.literal("§a服务器确认：能力等级提升成功，请重新打开开发机继续学习");
                pendingSkillId = null;
                pendingLevel = -1;
                selectedNode = null;
                buildSkillNodes();
            } else if (data.hasLearnedSkill(pendingSkillId)) {
                serverFeedback = Component.literal("§a服务器确认：学习成功，请重新打开开发机继续学习");
                pendingSkillId = null;
                pendingLevel = -1;
                buildSkillNodes();
            } else if (mc.level != null && mc.level.getGameTime() - pendingSince > 500) {
                serverFeedback = Component.literal("§c服务器未接受请求；请检查条件、能量或会话距离");
                pendingSkillId = null;
                pendingLevel = -1;
            }
        }
    }

    private void refreshAuthoritativeEnergy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (devType == DevMachineType.PORTABLE) {
            energy = com.mohistmc.academy.capability.EnergyItemHelper.getEnergy(mc.player.getMainHandItem());
        } else if (devPos != null && mc.level != null
                && mc.level.getBlockEntity(devPos) instanceof com.mohistmc.academy.capability.IFEnergyStorage storage) {
            energy = Math.clamp(storage.getEnergyStored(), 0, storage.getMaxEnergyStored());
        }
    }

    public static void acceptServerResult(DevLearningResultPacket result) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SkillTreeGui gui) || gui.sessionNonce == null
                || !gui.sessionNonce.equals(result.nonce())) return;
        gui.energy = Math.clamp(result.energy(), 0, Math.max(0, result.maxEnergy()));
        boolean developing = result.reason().startsWith("开发中 ");
        if (!developing) { gui.pendingSkillId = null; gui.pendingLevel = -1; }
        else if (mc.level != null) gui.pendingSince = mc.level.getGameTime();
        gui.serverFeedback = Component.literal((result.success() ? "§a" : developing ? "§b" : "§c") + result.reason());
        gui.sessionSpent = !developing;
        gui.buildSkillNodes();
    }

    private void buildSkillNodes() {
        skillNodes.clear();
        if (!interactiveLayout) { maxScroll = 0; scrollOffset = 0; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;

        AbilityCategory category = data.getCurrentAbility();
        List<Skill> allSkills = SkillRegistry.getSkillsByCategory(category);
        legacyPositionLayout = !allSkills.isEmpty() && allSkills.stream().allMatch(Skill::hasLegacyTreePosition);
        if (legacyPositionLayout) {
            // Final 1.12.2 uses the authored guiX/guiY values directly inside
            // page_developer.xml's 257x139 area.  Scaling the 241x126 occupied
            // bounds to fill the area shifts every node and is observably wrong.
            int nodeSize = Math.max(8, scaled(16));
            for (Skill skill : allSkills) {
                int x = treeAreaLeft + scaled((int) Math.round(skill.getTreeX()));
                int y = treeAreaTop + scaled((int) Math.round(skill.getTreeY()));
                boolean learned = data.hasLearnedSkill(skill.getId());
                skillNodes.add(new SkillNode(skill, x, y, nodeSize, nodeSize, learned,
                        data.canLearnSkill(skill), skill.getType() == SkillType.PASSIVE));
            }
            maxScroll = 0;
            scrollOffset = 0;
            return;
        }
        int totalContentHeight = 0;

        for (int level = 1; level <= 5; level++) {
            List<Skill> levelSkills = new ArrayList<>();
            for (Skill s : allSkills) if (s.getLevel() == level) levelSkills.add(s);
            if (levelSkills.isEmpty()) continue;

            int colX = this.treeAreaLeft + (level - 1) * colWidth;
            int columnTotalHeight = levelSkills.size() * SKILL_HEIGHT + (levelSkills.size() - 1) * SKILL_GAP;
            int startY = this.treeAreaTop + (treeAreaHeight - columnTotalHeight) / 2;

            for (int i = 0; i < levelSkills.size(); i++) {
                Skill skill = levelSkills.get(i);
                int y = startY + i * (SKILL_HEIGHT + SKILL_GAP);
                boolean learned = data.hasLearnedSkill(skill.getId());
                boolean canLearn = data.canLearnSkill(skill);
                boolean isPassive = skill.getType() == SkillType.PASSIVE;
                int nodeSize = Math.min(SKILL_HEIGHT, skillWidth);
                int nodeX = colX + (skillWidth - nodeSize) / 2;
                skillNodes.add(new SkillNode(skill, nodeX, y, nodeSize, nodeSize, learned, canLearn, isPassive));
            }
            totalContentHeight = Math.max(totalContentHeight, columnTotalHeight);
        }
        maxScroll = Math.max(0, totalContentHeight - treeAreaHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        pushZ(graphics);

        if (!interactiveLayout) {
            String message = "窗口过小，请放大窗口或降低 GUI 缩放";
            int maxText = Math.max(0, guiWidth - 4);
            String clipped = maxText == 0 ? "" : font.plainSubstrByWidth(message, maxText);
            if (!clipped.isEmpty()) graphics.drawString(font, clipped, guiLeft + 2, guiTop + Math.max(0, guiHeight / 2 - 4),
                    AcademyColors.TEXT_SECONDARY, false);
            popZ(graphics);
            return;
        }

        drawLegacyChrome(graphics);

        if (fromTerminal) {
            int backX = this.guiLeft + scaled(7), backY = this.guiTop + scaled(7);
            hoveredBack = drawBackButton(graphics, backX, backY, mouseX, mouseY);
        }

        drawDevInfoPanel(graphics);
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player != null ? mc.player.getData(AcademyAttachments.PLAYER_ABILITY) : null;
        boolean resetMode = isResetMode(data);
        boolean consoleMode = data == null || !data.hasAbility() || resetMode;
        hoveredNode = null;
        if (!consoleMode) drawLevelHeaders(graphics);

        graphics.enableScissor(treeAreaLeft, treeAreaTop, treeAreaLeft + treeAreaWidth, treeAreaTop + treeAreaHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int adjustedMouseY = mouseY + scrollOffset;
        if (!consoleMode) {
            drawConnectionLines(graphics);
            drawSkillNodes(graphics, mouseX, adjustedMouseY);
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollBarX = this.guiLeft + guiWidth - 6, thumbHeight = Math.max(12, treeAreaHeight * treeAreaHeight / (treeAreaHeight + maxScroll));
            int thumbY = treeAreaTop + (treeAreaHeight - thumbHeight) * scrollOffset / maxScroll;
            graphics.fill(scrollBarX, treeAreaTop, scrollBarX + 3, treeAreaTop + treeAreaHeight, 0x44FFFFFF);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, COLOR_SCROLL_BAR);
        }

        if (consoleMode) drawLegacyConsole(graphics, data, resetMode);

        // The final 1.12.2 developer opens the centered detail cover on click;
        // it does not add a modern hover tooltip over its authored tree.
        if (!legacyPositionLayout && !detailOpen && !levelDetailOpen && hoveredNode != null) {
            drawTooltip(graphics, mouseX, mouseY);
        }
        drawSelectedActions(graphics, mouseX, mouseY);
        popZ(graphics);
    }

    private void drawLegacyChrome(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int leftX = guiLeft + scaled(4);
        int rightX = guiLeft + scaled(118);
        blitFull(graphics, LEGACY_PARENT_RIGHT, rightX, guiTop, scaled(278), guiHeight, 556, 374);
        blitFull(graphics, LEGACY_UI_RIGHT, rightX, guiTop, scaled(278), guiHeight, 556, 374);
        blitFull(graphics, LEGACY_PARENT_LEFT, leftX, guiTop, scaled(109), guiHeight, 217, 374);
        blitFull(graphics, fromTerminal ? LEGACY_UI_LEFT_TREE : LEGACY_UI_LEFT,
                leftX, guiTop, scaled(109), guiHeight, 217, 374);
        if (!fromTerminal) {
            blitFull(graphics, LEGACY_PARENT_MACHINE, leftX, guiTop, scaled(109), guiHeight, 217, 374);
        }
        blitFull(graphics, LEGACY_TREE_BACKGROUND, treeAreaLeft, treeAreaTop,
                treeAreaWidth, treeAreaHeight, 512, 279);
        RenderSystem.disableBlend();
    }

    private static void blitFull(GuiGraphics graphics, ResourceLocation texture,
                                 int x, int y, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(texture, x, y, width, height, 0, 0, textureWidth, textureHeight,
                textureWidth, textureHeight);
    }

    private void drawSelectedActions(GuiGraphics graphics, int mouseX, int mouseY) {
        actionTop = -1;
        actionWidth = 0;
        networkActionLeft = guiLeft + scaled(8);
        networkActionTop = guiTop + scaled(115);
        networkActionWidth = scaled(100);
        if (devType == null || readOnly) {
            networkActionTop = -1;
            networkActionWidth = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null
                : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        boolean resetMode = isResetMode(data);
        if (resetMode || data == null || !data.hasAbility()) return;

        if (detailOpen && selectedNode != null) {
            drawLegacySkillDetail(graphics, data, mouseX, mouseY);
            return;
        }
        if (levelDetailOpen) {
            drawLegacyLevelDetail(graphics, data, mouseX, mouseY);
            return;
        }

        if (data.canLevelUp()) {
            actionLeft = guiLeft + scaled(64);
            actionTop = guiTop + scaled(82);
            actionWidth = scaled(45);
            drawLegacyActionButton(graphics, BUTTON_LEARN, actionLeft, actionTop, actionWidth,
                    scaled(14), Component.translatable("ac.skill_tree.uplevel",
                            Component.literal("Lv." + (data.getPlayerLevel() + 1))).getString(),
                    mouseX, mouseY, true);
        }
    }

    private void drawLegacySkillDetail(GuiGraphics graphics, PlayerAbilityData data, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xB8000000);
        SkillNode node = selectedNode;
        int centerX = width / 2;
        int iconSize = scaled(50);
        int iconX = centerX - iconSize / 2;
        int iconY = height / 2 - scaled(55);
        graphics.blit(SKILL_BACK, iconX, iconY, iconSize, iconSize, 0, 0, 128, 128, 128, 128);
        int skillIcon = scaled(27);
        RenderUtils.render(skillIcon, skillIcon, centerX - skillIcon / 2,
                iconY + (iconSize - skillIcon) / 2, graphics, node.skill.getIconLocation());
        graphics.blit(node.learned ? SKILL_VIEW_OUTLINE : SKILL_SELECTED,
                iconX, iconY, iconSize, iconSize, 0, 0, 128, 128, 128, 128);

        String title = Component.translatable(node.skill.getTranslationKey()).getString()
                + (node.learned ? "" : " (LV " + node.skill.getLevel() + ")");
        graphics.drawCenteredString(font, title, centerX, iconY + iconSize + scaled(3), 0xFFFFFFFF);
        int textY = iconY + iconSize + scaled(15);
        if (node.learned) {
            graphics.drawCenteredString(font, Component.translatable("ac.skill_tree.skill_exp").getString()
                    + Math.round(data.getProficiency(node.skill.getId()) * 100) + "%", centerX, textY, 0xFFA1E1FF);
            drawCenteredWrapped(graphics, Component.translatable(node.skill.getDescKey()).getString(),
                    centerX, textY + scaled(12), scaled(200), 0xFFE8E8E8);
            return;
        }

        graphics.drawCenteredString(font, Component.translatable("ac.skill_tree.skill_not_learned"),
                centerX, textY, 0xFFFF5555);
        drawLegacyRequirements(graphics, node.skill, data, centerX, textY + scaled(13));
        int stimulations = (int) (3 + node.skill.getLevel() * node.skill.getLevel() * .5f);
        int cost = devType.actualEnergyPerStimulation() * stimulations;
        String prompt = pendingSkillId != null ? serverFeedback.getString()
                : Component.translatable("ac.skill_tree.learn_question", cost).getString();
        graphics.drawCenteredString(font, prompt, centerX, textY + scaled(39), 0xFFDDDDDD);
        boolean learnable = node.canLearn && node.skill.getLevel() <= devType.maxLevel
                && energy >= devType.energyPerTick() && pendingSkillId == null
                && sessionNonce != null && !sessionSpent;
        actionWidth = scaled(36);
        actionLeft = centerX - actionWidth / 2;
        actionTop = textY + scaled(54);
        drawLegacyActionButton(graphics, BUTTON_LEARN, actionLeft, actionTop, actionWidth,
                scaled(16), Component.translatable("ac.skill_tree.learn").getString(), mouseX, mouseY, learnable);
    }

    private void drawLegacyRequirements(GuiGraphics graphics, Skill skill, PlayerAbilityData data,
                                        int centerX, int top) {
        List<RequirementIcon> requirements = new ArrayList<>();
        DevMachineType minimumType = skill.getLevel() <= 2 ? DevMachineType.PORTABLE
                : skill.getLevel() == 3 ? DevMachineType.NORMAL : DevMachineType.ADVANCED;
        ResourceLocation machineIcon = switch (minimumType) {
            case PORTABLE -> tex("textures/item/developer_portable_empty.png");
            case NORMAL -> tex("textures/item/dev_normal.png");
            case ADVANCED -> tex("textures/item/dev_advanced.png");
        };
        requirements.add(new RequirementIcon(machineIcon, devType.ordinal() >= minimumType.ordinal()));
        for (Skill.Prerequisite prerequisite : skill.getPrerequisites()) {
            if (prerequisite.skillId().startsWith("any_level_")) {
                int level = Integer.parseInt(prerequisite.skillId().substring("any_level_".length()));
                boolean accepted = SkillRegistry.getSkillsByLevel(skill.getCategory(), level).stream()
                        .anyMatch(candidate -> data.hasLearnedSkill(candidate.getId()));
                requirements.add(new RequirementIcon(tex("textures/abilities/condition/any" + level + ".png"), accepted));
            } else {
                Skill dependency = SkillRegistry.getSkill(skill.getCategory(), prerequisite.skillId());
                if (dependency != null) {
                    boolean accepted = data.hasLearnedSkill(dependency.getId())
                            && data.getProficiency(dependency.getId()) >= prerequisite.proficiencyRequired();
                    requirements.add(new RequirementIcon(dependency.getIconLocation(), accepted));
                }
            }
        }
        int iconSize = scaled(14);
        int step = scaled(16);
        int totalWidth = requirements.size() * step;
        int labelWidth = font.width(Component.translatable("ac.skill_tree.req"));
        int startX = centerX - totalWidth / 2;
        graphics.drawString(font, Component.translatable("ac.skill_tree.req"),
                startX - labelWidth - scaled(2), top + scaled(3), 0xAAFFFFFF, false);
        for (int i = 0; i < requirements.size(); i++) {
            RequirementIcon requirement = requirements.get(i);
            graphics.setColor(requirement.accepted ? 1f : .32f, requirement.accepted ? 1f : .32f,
                    requirement.accepted ? 1f : .32f, requirement.accepted ? 1f : .65f);
            RenderUtils.render(iconSize, iconSize, startX + i * step, top, graphics, requirement.icon);
        }
        graphics.setColor(1, 1, 1, 1);
    }

    private void drawLegacyLevelDetail(GuiGraphics graphics, PlayerAbilityData data, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xB8000000);
        int centerX = width / 2;
        int iconSize = scaled(50);
        int iconX = centerX - iconSize / 2;
        int iconY = height / 2 - scaled(52);
        graphics.blit(SKILL_BACK, iconX, iconY, iconSize, iconSize, 0, 0, 128, 128, 128, 128);
        int inner = scaled(28);
        RenderUtils.render(inner, inner, centerX - inner / 2, iconY + (iconSize - inner) / 2,
                graphics, data.getCurrentAbility().getIcon());
        graphics.blit(SKILL_SELECTED, iconX, iconY, iconSize, iconSize,
                0, 0, 128, 128, 128, 128);
        String next = "Lv." + (data.getPlayerLevel() + 1);
        graphics.drawCenteredString(font, Component.translatable("ac.skill_tree.uplevel", next),
                centerX, iconY + iconSize + scaled(4), 0xFFFFFFFF);
        int stimulations = 5 * (data.getPlayerLevel() + 1);
        int cost = devType.actualEnergyPerStimulation() * stimulations;
        graphics.drawCenteredString(font, Component.translatable("ac.skill_tree.level_question").getString()
                        + "  " + cost + " IF", centerX, iconY + iconSize + scaled(17), 0xFFDDDDDD);
        boolean enabled = data.canLevelUp() && energy >= devType.energyPerTick()
                && pendingSkillId == null && sessionNonce != null && !sessionSpent;
        actionWidth = scaled(36);
        actionLeft = centerX - actionWidth / 2;
        actionTop = iconY + iconSize + scaled(31);
        drawLegacyActionButton(graphics, BUTTON_LEARN, actionLeft, actionTop, actionWidth,
                scaled(16), Component.translatable("ac.skill_tree.learn").getString(), mouseX, mouseY, enabled);
    }

    private void drawLegacyActionButton(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                        int buttonWidth, int buttonHeight, String label,
                                        int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        graphics.setColor(1, 1, 1, enabled ? hovered ? 1f : .72f : .28f);
        graphics.blit(texture, x, y, buttonWidth, buttonHeight, 0, 0, 200, 64, 200, 64);
        graphics.setColor(1, 1, 1, 1);
        String fitted = font.plainSubstrByWidth(label, Math.max(0, buttonWidth - 2));
        graphics.drawCenteredString(font, fitted, x + buttonWidth / 2,
                y + Math.max(1, (buttonHeight - 8) / 2), 0xFFFFFFFF);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isResetMode(PlayerAbilityData data) {
        Minecraft mc = Minecraft.getInstance();
        return data != null && data.hasAbility() && devType == DevMachineType.ADVANCED
                && mc.player != null && mc.player.getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get());
    }

    private boolean hasDifferentFactor(PlayerAbilityData data) {
        Minecraft mc = Minecraft.getInstance();
        return data != null && mc.player != null && mc.player.getInventory().items.stream().anyMatch(stack ->
                stack.getItem() instanceof BaseFactor factor
                        && factor.getCategory() != data.getCurrentAbility());
    }

    private void drawDevInfoPanel(GuiGraphics graphics) {
        int panelLeft = guiLeft + scaled(6);
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player != null ? mc.player.getData(AcademyAttachments.PLAYER_ABILITY) : null;
        boolean hasAbility = data != null && data.hasAbility();
        int iconSize = scaled(32);
        int abilityTop = guiTop + scaled(68);
        ResourceLocation abilityIcon = hasAbility ? data.getCurrentAbility().getIcon() : LEGACY_NO_CATEGORY_ICON;
        RenderUtils.render(iconSize, iconSize, panelLeft, abilityTop, graphics, abilityIcon);

        String abilityName = hasAbility
                ? Component.translatable("item.academy.factor_" + data.getCurrentAbility().id()).getString()
                : "N/A";
        int textLeft = panelLeft + scaled(32);
        int textWidth = scaled(70);
        graphics.drawString(font, font.plainSubstrByWidth(abilityName, textWidth),
                textLeft, abilityTop + scaled(2), 0xFFFFFFFF, false);
        if (hasAbility) {
            int progressTop = abilityTop + scaled(14);
            drawLegacyBar(graphics, textLeft, progressTop, textWidth, data.getLevelProgress(), 0xFFFFFFFF);
            graphics.drawString(font, "EXP " + Math.round(data.getLevelProgress() * 100) + "%",
                    textLeft, abilityTop + scaled(18), 0xFFFFFFFF, false);
            String level = "Lv." + data.getPlayerLevel();
            graphics.drawString(font, level, panelLeft + scaled(72), abilityTop + scaled(18),
                    0xFF1177D6, false);
        }

        if (fromTerminal || devType == null) return;
        // page_developer.xml centers these children inside parent_left
        // (x=4,width=108.5). Preserve its half-pixel coordinates before
        // scaling; rounding each offset independently shifted the bars and
        // labels by up to four physical pixels at GUI scale 2.
        int lineLeft = guiLeft + scaled(4 + (108.5 - 100) / 2);
        int lineWidth = scaled(100);
        int barLeft = guiLeft + scaled(4 + (108.5 - 97) / 2);
        graphics.drawString(font, Component.translatable("ac.skill_tree.current_node"),
                lineLeft, guiTop + scaled((187 - 12) / 2.0 + 17), 0xFFFFFFFF, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(.4f, .4f, .4f, .8f);
        int nodeButtonTop = guiTop + scaled((187 - 16) / 2.0 + 29);
        graphics.blit(LEGACY_LIST_BUTTON, lineLeft, nodeButtonTop, lineWidth, scaled(16),
                0, 0, 300, 32, 300, 32);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        int nodeIcon = scaled(12);
        RenderUtils.render(nodeIcon, nodeIcon, lineLeft + scaled(3), nodeButtonTop + scaled(2), graphics, LEGACY_NODE_ICON);
        String displayedNode = linkedNodeName.isBlank()
                ? Component.translatable("ac.skill_tree.not_connected").getString() : linkedNodeName;
        graphics.drawString(font, font.plainSubstrByWidth(displayedNode, lineWidth - scaled(22)),
                lineLeft + scaled(18), nodeButtonTop + scaled(4), 0xFFFFFFFF, false);

        graphics.drawString(font, Component.translatable("ac.skill_tree.power"),
                lineLeft, guiTop + scaled((187 - 12) / 2.0 + 43), 0xFFFFFFFF, false);
        drawLegacyBar(graphics, barLeft, guiTop + scaled((187 - 8) / 2.0 + 55.5), scaled(97),
                maxEnergy <= 0 ? 0 : energy / (double) maxEnergy, 0xFFFCC532);
        graphics.drawString(font, Component.translatable("ac.skill_tree.sync_rate"),
                lineLeft, guiTop + scaled((187 - 12) / 2.0 + 66), 0xFFFFFFFF, false);
        drawLegacyBar(graphics, barLeft, guiTop + scaled((187 - 8) / 2.0 + 77.5), scaled(97),
                devType.syncRate / 100.0, 0xFF32A4FC);
    }

    private void drawLegacyBar(GuiGraphics graphics, int x, int y, int width, double progress, int color) {
        int height = Math.max(2, scaled(8));
        // The authored parent_background_developermachine texture already
        // contains the black track and white outline. The legacy ProgressBar
        // draws only the colored fill on top of it.
        int filled = (int) Math.round(width * Math.clamp(progress, 0, 1));
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, color);
    }

    private void drawLegacyConsole(GuiGraphics graphics, PlayerAbilityData data, boolean resetMode) {
        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player == null ? "User" : mc.player.getName().getString();
        List<FormattedCharSequence> output = new ArrayList<>();
        addConsoleLines(output, Component.translatable("ac.skill_tree.console.init", playerName).getString());
        if (resetMode) {
            addConsoleLines(output, Component.translatable("ac.skill_tree.console.override").getString());
            addConsoleLines(output, hasDifferentFactor(data)
                    ? Component.translatable("ac.skill_tree.console.factor_ready").getString()
                    : Component.translatable("ac.skill_tree.console.factor_missing").getString());
        } else {
            addConsoleLines(output, Component.translatable("ac.skill_tree.console.invalid_cat").getString());
            if (devType != null && !readOnly) {
                addConsoleLines(output, Component.translatable("ac.skill_tree.console.learn_hint").getString());
            }
        }
        if (!serverFeedback.getString().isBlank()) addConsoleLines(output, serverFeedback.getString());

        int maxRows = Math.max(1, treeAreaHeight / 10 - 2);
        int first = Math.max(0, output.size() - maxRows);
        int y = treeAreaTop + scaled(5);
        for (int i = first; i < output.size(); i++) {
            graphics.drawString(font, output.get(i), treeAreaLeft + scaled(5), y, 0xFFDDDDDD, false);
            y += 10;
        }
        String cursor = (System.currentTimeMillis() / 500L & 1L) == 0 ? "_" : "";
        String prompt = "OS >" + (pendingSkillId == null ? consoleInput + cursor
                : Component.translatable("ac.skill_tree.dev_developing").getString());
        graphics.drawString(font, font.plainSubstrByWidth(prompt, treeAreaWidth - scaled(10)),
                treeAreaLeft + scaled(5), treeAreaTop + treeAreaHeight - scaled(12), 0xFFFFFFFF, false);
    }

    private void addConsoleLines(List<FormattedCharSequence> output, String text) {
        for (String line : text.replace("\\n", "\n").split("\n", -1)) {
            if (line.isEmpty()) {
                output.add(Component.empty().getVisualOrderText());
            } else {
                output.addAll(font.split(Component.literal(line), Math.max(16, treeAreaWidth - scaled(10))));
            }
        }
    }

    private void drawCenteredWrapped(GuiGraphics graphics, String text, int centerX, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(text), Math.max(16, width))) {
            graphics.drawString(font, line, centerX - font.width(line) / 2, y, color, false);
            y += 10;
        }
    }

    private void drawLevelHeaders(GuiGraphics graphics) {
        if (legacyPositionLayout) return;
        for (int level = 1; level <= 5; level++) {
            int colX = this.treeAreaLeft + (level - 1) * colWidth;
            String levelText = "Lv." + level;
            int tw = this.font.width(levelText);
            int nodeSize = Math.min(SKILL_HEIGHT, skillWidth);
            int nodeX = colX + (skillWidth - nodeSize) / 2;
            graphics.drawString(this.font, levelText, nodeX + (nodeSize - tw) / 2, this.treeAreaTop + 2, AcademyColors.TEXT_SECONDARY);
        }
    }

    private void drawConnectionLines(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;

        for (SkillNode node : skillNodes) {
            List<Skill.Prerequisite> linePrerequisites = node.skill.getPrerequisites();
            if (legacyPositionLayout) {
                // 1.0.7 rendered only Skill.parent. addSkillDep conditions did
                // not become extra graph edges.
                linePrerequisites = linePrerequisites.stream()
                        .filter(prereq -> !prereq.skillId().startsWith("any_level_"))
                        .limit(1).toList();
            }
            for (Skill.Prerequisite prereq : linePrerequisites) {
                String prereqId = prereq.skillId();
                if (prereqId.startsWith("any_level_")) continue;
                SkillNode prereqNode = findNode(prereqId);
                if (prereqNode == null) continue;

                double x1 = prereqNode.x + prereqNode.w / 2.0;
                double y1 = prereqNode.y + prereqNode.h / 2.0;
                double x2 = node.x + node.w / 2.0;
                double y2 = node.y + node.h / 2.0;
                double dx = x2 - x1;
                double dy = y2 - y1;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance <= scaled(24)) continue;
                double trim = 12.2 * guiWidth / LEGACY_GUI_WIDTH;
                double startX = x1 + dx / distance * trim;
                double startY = y1 + dy / distance * trim;
                double length = distance - trim * 2;
                float alpha = legacyNodeAlpha(node, data) * (node.learned ? 1f : .4f);
                int lineWidth = Math.max(2, scaled(6));
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.setColor(1, 1, 1, alpha);
                graphics.pose().pushPose();
                graphics.pose().translate(startX, startY, 0);
                graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
                graphics.blit(LEGACY_LINE, 0, -lineWidth / 2, Math.max(1, (int) Math.round(length)),
                        lineWidth, 0, 0, 16, 16, 16, 16);
                graphics.pose().popPose();
                graphics.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            }
        }
    }

    private void drawSkillNodes(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;

        for (SkillNode node : skillNodes) {
            boolean isHovered = mouseX >= node.x && mouseX < node.x + node.w && mouseY >= node.y && mouseY < node.y + node.h;
            if (isHovered) hoveredNode = node;

            if (isHovered) {
                graphics.pose().pushPose();
                float cx = node.x + node.w / 2f, cy = node.y + node.h / 2f;
                graphics.pose().translate(cx, cy, 0);
                graphics.pose().scale(1.15f, 1.15f, 1.0f);
                graphics.pose().translate(-cx, -cy, 0);
            }

            float alpha = legacyNodeAlpha(node, data);
            int backSize = Math.max(node.w, scaled(23));
            int outlineSize = Math.max(backSize, scaled(31));
            int backX = node.x + (node.w - backSize) / 2;
            int backY = node.y + (node.h - backSize) / 2;
            int outlineX = node.x + (node.w - outlineSize) / 2;
            int outlineY = node.y + (node.h - outlineSize) / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1, 1, 1, alpha);
            graphics.blit(SKILL_BACK, backX, backY, backSize, backSize, 0, 0, 128, 128, 128, 128);
            graphics.setColor(.2f, .2f, .2f, alpha * .6f);
            graphics.blit(SKILL_OUTLINE, outlineX, outlineY, outlineSize, outlineSize,
                    0, 0, 128, 128, 128, 128);
            ResourceLocation icon = node.skill.getIconLocation();
            int iconSize = Math.max(8, scaled(14));
            float iconTone = node.learned ? 1f : .42f;
            graphics.setColor(iconTone, iconTone, iconTone, alpha);
            RenderUtils.render(iconSize, iconSize,
                    node.x + (node.w - iconSize) / 2,
                    node.y + (node.h - iconSize) / 2, graphics, icon);
            if (node.learned) {
                float proficiency = Math.clamp(data.getProficiency(node.skill.getId()), 0, 1);
                graphics.setColor(1, 1, 1, .2f + proficiency * .8f);
                graphics.blit(SKILL_OUTLINE, outlineX, outlineY, outlineSize, outlineSize,
                        0, 0, 128, 128, 128, 128);
            }
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();

            if (isHovered) graphics.pose().popPose();
        }
    }

    private float legacyNodeAlpha(SkillNode node, PlayerAbilityData data) {
        if (node.learned) return 1f;
        Skill.Prerequisite parent = node.skill.getPrerequisites().stream()
                .filter(prerequisite -> !prerequisite.skillId().startsWith("any_level_"))
                .findFirst().orElse(null);
        return parent == null || data.hasLearnedSkill(parent.skillId()) ? .7f : .25f;
    }

    private void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Skill skill = hoveredNode.skill;
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(skill.getTranslationKey()));

        String desc = Component.translatable(skill.getDescKey()).getString();
        if (!desc.equals(skill.getDescKey())) {
            int maxCharsPerLine = Math.max(16, guiWidth / 18);
            for (int i = 0; i < desc.length(); i += maxCharsPerLine) {
                tooltip.add(Component.literal("§7" + desc.substring(i, Math.min(i + maxCharsPerLine, desc.length()))));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§f等级: " + skill.getLevel() + "  类型: " + (skill.getType() == SkillType.PASSIVE ? "被动" : "主动")));
        if (skill.getBaseCpCost() > 0) {
            tooltip.add(Component.literal("§b计算力: " + (int) skill.getBaseCpCost() + "  §c过载: " + (int) skill.getBaseOverload()));
        }

        if (hoveredNode.learned) {
            // 已学习状态
            tooltip.add(Component.literal("§a[已学习]"));
            if (!hoveredNode.isPassive) {
                float prof = data.getProficiency(skill.getId());
                tooltip.add(Component.literal("§e熟练度: " + String.format("%.1f%%", prof * 100)));
            }
            // 开发机能量不足时追加提示
            if (!fromTerminal && devType != null && energy <= 0) {
                tooltip.add(Component.literal("§c[开发机能量不足] 该技能暂不可用"));
            }
        } else if (hoveredNode.canLearn) {
            // 可学习状态
            if (readOnly) {
                tooltip.add(Component.literal("§7[仅查看] 请使用开发机学习"));
            } else if (skill.getLevel() > devType.maxLevel) {
                tooltip.add(Component.literal("§c[同步率不足] 该开发机无法支持此等级技能"));
            } else if (!fromTerminal && devType != null && energy <= 0) {
                // 能量不足时优先提示
                tooltip.add(Component.literal("§c[开发机能量不足] 暂无法学习该技能"));
            } else {
                int stimulations = (int) (3 + skill.getLevel() * skill.getLevel() * .5f);
                int cost = devType.actualEnergyPerStimulation() * stimulations;
                float seconds = stimulations * devType.developmentTicksPerStimulation() / 20f;
                tooltip.add(Component.literal("§b[开始开发] 预计: " + cost + " IF / " + String.format("%.1f",seconds) + "秒"));
            }
        } else {
            // 未解锁状态
            tooltip.add(Component.literal("§c[未解锁]"));
            if (skill.getLevel() > data.getPlayerLevel()) {
                tooltip.add(Component.literal("§c✗ §7需要能力等级 Lv." + skill.getLevel()
                        + "（当前 Lv." + data.getPlayerLevel() + "）"));
            }
            for (Skill.Prerequisite prereq : skill.getPrerequisites()) {
                String prereqId = prereq.skillId();
                if (prereqId.startsWith("any_level_")) {
                    int reqLv = Integer.parseInt(prereqId.substring("any_level_".length()));
                    tooltip.add(Component.literal("§7  需要任意 Lv." + reqLv + " 技能"));
                } else {
                    String prereqName = Component.translatable("item.academy.factor_" + skill.getCategory().id() + "." + prereqId).getString();
                    boolean met = data.hasLearnedSkill(prereqId) && data.getProficiency(prereqId) >= prereq.proficiencyRequired();
                    String status = met ? "§a✓" : "§c✗";
                    String reqText = prereq.proficiencyRequired() > 0
                            ? String.format(" %.0f%%", prereq.proficiencyRequired() * 100)
                            : "";
                    tooltip.add(Component.literal(status + " §7" + prereqName + reqText));
                }
            }
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        graphics.pose().popPose();
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!interactiveLayout) return super.mouseClicked(mouseX, mouseY, button);
        if (fromTerminal && hoveredBack && button == 0) {
            Minecraft.getInstance().setScreen(new DataTerminalGui());
            return true;
        }
        Minecraft clickMinecraft = Minecraft.getInstance();
        PlayerAbilityData clickData = clickMinecraft.player == null ? null
                : clickMinecraft.player.getData(AcademyAttachments.PLAYER_ABILITY);
        boolean resetMode = isResetMode(clickData);

        if (button == 0 && (detailOpen || levelDetailOpen)) {
            if (inside(mouseX, mouseY, actionLeft, actionTop, actionWidth, scaled(16))) {
                if (levelDetailOpen && clickData != null && clickData.canLevelUp()) {
                    beginAction(LearnSkillPacket.LEVEL_UP_ACTION, clickData);
                } else if (detailOpen && selectedNode != null && !selectedNode.learned
                        && selectedNode.canLearn && selectedNode.skill.getLevel() <= devType.maxLevel) {
                    beginAction(selectedNode.skill.getId(), clickData);
                }
            } else if (pendingSkillId == null) {
                detailOpen = false;
                levelDetailOpen = false;
                selectedNode = null;
                serverFeedback = Component.empty();
            }
            return true;
        }
        if (!resetMode && hoveredNode != null && button == 0) {
            selectedNode = hoveredNode;
            detailOpen = true;
            levelDetailOpen = false;
            serverFeedback = Component.empty();
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, actionLeft, actionTop, actionWidth, 14)) {
            if (clickData != null && clickData.hasAbility() && clickData.canLevelUp()) {
                levelDetailOpen = true;
                detailOpen = false;
            }
            return true;
        }
        if (button == 0 && devType != DevMachineType.PORTABLE && devPos != null && sessionNonce != null
                && inside(mouseX, mouseY, networkActionLeft, networkActionTop, networkActionWidth, scaled(16))) {
            PacketDistributor.sendToServer(new com.mohistmc.academy.network.OpenDevNetworkPacket(devPos, sessionNonce));
            return true;
        }
        if (maxScroll > 0) {
            int scrollBarX = this.guiLeft + guiWidth - 6;
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 3 && mouseY >= treeAreaTop && mouseY <= treeAreaTop + treeAreaHeight) {
                isScrolling = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void beginAction(String action, PlayerAbilityData data) {
        if (data == null || readOnly || devType == null || energy <= 0 || pendingSkillId != null
                || sessionNonce == null || sessionSpent) return;
        pendingSkillId = action;
        pendingLevel = data.getPlayerLevel();
        Minecraft mc = Minecraft.getInstance();
        pendingSince = mc.level == null ? 0 : mc.level.getGameTime();
        serverFeedback = Component.translatable("ac.skill_tree.console.request_sent");
        PacketDistributor.sendToServer(new LearnSkillPacket(action, devType.ordinal(),
                java.util.Optional.ofNullable(devPos), sessionNonce));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (isLegacyConsole(data) && pendingSkillId == null && Character.isLetter(codePoint)
                && consoleInput.length() < 8) {
            consoleInput += Character.toLowerCase(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((detailOpen || levelDetailOpen) && keyCode == GLFW.GLFW_KEY_ESCAPE && pendingSkillId == null) {
            detailOpen = false;
            levelDetailOpen = false;
            selectedNode = null;
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (isLegacyConsole(data)) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !consoleInput.isEmpty()) {
                consoleInput = consoleInput.substring(0, consoleInput.length() - 1);
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && pendingSkillId == null) {
                String command = consoleInput.trim();
                consoleInput = "";
                if (!data.hasAbility() && "learn".equals(command)) {
                    beginAction(LearnSkillPacket.INDUCTION_ACTION, data);
                } else if (isResetMode(data) && "reset".equals(command)) {
                    beginAction(LearnSkillPacket.RESET_ACTION, data);
                } else {
                    serverFeedback = Component.translatable("ac.skill_tree.console.invalid_command");
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isLegacyConsole(PlayerAbilityData data) {
        return data != null && (!data.hasAbility() || isResetMode(data));
    }

    /**
     * Property-gated integration hook used by the isolated client visual gate.
     * It deliberately routes through the real hit box and packet path instead
     * of constructing the wireless screen directly, so menu registration,
     * the learning nonce and C2S/S2C ordering are all exercised.
     */
    public boolean clickNetworkButtonForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || networkActionTop < 0) return false;
        return mouseClicked(networkActionLeft + Math.max(1, networkActionWidth / 2.0),
                networkActionTop + Math.max(1, scaled(8)), 0);
    }

    /** Confirms the production screen is using the final 1.12.2 canvas and console state. */
    public boolean isLegacyConsoleForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || guiWidth <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null
                : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        return isLegacyConsole(data) && treeAreaWidth == scaled(257) && treeAreaHeight == scaled(139);
    }

    /** Opens a real skill hit box so the client gate can inspect the legacy detail cover. */
    public boolean openFirstSkillDetailForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate") || skillNodes.isEmpty()) return false;
        hoveredNode = skillNodes.getFirst();
        return mouseClicked(hoveredNode.x + hoveredNode.w / 2.0,
                hoveredNode.y + hoveredNode.h / 2.0, 0);
    }

    public boolean isSkillDetailOpenForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") && detailOpen && selectedNode != null;
    }

    public boolean closeSkillDetailForVisualGate() {
        if (!isSkillDetailOpenForVisualGate()) return false;
        return keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    /** Read-only assertion hook for the reset presentation gate. */
    public boolean isResetModeForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate")) return false;
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null
                : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        return isResetMode(data);
    }

    /** Confirms that reset guidance sees the same inventory factor as the server action. */
    public boolean hasDifferentFactorForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate")) return false;
        Minecraft mc = Minecraft.getInstance();
        PlayerAbilityData data = mc.player == null ? null
                : mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        return hasDifferentFactor(data);
    }

    @Override
    public void removed() {
        super.removed();
        if (sessionNonce != null) {
            PacketDistributor.sendToServer(new CloseDevLearningSessionPacket(sessionNonce));
        }
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isScrolling) { updateScrollFromMouse(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { isScrolling = false; return super.mouseReleased(mouseX, mouseY, button); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!interactiveLayout) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (maxScroll > 0 && mouseX >= treeAreaLeft && mouseX <= treeAreaLeft + treeAreaWidth
                && mouseY >= treeAreaTop && mouseY <= treeAreaTop + treeAreaHeight) {
            scrollOffset = (int) Math.clamp(scrollOffset - scrollY * 12, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateScrollFromMouse(double mouseY) {
        int scrollBarHeight = treeAreaHeight;
        int thumbHeight = Math.max(12, scrollBarHeight * treeAreaHeight / (treeAreaHeight + maxScroll));
        double ratio = (mouseY - treeAreaTop - thumbHeight / 2.0) / (scrollBarHeight - thumbHeight);
        scrollOffset = (int) Math.clamp(ratio * maxScroll, 0, maxScroll);
    }

    private SkillNode findNode(String skillId) {
        for (SkillNode node : skillNodes) if (node.skill.getId().equals(skillId)) return node;
        return null;
    }

    private record RequirementIcon(ResourceLocation icon, boolean accepted) {}
    private record SkillNode(Skill skill, int x, int y, int w, int h, boolean learned, boolean canLearn, boolean isPassive) {}
}
