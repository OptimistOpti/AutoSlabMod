package ru.optimist.autoslab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Простая настройка мода, хранится в config/autoslab.json.
 * Клавиша настраивается через ванильное меню "Управление" (Controls) -
 * категория "AutoSlab", т.к. используется обычный KeyBinding.
 */
public class AutoSlabConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("autoslab.json");

    /** Включён ли мод в принципе (можно выключить командой /autoslab toggle). */
    public boolean enabled = true;

    /** Интервал между расстановками полублоков, в тиках (20 тиков = 1 секунда). */
    public int intervalTicks = 4;

    /** Радиус (в блоках) вокруг игрока, в котором мод ищет полные блоки под полублок. */
    public int radius = 4;

    public static AutoSlabConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                AutoSlabConfig cfg = GSON.fromJson(reader, AutoSlabConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                AutoSlabClient.LOGGER.warn("Не удалось прочитать autoslab.json, использую значения по умолчанию", e);
            }
        }
        AutoSlabConfig fresh = new AutoSlabConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            AutoSlabClient.LOGGER.warn("Не удалось сохранить autoslab.json", e);
        }
    }

    private void sanitize() {
        if (intervalTicks < 1) intervalTicks = 1;
        if (intervalTicks > 200) intervalTicks = 200;
        if (radius < 1) radius = 1;
        if (radius > 8) radius = 8;
    }
}
