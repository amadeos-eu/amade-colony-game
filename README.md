# amade-colony-game

**amade-colony-game** is the economy game of the [amadeOS](https://github.com/amadeos-eu/amadeos) desktop: a green valley, a storehouse and a few dozen settlers. You lay out houses and link them with roads, flag to flag; every piece of road gets its own carrier, who takes the goods from one flag to the next. Woodcutters, foresters and quarries bring logs and stone, the sawmill makes planks, farms, the windmill and the bakery turn grain into bread, the sheep farm turns grain and water into wool and the weaver makes cloth. Food and cloth brought to a residence make a new settler; tools turn settlers into woodcutters, farmers and fishers. Lookout towers move the border out. Winter stops the fields, houses catch fire (a well nearby puts it out), and wolves come for the sheep (the dog and the watchman's horn drive them off). Three missions on maps larger than the screen, made in the [Tiled](https://www.mapeditor.org/) editor; the buildings, missions and the computer's plan are text files. A settlement can be saved and loaded. Built on [amade-game-engine](https://github.com/amadeos-eu/amade-game-engine); runs on other operating systems as well.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Playing

- A click, **Space** or **Enter** leads from the title to the choice of a mission. Click one, or choose with **left** and **right** and play with **Space** or **Enter**. A mission opens when the one before it is won; the fastest win of each is kept.
- **The goal** of each mission is a number of settlers, sometimes with goods in the storehouses and a number of years. The bar at the top shows the goods in the storehouses, the settlers and the goal, the season and the year, and the clock.
- **The panel** on the right has three tabs of buildings, *Basic*, *Food* and *Crafts*, each with what it costs in planks and stone. Click one, then the place on the map: inside the border, on free land, with room for its flag below the door (a green mark yes, red no). A builder comes from the storehouse, the carriers bring the goods, and the house rises.
- **Roads**: after a building is laid out, its road follows the mouse from the new flag. Each click lays the piece of road the mouse shows and goes on from its end, until the road meets a flag or a road that is already there. **R** or *Road* lays roads from any flag; **F** puts a flag on the tile under the mouse, which also splits a long road in two, so two carriers share it. The right button or **Esc** stops.
- A click on a house, a flag or a road shows it in the panel: what goes in and comes out, whether a worker or tools are missing, the sheep and the dog, the wares waiting at a flag, the carrier of a road. **Delete** or *Tear down* pulls down the chosen house, flag or road.
- **+** and **-** make time run two or four times as fast, and back.
- **F5** saves the settlement, **F9** loads it, here and on the choice of a mission.
- **Arrow keys**, dragging with the middle mouse button, the mouse at the edge of the screen or a click on the minimap move the view.
- **P** or *Menu* pauses, and so does leaving the window; there you can save, load or leave the mission. **Esc** steps back, or pauses when nothing is chosen.
- **F11** switches to full screen and back. *Game → Graphics* chooses how the settlement reaches the screen: **Direct** (at the screen's full size) or **Pixels** (at 640 × 400, enlarged by whole factors). **F12** shows how long each frame takes.
- Sound effects and music can be switched off under *Game → Sound*.

Texts in English, Deutsch, Français, Español and Italiano.

## Files

| What | Where |
|---|---|
| Settings (graphics, sound) | `~/.config/amadeos/colony/colony.properties` |
| Progress (open missions, fastest wins) | `~/.local/share/amadeos/games/colony/progress.save` |
| The saved settlement (F5) | `~/.local/share/amadeos/games/colony/settlement.save` |

Both save files are written to a new file that then replaces the old one, with the old one kept as `.bak` and a checksum in the last line, so a crash while saving or a damaged file loses nothing. The saved settlement holds the whole game: every house, road, flag and ware, every person with their task, the trees, fields, sheep and wolves, the seasons and the random numbers, so a loaded settlement goes on exactly as it would have.

## Making missions

The maps are `src/main/resources/eu/amadeos/games/colony/maps/map1.tmx` to `map3.tmx`, made for Tiled 1.10 or later, with the tileset `tiles.tsx` and its picture `tiles.png`. Open them in Tiled, change them, save; the game reads them as they are.

- A map is orthogonal, not infinite, with tiles of 16 × 16 pixels. Its edge is always mountains.
- The tile layer **ground** says what each tile is; the tileset gives each tile a class: `grass` (also a tile without a class), `forest` (grass with a grown tree), `rock` (stone for the quarry), `water` (fish) and `mountain` (nobody crosses it). The game draws the ground itself, so `tiles.png` only shows Tiled which tile is which.
- The object layer **things** holds one `storehouse`, a rectangle on grass, with grass below its door for the flag, and for a mission with wolves the `den`s they come from.
- A map with tiles of another size, an unknown class, a storehouse off the grass or a den in the water is rejected with its file and the object.

What the settlers build and how the computer plays is in `src/main/resources/eu/amadeos/games/colony/data/`, explained at the top of each file:

- `buildings.txt`: each building's tab, size, cost, building time, work, what goes in and comes out and how long it takes, its radius, and whether its worker needs tools.
- `missions.txt`: each mission's map, the goods and settlers at the start, the goal, the years, the lengths of the seasons, and whether there are fires and wolves.
- `plan.txt`: the order in which the computer builds its settlement on the title screen and in the tests.

Change a number, start the game again, play. A mistake is reported with its file and line. `GameTest` lets the computer build mission 1 until it is won, so an economy that stalls fails the build.

## Building

```bash
./gradlew test          # the window test needs a display (xvfb-run on servers)
./gradlew run
./gradlew deb           # build/distributions/amade-colony-game_<version>_all.deb
./gradlew installToAmadeOs   # copies the package to ../amadeos/config/packages.chroot/
```

All pictures are drawn by the game from vector shapes (`Art`, `Houses`, `Folk`) when the window opens, at the size of the screen: fifteen houses with their sites, the people with job-specific headwear and tools, sheep, the dog and wolves, trees and fields in summer and winter. The ground is drawn in squares of 8 × 8 tiles on background threads, with the roads and the border mixed in again when they change, and snow blended over it in winter. `./gradlew art` draws `maps/tiles.png` and the icon from the same shapes, `./gradlew screenshots -Pshots=<folder>` the handbook's screenshots, and `./gradlew benchmark` measures the frame times of every mission.

With `../amade-game-engine` (and `../amade-commons`) checked out next to this repository, Gradle builds those checkouts instead of the JitPack versions.

## Structure

- `domain`: the rules without user interface or time of their own. `Game` (a settlement: sites and builders, workers and their work, wares and carriers on the roads, the storehouses' orders, trees, fields, fish, sheep, seasons, fires, wolves, the border, winning), `Overseer` (the computer: lays out the plan, its roads and towers), `Archive` (the whole game to and from a save file), `Level` (reads a Tiled map), `Rules` (reads the three data files), `BuildingType`, `Mission`, `Plan`, `Building`, `Person`, `Ware`, `Wolf`, `Good`, `Work`, `Ground`, `Event`, `Field` (sizes and numbers).
- `repository`: `ColonySettings` (graphics, sound), `Progress` (open missions and fastest wins), `SaveSlot` (the saved settlement).
- `ui.game`: the running game on the loop thread. `ColonyGame` (loop, mouse and keys, scenes: title, choice, mission, pause, result; laying out, roads, choosing), `ColonyPainter` (the settlement as the camera sees it, in layers), `Terrain` (the ground in chunks, drawn in the background), `Art`, `Houses` and `Folk` (all drawings), `Pictures` (the sprites made of them), `Hud` (the goods bar, the panel, the cards and texts), `Effects` (events become smoke, sparks, snow, sounds and music), `SoundBank`, `Music` (the tunes, in MML), `Arena` (what the presenter may ask of the game).
- `ui.main`: Model-View-Presenter for everything around the settlement: `ColonyPresenter`, `ColonyView`, `ColonyWindow`.

From the engine: `RoadNetwork` (flags and roads), `Stock` and `Recipe` (goods in a building and what it makes of them), `Seasons`, `Territory` (the border), `Wander` (grazing sheep), `AStar`, `Minimap`, `Snapshot` (saving a whole game), `Catalog`, `TiledMap`, `Camera`, `Rotations` and `Sprite`, `Particles`, `SaveFile`, `GameLoop`, `GameCanvas`, `Sound`, `Tune` and `Mixer`, `Keyboard`, `Mouse` and `Controls`, `SceneStack`, `FullScreen`.

Comments of the form `// --8<-- [start:name]` mark the excerpts the [Game Developer's Handbook](https://docs.amadeos.eu/amade-game-engine/) quotes; chapter 32 is about this game.

## Licence

[EUPL-1.2](LICENSE)
