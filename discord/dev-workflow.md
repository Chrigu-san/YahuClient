# 🛠️ GitHub Workflow für Yahu Client
Repo: [Chrigu-san/YahuClient](https://github.com/Chrigu-san/YahuClient)
Code, Commits, Branches und PRs sind auf **Englisch**. Hier im Discord reden wir Deutsch.

## 🔒 Regeln für `main`
- Auf `main` wird **nie direkt gepusht**. Alles läuft über einen Pull Request, auch für Admins.
- Force Push und Löschen von `main` sind gesperrt.
- Ein PR kann erst gemerged werden, wenn alle Kommentare darin gelöst sind.
- `main` muss immer bauen und laufen. Was auf `main` ist, kann jederzeit ein Release werden.

## 🌿 Branches
Jeder Branch kommt von `main` und gehört zu einem Jira Ticket.
```
<typ>/<TICKET>-<kurze-beschreibung>
```
Typen:
- `feature/` neue Funktion → `feature/YAHU-12-cosmetics-shop`
- `fix/` Bugfix → `fix/YAHU-31-server-port-not-closed`
- `ui/` nur Design und Texte → `ui/YAHU-18-darker-banner`
- `chore/` Build, Installer, Aufräumen → `chore/YAHU-7-installer-runtime`
- `docs/` Doku → `docs/YAHU-9-build-guide`
Kleinbuchstaben, Wörter mit `-` trennen, kurz halten.

---- neue Nachricht ----

## ✍️ Commits
Erste Zeile: Ticket, dann **was** sich ändert, in der Befehlsform, max. ca. 72 Zeichen. Kein Punkt am Ende.
Danach eine leere Zeile und bei grösseren Sachen eine Liste mit Details: was und **warum**.
```
YAHU-31 Close the router port when the launcher quits

- Servers were left running in the background after closing the
  main window, with their port still open on the router
- MainWindow now stops all servers before the app exits
```
✅ `YAHU-12 Add cape preview to the shop page`
❌ `fixed stuff`, `wip`, `asdf`, `Update ServerManager.cpp`
- Ein Commit = eine zusammenhängende Änderung. Lieber mehrere kleine als einen riesigen.
- Wenn KI mitgeschrieben hat, kommt am Schluss eine Zeile `Co-Authored-By: ...` dazu.
- Keine Passwörter, API Keys oder Client IDs committen. Die gehören in `CMakeUserPresets.json`, und die ist in `.gitignore`.
- **Doku gehört zur Änderung:** `README.md`, `YAHU.md` und die Discord Texte in `discord/` im selben PR nachführen.

---- neue Nachricht ----

## 🔁 Ablauf für ein Ticket
1. Ticket in Jira auf **In Arbeit** setzen
2. Branch erstellen:
```
git switch main
git pull
git switch -c feature/YAHU-12-cosmetics-shop
```
3. Arbeiten, bauen (`scripts\yahu-build.bat`), selber testen, committen
4. Pushen und PR öffnen:
```
git push -u origin feature/YAHU-12-cosmetics-shop
gh pr create --fill
```
5. Titel vom PR: `YAHU-12 Cosmetics shop with cape preview`
6. In die Beschreibung: **Was** wurde gemacht, **wie testen**, Screenshot bei UI Änderungen, Doku angepasst?
7. Jemand anderes schaut den PR an, Kommentare werden gelöst
8. **Squash and merge**, danach den Branch löschen
9. Ticket in Jira auf **Erledigt**
-# Weil das Ticket im Branch, Commit und PR steht, verknüpft Jira alles automatisch, sobald die App "GitHub for Jira" installiert ist.

## 🔄 Branch aktuell halten
Wenn `main` sich in der Zwischenzeit geändert hat:
```
git fetch origin
git rebase origin/main
git push --force-with-lease
```
`--force-with-lease` nur auf dem **eigenen** Branch, nie auf `main`.

---- neue Nachricht ----

## 🚀 Releases
Versionen nach dem Schema `MAJOR.MINOR.PATCH`, z.B. `0.1.1`:
- **PATCH** (`0.1.0` → `0.1.1`): Bugfixes und kleine Features
- **MINOR** (`0.1.x` → `0.2.0`): grössere neue Features
- **MAJOR** (`0.x` → `1.0.0`): erste "richtige" Version, grosse Umbauten

Ablauf:
1. Version in `CMakeLists.txt` erhöhen (`Launcher_VERSION_PATCH` usw.), per PR auf `main`
2. Release bauen und Installer erstellen (`YahuClient-Setup.exe` mit NSIS)
3. Auf GitHub ein Release mit Tag `v0.1.1` erstellen und den Installer anhängen
4. Kurze Release Notes: was ist neu, was wurde gefixt
5. Im Announcement Channel posten

Download Link für Spieler, zeigt immer auf die neuste Version:
`https://github.com/Chrigu-san/YahuClient/releases/latest/download/YahuClient-Setup.exe`

## 📁 Wichtige Ordner
- `launcher/ui/yahu/` eigenes Design (Banner, Leiste, Server Seite)
- `launcher/servers/` Server Hosting, UPnP und Server Adressen
- `services/dns-worker/` Adress Dienst für `name.yahuclient.net` (Cloudflare Worker, Tests mit `node --test`)
- `launcher/ui/themes/YahuTheme.cpp` und `launcher/ui/yahu/YahuStyle.h` Farben und Stil
- `program_info/win_install.nsi.in` Installer
- `tests/` Tests (`ctest` im `build` Ordner)
-# Yahu Client ist ein Fork von Prism Launcher und steht unter GPL 3.0. Der Code bleibt öffentlich und die Credits von Prism, PolyMC und MultiMC bleiben drin.
