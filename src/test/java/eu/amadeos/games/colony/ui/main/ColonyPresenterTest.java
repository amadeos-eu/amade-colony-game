package eu.amadeos.games.colony.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.colony.repository.ColonySettings;
import eu.amadeos.games.colony.ui.game.Arena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColonyPresenterTest {
    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private ColonyPresenter presenter;
    private ColonySettings settings;

    private final ColonyView view = new ColonyView() {
        @Override
        public void showOptions(boolean sound, RenderMode mode) {
            log.add("options " + sound + " " + mode);
        }

        @Override
        public void useRenderMode(RenderMode mode) {
            log.add("mode " + mode);
        }

        @Override
        public void showAbout() {
            log.add("about");
        }
    };

    private final Arena arena = new Arena() {
        @Override
        public void showTitle() {
            log.add("title");
        }

        @Override
        public void chooseLevel() {
            log.add("choose");
        }

        @Override
        public void pause() {
            log.add("pause");
        }

        @Override
        public void setSound(boolean on) {
            log.add("sound " + on);
        }
    };

    @BeforeEach
    void create() {
        settings = new ColonySettings(dir.resolve("colony.properties"));
        presenter = new ColonyPresenter(view, arena, settings);
        presenter.start();
    }

    @Test
    void theSettingsAreAppliedAtStartAndKept() {
        assertEquals(List.of("options true DIRECT", "mode DIRECT", "sound true", "title"), log);
        log.clear();
        presenter.setRenderMode(RenderMode.PIXEL);
        presenter.setSound(false);
        assertEquals(List.of("mode PIXEL", "sound false"), log);
        assertEquals(RenderMode.PIXEL, settings.renderMode());
    }

    @Test
    void losingTheFocusPausesTheSettlement() {
        log.clear();
        presenter.focusLost();
        presenter.newGame();
        presenter.showAbout();
        assertEquals(List.of("pause", "choose", "about"), log);
    }
}
