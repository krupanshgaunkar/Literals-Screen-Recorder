package com.literalscreenrecorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class LiteralScreenRecorderClient implements ClientModInitializer {
    private boolean wasMenuPressed = false;
    private boolean wasStartStopPressed = false;
    private boolean wasClipPressed = false;

    @Override
    public void onInitializeClient() {
        RecorderConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getWindow() == null) return;
            if (client.screen != null) return;

            long windowHandle = client.getWindow().getWindow();
            RecorderConfig cfg = RecorderConfig.INSTANCE;

            // Open GUI Key (Default: N)
            if (cfg.menuKey != GLFW.GLFW_KEY_UNKNOWN) {
                boolean isMenuPressed = InputConstants.isKeyDown(windowHandle, cfg.menuKey);
                if (isMenuPressed && !wasMenuPressed) {
                    client.setScreen(new RecorderScreen(null));
                }
                wasMenuPressed = isMenuPressed;
            }

            // Start/Stop Key (Default: Not Bound)
            if (cfg.startStopKey != GLFW.GLFW_KEY_UNKNOWN) {
                boolean isPressed = InputConstants.isKeyDown(windowHandle, cfg.startStopKey);
                if (isPressed && !wasStartStopPressed) {
                    toggleRecording();
                }
                wasStartStopPressed = isPressed;
            }

            // Clip Key (Default: Not Bound)
            if (cfg.clipKey != GLFW.GLFW_KEY_UNKNOWN) {
                boolean isPressed = InputConstants.isKeyDown(windowHandle, cfg.clipKey);
                if (isPressed && !wasClipPressed) {
                    saveClip();
                }
                wasClipPressed = isPressed;
            }
        });
    }

    public static void toggleRecording() {
        RecorderConfig cfg = RecorderConfig.INSTANCE;
        cfg.isRecording = !cfg.isRecording;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (cfg.isRecording) {
                client.player.displayClientMessage(Component.literal("● [Literal Recorder] Recording Started (" + cfg.quality + " @ " + cfg.fps + " FPS)").withStyle(ChatFormatting.GREEN), true);
            } else {
                client.player.displayClientMessage(Component.literal("■ [Literal Recorder] Recording Stopped & Saved!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public static void saveClip() {
        RecorderConfig cfg = RecorderConfig.INSTANCE;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            String timeLabel = cfg.clipDuration == 60 ? "1 minute" : cfg.clipDuration + " seconds";
            client.player.displayClientMessage(Component.literal("★ [Literal Recorder] Saved clip of the last " + timeLabel + "!").withStyle(ChatFormatting.AQUA), true);
        }
    }

    public static class RecorderConfig {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "literal_screen_recorder.json");
        public static RecorderConfig INSTANCE = new RecorderConfig();

        public int clipDuration = 30;
        public String quality = "High";
        public int fps = 60;
        public int menuKey = GLFW.GLFW_KEY_N;
        public int startStopKey = GLFW.GLFW_KEY_UNKNOWN;
        public int clipKey = GLFW.GLFW_KEY_UNKNOWN;
        public transient boolean isRecording = false;

        public static void load() {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    INSTANCE = GSON.fromJson(reader, RecorderConfig.class);
                } catch (IOException ignored) {}
            } else {
                save();
            }
        }

        public static void save() {
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            } catch (IOException ignored) {}
        }

        public void cycleClipDuration() {
            clipDuration = (clipDuration == 15) ? 30 : (clipDuration == 30 ? 60 : 15);
            save();
        }

        public void cycleQuality() {
            quality = switch (quality) {
                case "Low" -> "Medium";
                case "Medium" -> "High";
                case "High" -> "Ultra";
                default -> "Low";
            };
            save();
        }

        public void cycleFps() {
            fps = (fps == 30) ? 60 : (fps == 60 ? 120 : 30);
            save();
        }
    }

    public static class RecorderScreen extends Screen {
        private final Screen parent;
        private int listeningForKey = 0;
        private Button clipBtn, bindMenuBtn, bindStartStopBtn, bindClipBtn;

        public RecorderScreen(Screen parent) {
            super(Component.literal("Literal Screen Recorder"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            RecorderConfig cfg = RecorderConfig.INSTANCE;
            int centerX = this.width / 2;
            int startY = this.height / 2 - 80;

            Button startStopBtn = Button.builder(getRecordButtonText(), btn -> {
                toggleRecording();
                btn.setMessage(getRecordButtonText());
            }).bounds(centerX - 155, startY, 150, 20).build();

            clipBtn = Button.builder(Component.literal("Save Last " + getDurationLabel(cfg.clipDuration)).withStyle(ChatFormatting.AQUA), btn -> saveClip())
                    .bounds(centerX + 5, startY, 150, 20).build();

            Button durationBtn = Button.builder(Component.literal("Clip Length: " + getDurationLabel(cfg.clipDuration)), btn -> {
                cfg.cycleClipDuration();
                btn.setMessage(Component.literal("Clip Length: " + getDurationLabel(cfg.clipDuration)));
                clipBtn.setMessage(Component.literal("Save Last " + getDurationLabel(cfg.clipDuration)).withStyle(ChatFormatting.AQUA));
            }).bounds(centerX - 155, startY + 30, 150, 20).build();

            Button qualityBtn = Button.builder(Component.literal("Quality: " + cfg.quality), btn -> {
                cfg.cycleQuality();
                btn.setMessage(Component.literal("Quality: " + cfg.quality));
            }).bounds(centerX + 5, startY + 30, 150, 20).build();

            Button fpsBtn = Button.builder(Component.literal("Target FPS: " + cfg.fps), btn -> {
                cfg.cycleFps();
                btn.setMessage(Component.literal("Target FPS: " + cfg.fps));
            }).bounds(centerX - 75, startY + 55, 150, 20).build();

            bindMenuBtn = Button.builder(getKeybindText("Open GUI Key", cfg.menuKey, 3), btn -> {
                listeningForKey = (listeningForKey == 3) ? 0 : 3;
                updateKeybindButtons();
            }).bounds(centerX - 75, startY + 95, 150, 20).build();

            bindStartStopBtn = Button.builder(getKeybindText("Start/Stop Key", cfg.startStopKey, 1), btn -> {
                listeningForKey = (listeningForKey == 1) ? 0 : 1;
                updateKeybindButtons();
            }).bounds(centerX - 155, startY + 120, 150, 20).build();

            bindClipBtn = Button.builder(getKeybindText("Clip Key", cfg.clipKey, 2), btn -> {
                listeningForKey = (listeningForKey == 2) ? 0 : 2;
                updateKeybindButtons();
            }).bounds(centerX + 5, startY + 120, 150, 20).build();

            Button doneBtn = Button.builder(Component.literal("Done"), btn -> this.onClose())
                    .bounds(centerX - 50, startY + 150, 100, 20).build();

            this.addRenderableWidget(startStopBtn);
            this.addRenderableWidget(clipBtn);
            this.addRenderableWidget(durationBtn);
            this.addRenderableWidget(qualityBtn);
            this.addRenderableWidget(fpsBtn);
            this.addRenderableWidget(bindMenuBtn);
            this.addRenderableWidget(bindStartStopBtn);
            this.addRenderableWidget(bindClipBtn);
            this.addRenderableWidget(doneBtn);
        }

        private Component getRecordButtonText() {
            return RecorderConfig.INSTANCE.isRecording
                    ? Component.literal("■ Stop Recording").withStyle(ChatFormatting.RED)
                    : Component.literal("● Start Recording").withStyle(ChatFormatting.GREEN);
        }

        private String getDurationLabel(int sec) {
            return sec == 60 ? "1 Min" : sec + " Sec";
        }

        private Component getKeybindText(String label, int keyCode, int buttonId) {
            if (listeningForKey == buttonId) return Component.literal(label + ": > Press Key <").withStyle(ChatFormatting.YELLOW);
            if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return Component.literal(label + ": Not Bound").withStyle(ChatFormatting.GRAY);
            return Component.literal(label + ": " + InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString());
        }

        private void updateKeybindButtons() {
            RecorderConfig cfg = RecorderConfig.INSTANCE;
            bindMenuBtn.setMessage(getKeybindText("Open GUI Key", cfg.menuKey, 3));
            bindStartStopBtn.setMessage(getKeybindText("Start/Stop Key", cfg.startStopKey, 1));
            bindClipBtn.setMessage(getKeybindText("Clip Key", cfg.clipKey, 2));
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (listeningForKey != 0) {
                int newKey = (keyCode == GLFW.GLFW_KEY_ESCAPE) ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
                if (listeningForKey == 1) RecorderConfig.INSTANCE.startStopKey = newKey;
                else if (listeningForKey == 2) RecorderConfig.INSTANCE.clipKey = newKey;
                else if (listeningForKey == 3) RecorderConfig.INSTANCE.menuKey = (newKey == GLFW.GLFW_KEY_UNKNOWN) ? GLFW.GLFW_KEY_N : newKey;
                listeningForKey = 0;
                RecorderConfig.save();
                updateKeybindButtons();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int centerX = this.width / 2;
            int startY = this.height / 2 - 80;
            graphics.fill(centerX - 170, startY - 30, centerX + 170, startY + 180, 0xCC121212);
            graphics.hLine(centerX - 170, centerX + 170, startY - 30, 0xFF3A3A3A);
            graphics.hLine(centerX - 170, centerX + 170, startY + 180, 0xFF3A3A3A);
            graphics.drawCenteredString(this.font, "Literal Screen Recorder", centerX, startY - 18, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font, "Hotkeys (Press ESC to unbind)", centerX, startY + 82, 0xFFAAAAAA);
            super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public void onClose() {
            RecorderConfig.save();
            Minecraft.getInstance().setScreen(this.parent);
        }
    }
}
