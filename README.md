A REST client for making auto predictions on Twitch for Hearthstone game.
It is based on parsing HearthstoneDeckTracker logs to minimize parsing Hearthstone log files, already done by DeckTracker.
Server part is in <a href="https://github.com/MaxonRash/SpringBootTwitchBot">SpringBootTwitchBot</a> project (`PredictionsRestController`)

Ships as a small Swing GUI (Start / Stop / Restart / Test Connection buttons, live activity log, minimizes to the system tray) so it can just be launched directly instead of run from a terminal.

## Building

```
mvnw clean package
```
produces `target/prediction_client_3-0.0.1-SNAPSHOT.jar`, which can be run with `run.bat` or `java -jar ...` during development.

To build a standalone Windows executable (bundles its own Java runtime, no separate Java install needed to run it):
```
package-exe.bat
```
This produces `target/dist/PredictionClient3/PredictionClient3.exe` — copy that whole `PredictionClient3` folder to distribute it; the `.exe` inside is the one to run.

Credentials are set in `src/main/resources/credentials.properties` (copy `credentials.properties.origin`, fill in `user`/`password`) before building — they're baked into the jar at build time.