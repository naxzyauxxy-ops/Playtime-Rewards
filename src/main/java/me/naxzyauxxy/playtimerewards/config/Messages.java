package me.naxzyauxxy.playtimerewards.config;

import me.naxzyauxxy.playtimerewards.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cached chat messages. Each key may be a single string or a list of lines.
 * The {@code <prefix>} tag is always available.
 */
public final class Messages {

    private final Map<String, List<String>> messages;
    private final TagResolver prefixResolver;

    private Messages(Map<String, List<String>> messages, String prefix) {
        this.messages = messages;
        this.prefixResolver = Placeholder.parsed("prefix", prefix);
    }

    public static Messages load(ConfigurationSection section) {
        Map<String, List<String>> map = new HashMap<>();
        String prefix = "";
        if (section != null) {
            prefix = section.getString("prefix", "");
            for (String key : section.getKeys(false)) {
                if (section.isList(key)) {
                    map.put(key, List.copyOf(section.getStringList(key)));
                } else {
                    String value = section.getString(key, "");
                    map.put(key, value == null || value.isEmpty() ? List.of() : List.of(value));
                }
            }
        }
        return new Messages(map, prefix);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        List<String> lines = messages.get(key);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        TagResolver all = TagResolver.builder().resolver(prefixResolver).resolvers(resolvers).build();
        for (String line : lines) {
            to.sendMessage(Text.parse(line, all));
        }
    }

    /** Resolver for the {@code <prefix>} tag, reused by [message]/[broadcast] reward actions. */
    public TagResolver prefix() {
        return prefixResolver;
    }
}
