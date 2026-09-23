package com.ecostg.paper;

import com.ecostg.paper.menu.MenuHub;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ESC pause-screen hub: opens the full button menu directly (no intermediate screen).
 * Gamemode is OP-checked when clicked; {@link MenuHub#openMain} still hides it for non-ops on /menu.
 */
public final class EcoSTGBootstrap implements PluginBootstrap {

    public static final TypedKey<Dialog> PAUSE_DIALOG_KEY = DialogKeys.create(Key.key("ecostg:pause_menu"));
    public static final Key OPEN_MAIN = Key.key(MenuHub.OPEN_MAIN_KEY);

    @Override
    public void bootstrap(BootstrapContext context) {
        String title = loadMainLetters(context.getDataDirectory());
        boolean gsitPresent = gsitJarPresent(context.getDataDirectory());

        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(event ->
                event.registry().register(PAUSE_DIALOG_KEY, builder -> builder
                        .base(DialogBase.builder(Component.text(title))
                                .externalTitle(Component.text(title))
                                .canCloseWithEscape(true)
                                .pause(false)
                                .afterAction(DialogBase.DialogAfterAction.NONE)
                                .body(List.of(DialogBody.plainMessage(
                                        Component.text("Server hub menu"), 300)))
                                .build())
                        .type(DialogType.multiAction(hubActions(gsitPresent),
                                hubBtn("Close", "Close menu", MenuHub.HUB_CLOSE), 2))
                )));

        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG),
                event -> event.registrar().addToTag(
                        DialogTagKeys.PAUSE_SCREEN_ADDITIONS,
                        Set.of(PAUSE_DIALOG_KEY)
                )
        );
    }

    private static List<ActionButton> hubActions(boolean gsitPresent) {
        List<ActionButton> actions = new ArrayList<>();
        actions.add(hubBtn("Homes", "Set & teleport to homes", MenuHub.HUB_HOMES));
        actions.add(hubBtn("Auction", "Browse the auction house", MenuHub.HUB_AUCTION));
        actions.add(hubBtn("Sell", "Sell items to the auction house", MenuHub.HUB_SELL));
        actions.add(hubBtn("Teleport", "TPA / TPAHere", MenuHub.HUB_TELEPORT));
        actions.add(hubBtn("Leaderboards", "Top players", MenuHub.HUB_LEADERBOARDS));
        actions.add(hubBtn("RTP", "Random teleport (cooldown)", MenuHub.HUB_RTP));
        actions.add(hubBtn("RTP Queue", "Teleport to a random player", MenuHub.HUB_RTP_QUEUE));
        actions.add(hubBtn("Friends", "Follow players; friends when both follow", MenuHub.HUB_FRIENDS));
        actions.add(hubBtn("Pay", "Send money", MenuHub.HUB_PAY));
        actions.add(hubBtn("Stats", "View player stats", MenuHub.HUB_STATS));
        actions.add(hubBtn("Settings", "Privacy, chat, visuals", MenuHub.HUB_SETTINGS));
        actions.add(hubBtn("Ender Chest", "Open your ender chest", MenuHub.HUB_ENDER_CHEST));
        if (gsitPresent) {
            actions.add(hubBtn("Sit/lay", "Sit, crawl, lay, or spin (GSit)", MenuHub.HUB_SIT_LAY));
        }
        actions.add(hubBtn("Gamemode", "Change your gamemode (OP)", MenuHub.HUB_GAMEMODE));
        return actions;
    }

    private static ActionButton hubBtn(String label, String tooltip, Key key) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .action(DialogAction.customClick(key, null))
                .build();
    }

    private static boolean gsitJarPresent(Path dataDirectory) {
        Path pluginsDir = dataDirectory == null ? null : dataDirectory.getParent();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (var stream = Files.list(pluginsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase())
                    .anyMatch(name -> name.endsWith(".jar") && name.contains("gsit"));
        } catch (Exception e) {
            return false;
        }
    }

    private static String loadMainLetters(Path dataDirectory) {
        Path configFile = dataDirectory.resolve("config.yml");
        if (Files.isRegularFile(configFile)) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile.toFile());
            String value = cfg.getString("menu.main-letters");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "EcoSTG";
    }
}
