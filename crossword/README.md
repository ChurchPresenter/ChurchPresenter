# Crossword

Admin editor app and encoded puzzle content for ChurchPresenter's crossword feature. A module of
the main build (`:crossword`), so every command below runs on the repo-root wrapper.

## Admin App

A cross-platform (Windows / macOS / Linux) Compose Desktop app for creating and exporting crossword puzzles.

### Run

```bash
./gradlew :crossword:run
```

Requires Java 21. Gradle will download the rest automatically.

### Test

```bash
./gradlew :crossword:test
```

### Build a distributable

```bash
./gradlew :crossword:packageMsi        # Windows
./gradlew :crossword:packageDmg        # macOS
./gradlew :crossword:packageDeb        # Linux
```

---

## Workflow

1. Open the admin app (`./gradlew :crossword:run`)
2. Enter the **title** and **clues** (number, direction, clue text, answer)
3. Watch the **live crossword grid** build on the right as you type
4. Optionally **Save Plaintext…** to keep a local copy for editing later (gitignored — stays on your machine)
5. Click **Export .xwp…** → save to `encoded/levelN.xwp` (the filename determines the level order)
6. Commit the new `.xwp` — this module lives inside the ChurchPresenter repo, so there is nothing
   further to sync; rebuild → done

The next build of ChurchPresenter automatically copies the `.xwp` files into app resources.

---

## Clue Format (plaintext, for reference)

```
# In the Beginning
ACROSS:
1. The holy scriptures | BIBLE
3. God's unmerited favour | GRACE
DOWN:
2. To start or commence | BEGIN
```

- Words must share at least one letter to intersect in the grid.
- Longer words placed first — design puzzles with the longest word as your anchor.

