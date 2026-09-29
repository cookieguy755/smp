# GemsSMP 2.0 – Gems mit Abilities & Leveln (Paper 26.2)

## Was sich gegenüber der ersten Version geändert hat
- **Ziel-API auf Paper 26.2 umgestellt** (`paper-api:[26.2.build,)`, Java 25). Die erste Version war
  gegen die alte 1.21.4-API gebaut - auf einem 26.2-Server mit Java 25 kann sowas beim Start
  fehlschlagen, was vermutlich auch der Grund war, warum gar nichts ging, inklusive der Commands.
- **`/gem` ist jetzt ein echter, nativer Command** (Paper-Brigadier-API, `LifecycleEvents.COMMANDS`),
  kein Text-Handler mehr über den alten `commands:`-Block. Das heißt: `/gem` erscheint im
  Client mit echter Tab-Completion, Spieler- und Gem-Namen werden dir beim Tippen direkt
  vorgeschlagen, genau wie bei einem eingebauten Minecraft-Befehl.
- **Speed-Gem ersetzt durch Natur-Gem**: `Entangle` (Wurzeln + Gift, Gegner bleibt fast bewegungsunfähig,
  kann aber noch zuschlagen - bewusst anders als Frosts Vollstop) und `Regrowth` (heilt dich und
  Verbündete in der Nähe über Zeit).
- **Life-Gem ersetzt durch Amethyst-Gem**, das seltenste und stärkste Gem:
  - Gibt es **nicht** per Zufall beim ersten Join, sondern nur per Crafting-Rezept.
  - Serverweit limitiert (Standard: 3x craftbar, in der `config.yml` einstellbar, `-1` = unbegrenzt).
  - Levelt langsamer als die anderen Gems (XP-Multiplikator 2,5x), damit hohe Level nicht zu schnell da sind.
  - `Crystal Burst`: starker Flächenschaden + Knockback + kurzer Stun (kein Dauer-Lockdown).
  - `Crystal Aegis`: Absorption + Resistenz und wirft 25% des erlittenen Nahkampfschadens zurück.
  - Balance-Ventil: Wird der Besitzer von einem anderen Spieler getötet, fällt das Amethyst-Gem
    (anders als die anderen Gems) als normaler Death-Drop liegen - abschaltbar in der Config.

## Gems & Abilities (Rechtsklick = Ability 1, Shift+Rechtsklick = Ability 2)

| Gem | Ability 1 | Ability 2 | Passiv (im Inventar) |
|---|---|---|---|
| Feuer | **Burn** – zündet Gegner im Umkreis an | **Flame Beam** – Feuerstrahl auf ein Ziel | Feuerresistenz ab Lv. 2 |
| Stärke | **Smash** – Flächenschaden + Knockback | **Rage** – Stärke + Resistenz | Stärke I ab Lv. 3 |
| Frost | **Stun** – Gegner bewegungs-/angriffsunfähig | **Frost Nova** – Schaden + Verlangsamung | Wasseratmung ab Lv. 2, Delfingunst ab Lv. 4 |
| Natur | **Entangle** – Wurzeln + Gift | **Regrowth** – heilt dich & Verbündete über Zeit | Speed I ab Lv. 2, Sättigung ab Lv. 4 |
| Amethyst ✦ | **Crystal Burst** – starker Flächenschaden + kurzer Stun | **Crystal Aegis** – Schild, reflektiert Schaden | Hero of the Village ab Lv. 3, Glück ab Lv. 5 |

## Leveln
- Maximallevel 5 pro Gem (einstellbar über `xp-per-level` in der `config.yml`).
- XP gibt es für jede benutzte Ability (eigenes Gem) und für Kills (alle Gems im Inventar zugleich).
- Amethyst braucht 2,5x so viel XP pro Level wie die anderen Gems (einstellbar unter `xp-multiplier`).
- Höheres Level = mehr Schaden/Radius/Dauer, kürzerer Cooldown, mehr Passives.
- Neue Spieler bekommen beim ersten Join automatisch ein zufälliges **gewöhnliches** Gem
  (Feuer, Stärke, Frost oder Natur - niemals Amethyst).
- Gems bleiben beim Tod erhalten - außer Amethyst im PvP-Tod (siehe oben).

## Das Amethyst-Gem craften
Rezept (3x3, Werkbank): 4x Diamant in den Ecken, 4x Amethystsplitter in den Kanten,
1x Nether-Stern in der Mitte.
```
D A D
A S A
D A D
```
Einstellbar über `amethyst.craftable` und `amethyst.max-crafts` in der `config.yml`.

## Befehle
`/gem` (Alias: `/gems`) ist ein nativer Paper-Command mit Tab-Completion:
- `/gem info` – deine Level anzeigen (für alle)
- `/gem give <Spieler> <Gem>` (gems.admin)
- `/gem setlevel <Spieler> <Gem> <Level>` (gems.admin)
- `/gem addxp <Spieler> <Gem> <XP>` (gems.admin)
- `/gem reload` (gems.admin)

## Bauen (Kompilieren)
1. **JDK 25** und Maven installieren (Paper 26.x braucht Java 25). Alternativ: Projekt in IntelliJ IDEA öffnen.
2. Im Projektordner: `mvn clean package`
3. `target/GemsSMP-2.0.0.jar` per FTP in den `plugins`-Ordner deines G-Portal-Servers hochladen.
4. Server neu starten.

### Wichtiger Hinweis zum Testen
Ich konnte diesen Build in meiner Sandbox **nicht** gegen die echte Paper-26.2-API kompilieren, weil
mein Werkzeug-Container keinen Netzwerkzugriff auf Maven-Repositories (weder `repo.papermc.io` noch
Maven Central) hat - nur auf eine kleine Allowlist von Domains. Ich habe deshalb jeden neuen/kritischen
API-Aufruf (vor allem die komplett neue Brigadier-Command-Registrierung) einzeln gegen die aktuelle,
offizielle PaperMC-Dokumentation (docs.papermc.io) und den GitHub-Quellcode geprüft, statt mich auf
mein Gedächtnis zu verlassen - das ist der Grund, warum die Commands beim letzten Mal vermutlich
nicht gingen. Eine hundertprozentige Garantie ist das trotzdem nicht.

Falls du einen GitHub-Account hast: Ich habe eine GitHub-Actions-Datei (`.github/workflows/build.yml`)
beigelegt. Lädst du den Ordner in ein (auch privates) Repository hoch, baut GitHub das Plugin
automatisch mit echtem Internetzugriff auf die Paper-API und du bekommst unter dem Tab "Actions" grün
oder rot angezeigt, ob es wirklich kompiliert - inklusive der fertigen `.jar` zum Herunterladen.

Falls beim Bauen trotzdem ein Fehler kommt: schick mir einfach die komplette Fehlermeldung, dann
behebe ich sie gezielt.
