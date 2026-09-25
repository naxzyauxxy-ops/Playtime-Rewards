package me.naxzyauxxy.playtimerewards.config;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/** A pre-built Adventure sound; {@code null} sound means "disabled". */
public record SoundSetting(@Nullable Sound sound) {

    public static final SoundSetting NONE = new SoundSetting(null);

    @SuppressWarnings("PatternValidation")
    public static SoundSetting load(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) {
            return NONE;
        }
        String key = section.getString("sound", "");
        if (key == null || key.isBlank()) {
            return NONE;
        }
        try {
            Sound sound = Sound.sound(Key.key(key.trim().toLowerCase()), Sound.Source.MASTER,
                    (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
            return new SoundSetting(sound);
        } catch (InvalidKeyException ex) {
            logger.warning("Invalid sound key '" + key + "' at " + section.getCurrentPath());
            return NONE;
        }
    }

    public void play(Player player) {
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }
}
