package ru.lumine.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LumineFeedPersistenceTest {
    @TempDir Path temporary;

    @Test
    void savesAndReloadsConsentWithoutTruncatingIt() {
        Path file = temporary.resolve("nested/player-prefs.properties");
        Properties initial = new Properties();
        initial.setProperty("one.allowEffectFood", "false");
        LumineFeed.save(file, initial, "test");
        initial.setProperty("one.allowEffectFood", "true");
        LumineFeed.save(file, initial, "test");
        assertEquals("true", LumineFeed.load(file).getProperty("one.allowEffectFood"));
    }

    @Test
    void reportsUnreadableConsentInsteadOfPretendingItIsDisabled() {
        assertThrows(UncheckedIOException.class, () -> LumineFeed.load(temporary));
    }
}
