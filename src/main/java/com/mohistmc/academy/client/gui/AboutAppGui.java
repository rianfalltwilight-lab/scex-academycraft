package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.utils.RenderUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Functional port of the pre-installed AcademyCraft 1.12.2 About app. */
@OnlyIn(Dist.CLIENT)
public final class AboutAppGui extends Screen {
    private static final ResourceLocation BACKGROUND = resource("textures/guis/about/bg.png");
    private static final ResourceLocation BUTTON_GLOW = resource("textures/guis/about/button_glow.png");
    private static final ResourceLocation ABOUT_CONFIG = resource("config/about.conf");
    private static final Pattern QUOTED = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final int SOURCE_WIDTH = 742;
    private static final int SOURCE_HEIGHT = 923;
    private static final float LEGACY_SCALE = 0.25f;
    private static final int LINE_HEIGHT = 10;

    private final boolean fromTerminal;
    private final List<String> credits = new ArrayList<>();
    private final List<String> donation = new ArrayList<>();
    private boolean donateTab;
    private int scroll;
    private int maxScroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int tabY;
    private int tabWidth;
    private int tabHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private String hoveredUrl;

    public AboutAppGui(boolean fromTerminal) {
        super(Component.translatable("item.academy.app_about"));
        this.fromTerminal = fromTerminal;
        loadOfficialText();
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, path);
    }

    @Override
    protected void init() {
        float scale = Math.min(LEGACY_SCALE,
                Math.min((width - 8.0f) / SOURCE_WIDTH, (height - 8.0f) / SOURCE_HEIGHT));
        panelWidth = Math.max(1, Math.round(SOURCE_WIDTH * scale));
        panelHeight = Math.max(1, Math.round(SOURCE_HEIGHT * scale));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int areaX = panelX + Math.round(53 * scale);
        tabY = panelY + Math.round(266 * scale);
        tabWidth = Math.round(315 * scale);
        tabHeight = Math.max(12, Math.round(58 * scale));
        contentX = areaX + 5;
        contentY = tabY + tabHeight + 3;
        contentWidth = Math.round(620 * scale) - 10;
        contentHeight = Math.round(540 * scale) - 4;
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x99000000);
        RenderUtils.render(panelWidth, panelHeight, panelX, panelY, graphics, BACKGROUND);

        int firstTabX = panelX + Math.round(53 * panelWidth / (float) SOURCE_WIDTH);
        drawTab(graphics, firstTabX, tabY, tabWidth, tabHeight, "Credits", !donateTab,
                0x552984F1);
        drawTab(graphics, firstTabX + tabWidth, tabY, tabWidth, tabHeight, "Donate", donateTab,
                0x55E79CFF);

        List<String> lines = donateTab ? donation : credits;
        maxScroll = Math.max(0, lines.size() * LINE_HEIGHT - contentHeight + 4);
        clampScroll();
        hoveredUrl = null;
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        int y = contentY + 3 - scroll;
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            String text = raw;
            int color = 0xFFE0E5EB;
            String url = null;
            if (raw.startsWith("!!")) {
                int split = raw.indexOf('|');
                text = split > 2 ? raw.substring(2, split) : raw.substring(2);
                url = split > 2 ? raw.substring(split + 1) : null;
                color = 0xFF5BB4FF;
            } else if (!donateTab && index < 2) {
                color = 0xFF8ECBFF;
            } else if (isCreditHeading(raw)) {
                color = 0xFFFFFFFF;
            }
            if (y + LINE_HEIGHT >= contentY && y < contentY + contentHeight) {
                boolean hovered = url != null && mouseX >= contentX + 3
                        && mouseX <= contentX + 3 + font.width(text)
                        && mouseY >= y && mouseY < y + LINE_HEIGHT;
                graphics.drawString(font, text, contentX + 3, y,
                        hovered ? 0xFF8ECBFF : color, false);
                if (hovered) hoveredUrl = url;
            }
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = contentX + contentWidth - 3;
            int thumbHeight = Math.max(12, contentHeight * contentHeight
                    / Math.max(contentHeight, lines.size() * LINE_HEIGHT));
            int thumbY = contentY + (contentHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, contentY, trackX + 2, contentY + contentHeight, 0x553D4650);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCFFFFFF);
        }
        if (hoveredUrl != null) {
            graphics.renderTooltip(font, Component.literal(hoveredUrl), mouseX, mouseY);
        }
    }

    private void drawTab(GuiGraphics graphics, int x, int y, int w, int h,
                         String text, boolean selected, int selectedColor) {
        graphics.fill(x, y, x + w, y + h, selected ? selectedColor : 0x22FFFFFF);
        if (selected) {
            RenderUtils.render(w + 4, h + 6, x - 2, y - 3, graphics, BUTTON_GLOW);
        }
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2,
                selected ? 0xFF3D3F4B : 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int firstTabX = panelX + Math.round(53 * panelWidth / (float) SOURCE_WIDTH);
            if (inside(mouseX, mouseY, firstTabX, tabY, tabWidth, tabHeight)) {
                donateTab = false;
                scroll = 0;
                return true;
            }
            if (inside(mouseX, mouseY, firstTabX + tabWidth, tabY, tabWidth, tabHeight)) {
                donateTab = true;
                scroll = 0;
                return true;
            }
            if (hoveredUrl != null) {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.keyboardHandler.setClipboard(hoveredUrl);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("§7[AcademyCraft] §b链接已复制: " + hoveredUrl), true);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            scroll -= (int) Math.signum(verticalAmount) * 20;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, Math.max(0, maxScroll));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(fromTerminal ? new DataTerminalGui() : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void loadOfficialText() {
        String config = readConfig();
        if (!config.isBlank()) {
            int donationStart = config.indexOf("donation {");
            String creditSection = donationStart < 0 ? config : config.substring(0, donationStart);
            credits.addAll(quotedStrings(creditSection));

            String language = Minecraft.getInstance().getLanguageManager().getSelected();
            String donationSection = donationStart < 0 ? "" : config.substring(donationStart);
            String localized = listBody(donationSection, language);
            if (localized.isBlank()) localized = listBody(donationSection, "en_us");
            donation.addAll(quotedStrings(localized));
        }
        if (credits.isEmpty()) {
            credits.addAll(List.of("Presented by Lambda Innovation", "ac.li-dev.cn",
                    "AcademyCraft 1.12.2", "Thank you for playing!"));
        }
        if (donation.isEmpty()) {
            donation.addAll(List.of("Thank you for playing AcademyCraft!",
                    "The historical support links are preserved for project attribution."));
        }
    }

    private static String readConfig() {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(ABOUT_CONFIG);
            if (resource.isEmpty()) return "";
            try (var reader = new BufferedReader(new InputStreamReader(
                    resource.get().open(), StandardCharsets.UTF_8))) {
                return reader.lines().reduce("", (left, right) -> left + right + "\n");
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String listBody(String section, String key) {
        int start = section.indexOf(key + ":");
        if (start < 0) return "";
        int open = section.indexOf('[', start);
        int close = open < 0 ? -1 : section.indexOf(']', open);
        return open < 0 || close < 0 ? "" : section.substring(open + 1, close);
    }

    private static List<String> quotedStrings(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\"));
        }
        return result;
    }

    private static boolean isCreditHeading(String text) {
        return switch (text) {
            case "Project Direction", "Game Design", "Programming", "Art", "QA",
                    "Website", "Localization", "GitHub Contributors", "Donators",
                    "Thank you for playing!" -> true;
            default -> false;
        };
    }
}
