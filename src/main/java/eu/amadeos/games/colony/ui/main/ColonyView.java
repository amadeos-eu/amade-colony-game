package eu.amadeos.games.colony.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;

/** What {@link ColonyPresenter} needs from the window; tests replace it. The settlement itself is drawn by the game. */
public interface ColonyView {
    /** Shows the settings in the menu. */
    void showOptions(boolean sound, RenderMode mode);

    /** Switches the canvas to a render mode. */
    void useRenderMode(RenderMode mode);

    /** The About dialog. */
    void showAbout();
}
