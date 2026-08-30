# DreamBound

A 2D top-down action RPG built in Java (Swing / Java2D). You wake up in Dreambound with no memory of how you got there, fight your way through a string of increasingly strange encounters, and slowly start finding out that the "isekai adventure" everyone around you seems to believe in isn't quite what it looks like.

## Screenshots

<img width="985" height="726" alt="image" src="https://github.com/user-attachments/assets/79defa2a-4adc-4b04-b296-ef2fbc82dd58" />

<img width="980" height="731" alt="image" src="https://github.com/user-attachments/assets/5cafa25c-5e79-4594-a3ec-89c17ba9812b" />

<img width="877" height="202" alt="image" src="https://github.com/user-attachments/assets/8ac89fc6-5ac8-4fb7-aace-0956be01f986" />

<img width="976" height="687" alt="image" src="https://github.com/user-attachments/assets/fdeac2ae-ab13-4cb9-b679-f9fa05a79214" />


## Features

- **5 playable classes** — Warrior, Tank, Rogue, Mage, Archer — each with their own stats, melee or ranged playstyle, and unlock condition. Swap between unlocked party members anytime with `1`–`5`.
- **A branching campaign** — a linear opening gives way to a real fork partway through: one path leads to an alternate boss, the other continues toward the main story's final confrontation.
- **NPCs and dialogue** — branching conversations, story flags, and a hidden side path unlocked by what you say, not just where you go.
- **Two boss fights with distinct AI** — a giant slime boss, and a ranged boss that actively keeps its distance and kites away when you close in.
- **Checkpointing** — reaching the village auto-saves your progress.

## Controls

| Key | Action |
|---|---|
| Arrow keys | Move |
| Space | Attack |
| E | Talk to an NPC |
| 1–5 | Switch active party member |
| S | Save |
| Esc | Menu / close dialogue |

## Running it

Requires a JDK (11+ recommended).

```bash
cd src
javac -d ../build com/*.java
cp -r ../Resources ../build/
cd ../build
java com.Main
```

Or just open the project in your IDE of choice (IntelliJ, Eclipse, VS Code with the Java extension) and run `com.Main`.

## Project structure

```
DreamBound/
├── src/com/          # All source (.java) files
├── Resources/        # Sprites, tiles, and decorations
│   ├── character/    # Per-class player animations (idle/run/attack/damaged/die)
│   ├── enemy/         # Per-enemy-type animations
│   ├── decorations/   # Bushes, rocks, trees, ruins
│   └── tiles/         # Ground tiles (grass, path)
└── README.md
```

## Known placeholders

- **Village houses** are drawn procedurally (simple shapes) since no house sprites exist yet — swap in real art by dropping images into `Resources/decorations/` and updating the village map's decoration calls, no other code changes needed.
- A gate visual stands in for what will eventually be a proper portal/gate sprite.

## Credits

Character, enemy, and environment art sourced from free asset packs on [CraftPix](https://craftpix.net) and the "Pixel Fantasy World" demo pack. See each pack's license for redistribution terms if you fork this.

## License

Add your license of choice here (MIT is a common default for personal projects like this).
