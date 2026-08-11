# Ayah Cutie for the desktop

The same verse card, as a little floating window for a Linux desktop. It reads the very
same `quran.txt` / `surahs.txt` the Android app ships, wears the same eight styles, and
shuffles itself on the same intervals.

<p align="center"><i>drag it with the left button · resize from the corner grip · right-click for everything else</i></p>

## Running it

```bash
./install.sh          # fonts, verse files and an app-grid launcher, all under $HOME
python3 ayah_widget.py
```

The only dependency is PyGObject with GTK 3, which Ubuntu, Fedora and friends already
ship (`python3-gi` / `python3-gobject`). The card is drawn with GTK CSS rather than
cairo, so there is nothing else to install and nothing needs root.

`install.sh` copies the bundled **Amiri Quran** and **Quicksand** fonts into
`~/.local/share/fonts` — without them the widget falls back to whatever Arabic font you
already have, which is readable but not nearly as pretty.

## Using it

| | |
|---|---|
| **Move** | Left-click anywhere on the card and drag |
| **Resize** | Drag the ◢ grip in the bottom-right corner, or pick **Size** from the menu |
| **Everything else** | Right-click the card |

The right-click menu holds:

- **New ayah** and **Copy ayah**
- **Theme** — Cotton Candy, Frosted Glass, Smoked Glass, Midnight, Sunset, Mint, Paper, Neon
- **Size** — Small, Medium, Large, Wide (or just drag the grip to any size you like)
- **Opacity** — 100%, 90%, 75%, 60%
- **New ayah every** — 15 min to 6 hours
- **Arabic** / **English meaning** toggles
- **Start at login**

Everything is remembered in `~/.config/ayah-cutie/widget.json`, including the size you
dragged it to and the verse it is currently showing.

## Wayland vs X11

On **X11** the window asks to sit below other windows and on every workspace, so it
behaves like a proper desktop widget.

On **Wayland** (the GNOME default) the compositor does not let applications place
themselves, stay below other windows, or pin themselves to the desktop layer — so it is
a small ordinary window that you drag where you want it. Nothing is broken; that is as
far as the protocol lets a plain GTK app go. Log into "Ubuntu on Xorg" from the login
screen if you want the true always-on-the-desktop behaviour.

## About the glass styles

Frosted Glass and Smoked Glass are translucent with a bright rim light, the same as on
the phone. Neither desktop nor Android gives a widget a real blur of what is behind it,
so this is the honest version of the effect rather than a fake one.

## Uninstalling

```bash
rm -rf ~/.local/share/fonts/ayah-cutie ~/.local/share/ayah-cutie \
       ~/.local/share/applications/ayah-cutie.desktop \
       ~/.config/ayah-cutie ~/.config/autostart/ayah-cutie.desktop
fc-cache -f
```
