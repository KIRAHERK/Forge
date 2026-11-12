# Contributing

Issues and pull requests are welcome.

Forge is source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE), and its author also offers commercial licenses. By opening a pull request you agree that your contribution is licensed under the project's license and may also be included in commercially licensed versions of Forge.

## Build

Android SDK (platform 35) and JDK 17.

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug builds include a seeder that fills the app with two weeks of sample data:

```bash
adb shell am broadcast -a com.forge.app.SEED -n com.forge.app/.DebugSeedReceiver
```

and a way to fire any reminder immediately:

```bash
adb shell am broadcast -a com.forge.app.FIRE --es rem WEIGH_IN -n com.forge.app/.DebugSeedReceiver
```

## Ground rules

- Keep the UI copy short and plain. No emojis.
- Everything stays on the device. The only network calls are the GitHub release check, the Syrian dish list refresh, and Open Food Facts searches the user starts.
- Database or settings changes must not lose anyone's data. See *Rules that keep data safe* in [RELEASING.md](RELEASING.md).
- Rules logic (streaks, scoring, penalties, progression) lives in `data/Stats.kt` and `domain/Rules.kt` and has unit tests in `app/src/test`. Add a test when you change it.
