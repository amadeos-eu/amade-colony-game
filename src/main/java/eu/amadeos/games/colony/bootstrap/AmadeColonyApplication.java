package eu.amadeos.games.colony.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.games.colony.ui.game.ColonyGame;
import eu.amadeos.games.colony.ui.main.ColonyPresenter;
import eu.amadeos.games.colony.ui.main.ColonyWindow;

public final class AmadeColonyApplication {
    public static final AppInfo APP = new AppInfo("amade-colony-game", "Colony", "colony");

    private AmadeColonyApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeColonyApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            ColonyWindow window = injector.getInstance(ColonyWindow.class);
            ColonyPresenter presenter = injector.getInstance(ColonyPresenter.class);
            ColonyGame game = injector.getInstance(ColonyGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new ColonyModule());
    }
    // --8<-- [end:main]
}
