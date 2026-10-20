package eu.amadeos.games.colony.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.games.colony.repository.ColonySettings;
import eu.amadeos.games.colony.repository.Progress;
import eu.amadeos.games.colony.repository.SaveSlot;
import eu.amadeos.games.colony.ui.game.Arena;
import eu.amadeos.games.colony.ui.game.ColonyGame;
import eu.amadeos.games.colony.ui.main.ColonyPresenter;
import eu.amadeos.games.colony.ui.main.ColonyView;
import eu.amadeos.games.colony.ui.main.ColonyWindow;

import java.util.Locale;

/**
 * amade-colony-game bindings. Theme, language and the XDG directories (where {@link Progress} and {@link SaveSlot} save) come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ColonyModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.colony.messages";

    @Override
    protected void configure() {
        bind(ColonySettings.class).in(Scopes.SINGLETON);
        bind(Progress.class).in(Scopes.SINGLETON);
        bind(SaveSlot.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mouse.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(ColonyGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(ColonyGame.class);
        bind(ColonyWindow.class).in(Scopes.SINGLETON);
        bind(ColonyView.class).to(ColonyWindow.class);
        bind(ColonyPresenter.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ColonyModule.class, MESSAGES, locale);
    }
}
