# Forge

A training and nutrition app for Android that holds you to your plan: reminders, deadlines, and real consequences for skipping.

It ships with a 4-day home muscle program and a lean-bulk nutrition plan, logs every set, tracks calories, protein and morning weight, and tracks the daily habits you choose. Everything stays on your phone.

<p>
  <img src="docs/screenshots/today.png" width="19%" alt="Today">
  <img src="docs/screenshots/workout.png" width="19%" alt="Workout">
  <img src="docs/screenshots/fuel.png" width="19%" alt="Fuel">
  <img src="docs/screenshots/habits.png" width="19%" alt="Habits">
  <img src="docs/screenshots/weight.png" width="19%" alt="Weight">
</p>

## Install

1. Download `Forge-x.y.z.apk` from the [latest release](../../releases/latest) on your phone.
2. Open it and allow installs from your browser or file manager when Android asks.
3. After that the app updates itself: **Settings → Updates**, or the daily "new version" notification. Updates install over the existing app, so your data stays.

Prefer a dedicated updater? [Obtainium](https://github.com/ImranR98/Obtainium) works with this repo too.

## What it does

| | |
|---|---|
| **Today** | Today's session with a live deadline countdown, calorie and protein rings, morning weigh-in, daily habit check-in, streak, week score, and a blunt line for the day. |
| **Train** | The program, form guides for every exercise (setup, cues, common mistakes), session history. |
| **Workout** | Tick sets, adjust reps and kg, see last time's numbers, automatic rest timer, double-progression hints ("go heavier: 12.5 kg"), PR detection. A session counts only at 75% of its sets. |
| **Fuel** | Calories and macros against your targets, a food list with USDA values, 15 budget high-protein recipes, full-day meal plans, "what to eat next" suggestions. |
| **Habits** | Add any habit you want to build or quit. Weekly grid, week score, streaks and best runs, an urge timer for quit habits, penalty record. Starts empty. |
| **Progress** | 7-day average weight, kg per week vs target, ETA, 2-week check-in, progress photos (stored privately), body measurements, 12-week tracker, levels, lifts. |
| **Weight** | Morning weigh-ins with a type-in reminder notification, streak, weekly averages, editable history. |

### Consequences

- Miss the daily deadline on a training day: the streak resets, you lose XP, and a random penalty lands (e.g. 100 push-ups) that's due in 24 hours.
- Ignore the penalty and it doubles. Ignore it again and it's recorded as failed.
- Make the session up within 48 hours to restore the streak. The penalty still stands.
- Optional penalty jar: a money amount you owe yourself for every miss or slip.

## Your data

- Stored only on the device. No account, no analytics. The only network call checks this repo for a new release.
- Updates keep your data: same app, same signing key. The updater refuses any APK signed with a different key.
- A backup is written before every update, and daily to `Downloads/Forge/` on Android 10+. **Settings → Data** exports, imports and restores.

## The built-in plan

The program (Day 1 Chest Priority, Day 2 Legs + Posterior Chain, rest, Day 4 Upper + Back + Arms, Day 5 Legs + Delts/Arms, rest, rest) is used exactly as written. Nutrition targets default to 2,700 kcal and 130 g protein, and you set your own during setup or in Settings.

**Not medical advice.** If you have a medical condition, check calorie and protein limits with your clinician.

## Build

JDK 17 and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest assembleDebug
```

Releases are built and signed by GitHub Actions when a `v*` tag is pushed. See [RELEASING.md](RELEASING.md). Contributions: [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

Exercise cues compiled from ACE, NASM, Motra, Garage Gym Reviews, Muscle & Strength, Eric Cressey and StrengthLog. Food values from USDA FoodData Central. Typeface: Barlow (SIL Open Font License).

## License

[MIT](LICENSE)
