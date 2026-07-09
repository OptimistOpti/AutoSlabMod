package ru.optimist.autoslab;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/**
 * Экран настроек мода, встроенный в Mod Menu (список модов -> шестерёнка у AutoSlab).
 * Требует установленный Cloth Config. Если Mod Menu не установлен, этот класс
 * просто никогда не будет запрошен фреймворком Fabric (entrypoint "modmenu").
 */
public class AutoSlabModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            AutoSlabConfig config = AutoSlabClient.getConfig();
            KeyBinding activateKey = AutoSlabClient.getActivateKey();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("autoslab.config.title"))
                    // Сохраняем в наш собственный autoslab.json при выходе с экрана.
                    .setSavingRunnable(config::save);

            ConfigEntryBuilder eb = builder.entryBuilder();
            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("autoslab.config.category.general"));

            general.addEntry(eb.startBooleanToggle(Text.translatable("autoslab.config.enabled"), config.enabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("autoslab.config.enabled.tooltip"))
                    .setSaveConsumer(value -> config.enabled = value)
                    .build());

            general.addEntry(eb.startIntSlider(Text.translatable("autoslab.config.interval"), config.intervalTicks, 1, 200)
                    .setDefaultValue(4)
                    .setTextGetter(value -> Text.translatable("autoslab.config.interval.value", value))
                    .setTooltip(Text.translatable("autoslab.config.interval.tooltip"))
                    .setSaveConsumer(value -> config.intervalTicks = value)
                    .build());

            general.addEntry(eb.startIntSlider(Text.translatable("autoslab.config.radius"), config.radius, 1, 8)
                    .setDefaultValue(4)
                    .setTextGetter(value -> Text.translatable("autoslab.config.radius.value", value))
                    .setTooltip(Text.translatable("autoslab.config.radius.tooltip"))
                    .setSaveConsumer(value -> config.radius = value)
                    .build());

            general.addEntry(eb.startKeyCodeField(Text.translatable("key.autoslab.activate"), KeyBindingHelper.getBoundKeyOf(activateKey))
                    .setDefaultValue(activateKey.getDefaultKey())
                    .setTooltip(Text.translatable("autoslab.config.key.tooltip"))
                    .setKeySaveConsumer(key -> {
                        activateKey.setBoundKey(key);
                        KeyBinding.updateKeysByCode();
                        // Пишем в options.txt, чтобы привязка пережила перезапуск игры,
                        // так же как если бы игрок назначил её в "Управление".
                        MinecraftClient.getInstance().options.write();
                    })
                    .build());

            return builder.build();
        };
    }
}