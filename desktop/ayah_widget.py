#!/usr/bin/env python3
"""
Ayah Cutie — the same little verse card, for a Linux desktop.

A frameless GTK window that reads the very same quran.txt / surahs.txt the Android app
ships, wears the same eight card styles, and shuffles itself on a timer. Drag it with
the left mouse button, resize it from the grip in the bottom-right corner, and
right-click it for everything else.

The card is drawn entirely with GTK CSS rather than cairo, so the only thing this
needs is PyGObject — no pycairo bridge, no root install.
"""

import json
import os
import random
import sys
import time

import gi

gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
from gi.repository import Gdk, GLib, Gtk, Pango  # noqa: E402

APP_NAME = "Ayah Cutie"
TOTAL_VERSES = 6236

CONFIG_DIR = os.path.join(
    os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "ayah-cutie"
)
CONFIG_PATH = os.path.join(CONFIG_DIR, "widget.json")
AUTOSTART_PATH = os.path.join(
    os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")),
    "autostart",
    "ayah-cutie.desktop",
)

# Where quran.txt and surahs.txt might live: next to this script once installed, or
# still inside the Android module when running straight out of a git checkout.
HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIRS = [
    os.path.join(HERE, "data"),
    os.path.join(HERE, "..", "app", "src", "main", "assets"),
    os.path.expanduser("~/.local/share/ayah-cutie/data"),
]

ARABIC_FONT = "Amiri Quran"
LATIN_FONT = "Quicksand"
ARABIC_FALLBACK = "Noto Naskh Arabic"
LATIN_FALLBACK = "Sans"


# --------------------------------------------------------------------------- themes


def css_rgba(hex_argb):
    """'#AARRGGBB' or '#RRGGBB' -> the rgba(...) string GTK's CSS parser wants."""
    h = hex_argb.lstrip("#")
    if len(h) == 6:
        h = "FF" + h
    a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    return f"rgba({r}, {g}, {b}, {a / 255.0:.3f})"


class Theme:
    """One card look: the same eight the Android widget offers."""

    def __init__(
        self,
        key,
        label,
        stops,
        border,
        border_width=1.5,
        inner_border=None,
        accent="#7C5CE0",
        arabic="#3D3355",
        english="#6B6188",
        button="#40B69CFF",
        button_border="#00000000",
        angle="160deg",
    ):
        self.key = key
        self.label = label
        self.stops = stops                # list of (percent, colour)
        self.border = border
        self.border_width = border_width
        self.inner_border = inner_border  # the second, softer rim the glass styles use
        self.accent = accent
        self.arabic = arabic
        self.english = english
        self.button = button
        self.button_border = button_border
        self.angle = angle

    @property
    def gradient(self):
        stops = ", ".join(f"{css_rgba(c)} {p}%" for p, c in self.stops)
        return f"linear-gradient({self.angle}, {stops})"


THEMES = [
    Theme(
        "cotton", "Cotton Candy",
        [(0, "#FDEAF4"), (100, "#E6E2FF")], border="#55FFFFFF", angle="180deg",
    ),
    Theme(
        "glass", "Frosted Glass",
        [(0, "#73FFFFFF"), (50, "#4DFFFFFF"), (100, "#30FFFFFF")],
        border="#8CFFFFFF", inner_border="#33FFFFFF",
        accent="#5B41B8", arabic="#241E38", english="#3F3760",
        button="#59FFFFFF", button_border="#4DFFFFFF",
    ),
    Theme(
        "glass_dark", "Smoked Glass",
        [(0, "#8C15121F"), (50, "#6612101B"), (100, "#4D0D0B15")],
        border="#59FFFFFF", inner_border="#1FFFFFFF",
        accent="#D8CBFF", arabic="#FFFFFF", english="#DCD6EC",
        button="#26FFFFFF", button_border="#33FFFFFF",
    ),
    Theme(
        "midnight", "Midnight",
        [(0, "#322A5E"), (100, "#14102B")], border="#33FFFFFF",
        accent="#B69CFF", arabic="#F6F3FF", english="#C8C0E4",
        button="#26FFFFFF", button_border="#33FFFFFF",
    ),
    Theme(
        "sunset", "Sunset",
        [(0, "#FFE0BC"), (50, "#FFC0AE"), (100, "#FFAECB")], border="#66FFFFFF",
        accent="#B4406B", arabic="#4A2436", english="#75455B", button="#1A3D3355",
    ),
    Theme(
        "mint", "Mint",
        [(0, "#D3F7E6"), (100, "#C3E6FF")], border="#66FFFFFF",
        accent="#2F8F7A", arabic="#1F3D3A", english="#4C6A67", button="#1A3D3355",
    ),
    Theme(
        "paper", "Paper",
        [(0, "#FFFBF6"), (100, "#FFFBF6")], border="#1A3D3355", button="#1A3D3355",
    ),
    Theme(
        "neon", "Neon",
        [(0, "#160B2E"), (100, "#090616")], border="#CC63E6FF", border_width=2.0,
        inner_border="#3363E6FF",
        accent="#63E6FF", arabic="#EAFBFF", english="#A9C9E8",
        button="#3363E6FF", button_border="#8063E6FF",
    ),
]

THEMES_BY_KEY = {t.key: t for t in THEMES}
DEFAULT_THEME = "cotton"

SIZES = [
    ("Small", 330, 170),
    ("Medium", 430, 220),
    ("Large", 540, 300),
    ("Wide", 720, 210),
]

INTERVALS = [("15 min", 15), ("30 min", 30), ("1 hour", 60), ("3 hours", 180),
             ("6 hours", 360)]

PADDING = 16
GRIP = 22


# ----------------------------------------------------------------------- the Qur'an


class Quran:
    """Same two pipe-separated files the phone reads, kept on disk rather than in RAM."""

    def __init__(self):
        self.dir = self._find_data()
        self.surahs = self._load_surahs()
        self._offsets = None

    @staticmethod
    def _find_data():
        for candidate in DATA_DIRS:
            if os.path.exists(os.path.join(candidate, "quran.txt")):
                return os.path.abspath(candidate)
        sys.exit("Could not find quran.txt. Looked in:\n  " + "\n  ".join(DATA_DIRS))

    def _load_surahs(self):
        names = {}
        with open(os.path.join(self.dir, "surahs.txt"), encoding="utf-8") as handle:
            for line in handle:
                parts = line.rstrip("\n").split("|")
                if len(parts) >= 4 and parts[0].isdigit():
                    names[int(parts[0])] = (parts[1], parts[2], parts[3])
        return names

    def _line_offsets(self):
        """One pass over the file gives us random access without holding 2 MB of text."""
        if self._offsets is None:
            offsets = []
            with open(os.path.join(self.dir, "quran.txt"), "rb") as handle:
                position = 0
                for raw in handle:
                    offsets.append(position)
                    position += len(raw)
            self._offsets = offsets
        return self._offsets

    def verse(self, index):
        offsets = self._line_offsets()
        index = index % len(offsets)
        with open(os.path.join(self.dir, "quran.txt"), "rb") as handle:
            handle.seek(offsets[index])
            line = handle.readline().decode("utf-8").rstrip("\n")
        parts = line.split("|")
        surah = int(parts[0]) if parts and parts[0].isdigit() else 1
        ayah = parts[1] if len(parts) > 1 else "1"
        _arabic_name, english_name, meaning = self.surahs.get(surah, ("", "", ""))
        return {
            "surah": surah,
            "ayah": ayah,
            "arabic": parts[2] if len(parts) > 2 else "",
            "english": parts[3] if len(parts) > 3 else "",
            "reference": f"{english_name} {surah}:{ayah}",
            "meaning": meaning,
        }

    @property
    def total(self):
        return len(self._line_offsets())


# ---------------------------------------------------------------------- preferences


DEFAULTS = {
    "theme": DEFAULT_THEME,
    "width": 430,
    "height": 220,
    "show_arabic": True,
    "show_english": True,
    "interval_minutes": 60,
    "index": None,
    "last_shuffle": 0,
    "opacity": 1.0,
}


class Config(dict):
    def __init__(self):
        super().__init__(DEFAULTS)
        try:
            with open(CONFIG_PATH, encoding="utf-8") as handle:
                stored = json.load(handle)
            self.update({k: v for k, v in stored.items() if k in DEFAULTS})
        except (OSError, ValueError):
            pass
        if self["index"] is None:
            self["index"] = random.randrange(TOTAL_VERSES)

    def save(self):
        os.makedirs(CONFIG_DIR, exist_ok=True)
        tmp = CONFIG_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(dict(self), handle, indent=2)
        os.replace(tmp, CONFIG_PATH)


def font_available(family):
    wanted = family.lower()
    context = Gtk.Label().get_pango_context()
    return any(f.get_name().lower() == wanted for f in context.list_families())


# -------------------------------------------------------------------------- the card


class AyahWidget(Gtk.Window):

    def __init__(self):
        super().__init__(type=Gtk.WindowType.TOPLEVEL)

        self.config = Config()
        self.quran = Quran()
        self.theme = THEMES_BY_KEY.get(self.config["theme"], THEMES_BY_KEY[DEFAULT_THEME])
        self.arabic_font = ARABIC_FONT if font_available(ARABIC_FONT) else ARABIC_FALLBACK
        self.latin_font = LATIN_FONT if font_available(LATIN_FONT) else LATIN_FALLBACK
        self._save_pending = None
        self._tick_source = None

        self.set_title(APP_NAME)
        self.set_decorated(False)
        self.set_app_paintable(True)
        self.set_skip_taskbar_hint(True)
        self.set_skip_pager_hint(True)
        self.set_default_size(self.config["width"], self.config["height"])
        self.set_size_request(240, 130)
        self.get_style_context().add_class("ayah-window")
        # X11 honours these and the card behaves like a proper desktop widget; GNOME's
        # Wayland session ignores them, and it is an ordinary little window instead.
        self.set_keep_below(True)
        self.stick()

        visual = self.get_screen().get_rgba_visual()
        if visual is not None:
            self.set_visual(visual)   # so the corners can actually be round

        self.connect("destroy", Gtk.main_quit)
        self.connect("configure-event", self.on_configure)
        self.connect("button-press-event", self.on_button_press)
        self.add_events(
            Gdk.EventMask.BUTTON_PRESS_MASK | Gdk.EventMask.BUTTON_RELEASE_MASK
        )

        self.build_content()
        self.apply_theme()
        self.show_verse()
        self.schedule_tick()

    # ------------------------------------------------------------------ scaffolding

    def build_content(self):
        overlay = Gtk.Overlay()
        self.add(overlay)

        self.card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        self.card.get_style_context().add_class("ayah-card")
        self.card.set_border_width(PADDING)
        overlay.add(self.card)

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=7)
        self.card.pack_start(header, False, False, 0)

        self.moon = Gtk.Label()
        header.pack_start(self.moon, False, False, 0)

        self.reference = Gtk.Label(xalign=0.0)
        self.reference.set_ellipsize(Pango.EllipsizeMode.END)
        header.pack_start(self.reference, True, True, 0)

        self.shuffle = Gtk.Button(label="↻")
        self.shuffle.set_relief(Gtk.ReliefStyle.NONE)
        self.shuffle.set_size_request(30, 30)
        self.shuffle.set_tooltip_text("Show another ayah")
        self.shuffle.set_can_focus(False)
        self.shuffle.get_style_context().add_class("ayah-shuffle")
        self.shuffle.connect("clicked", lambda *_: self.shuffle_now())
        header.pack_end(self.shuffle, False, False, 0)

        self.arabic = self.verse_label()
        self.card.pack_start(self.arabic, True, True, 0)

        self.english = self.verse_label()
        self.card.pack_start(self.english, True, True, 0)

        # A corner grip, because a frameless window has no edges to grab.
        self.grip = Gtk.EventBox()
        self.grip.set_size_request(GRIP, GRIP)
        self.grip.set_halign(Gtk.Align.END)
        self.grip.set_valign(Gtk.Align.END)
        self.grip.set_visible_window(False)
        self.grip.add_events(Gdk.EventMask.BUTTON_PRESS_MASK)
        self.grip.connect("button-press-event", self.on_grip_press)
        self.grip.connect("realize", self.on_grip_realize)
        self.grip_label = Gtk.Label()
        self.grip_label.set_halign(Gtk.Align.END)
        self.grip_label.set_valign(Gtk.Align.END)
        self.grip.add(self.grip_label)
        overlay.add_overlay(self.grip)

        self.css = Gtk.CssProvider()
        Gtk.StyleContext.add_provider_for_screen(
            self.get_screen(), self.css, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )
        overlay.show_all()

    @staticmethod
    def verse_label():
        """
        A wrapping label asks for as much room as its longest paragraph needs, which
        would let a long ayah shove the card to whatever size it liked. Ellipsizing
        with a line cap keeps the request small, so the window stays the size the
        user picked and the text is fitted to it instead.
        """
        label = Gtk.Label()
        label.set_line_wrap(True)
        label.set_line_wrap_mode(Pango.WrapMode.WORD_CHAR)
        label.set_justify(Gtk.Justification.CENTER)
        label.set_ellipsize(Pango.EllipsizeMode.END)
        label.set_max_width_chars(12)
        label.set_no_show_all(True)
        return label

    def on_grip_realize(self, widget):
        window = widget.get_window()
        if window is not None:
            window.set_cursor(Gdk.Cursor.new_from_name(self.get_display(), "se-resize"))

    # ---------------------------------------------------------------------- styling

    def apply_theme(self):
        theme = self.theme
        inner = (
            f"box-shadow: inset 0 0 0 1px {css_rgba(theme.inner_border)};"
            if theme.inner_border else "box-shadow: none;"
        )
        self.css.load_from_data(f"""
            window.ayah-window {{
                background-color: transparent;
                background-image: none;
            }}
            box.ayah-card {{
                background-image: {theme.gradient};
                border: {theme.border_width}px solid {css_rgba(theme.border)};
                border-radius: 26px;
                {inner}
            }}
            button.ayah-shuffle {{
                background-image: none;
                background-color: {css_rgba(theme.button)};
                border: 1px solid {css_rgba(theme.button_border)};
                border-radius: 15px;
                color: {theme.accent};
                font-size: 15px;
                padding: 0;
                margin: 0;
                min-width: 30px;
                min-height: 30px;
                box-shadow: none;
                text-shadow: none;
            }}
            button.ayah-shuffle:hover {{
                background-color: {css_rgba(theme.accent)};
                color: #FFFFFF;
            }}
        """.encode())
        self.grip_label.set_markup(
            f'<span foreground="{theme.accent}" alpha="55%" size="11000">◢</span>'
        )
        Gtk.Widget.set_opacity(self, self.config["opacity"])
        self.queue_draw()

    # ------------------------------------------------------------------- the verse

    def show_verse(self):
        verse = self.quran.verse(self.config["index"])
        width = max(self.get_allocated_width(), self.config["width"]) - PADDING * 2
        height = max(self.get_allocated_height(), self.config["height"]) - PADDING * 2

        self.moon.set_markup(f'<span foreground="{self.theme.accent}" size="13000">☾</span>')
        self.reference.set_markup(
            f'<span font_desc="{self.latin_font} Bold 9" foreground="{self.theme.accent}">'
            f"{GLib.markup_escape_text(verse['reference'])}</span>"
        )

        # The header row and the gaps around it are space the verse never gets to use.
        body = max(height - 30 - 16, 40)
        show_arabic = self.config["show_arabic"]
        show_english = self.config["show_english"]
        arabic_share = 0.58 if (show_arabic and show_english) else 1.0
        english_share = 0.42 if (show_arabic and show_english) else 1.0

        self.arabic.set_visible(show_arabic)
        self.english.set_visible(show_english)

        if show_arabic:
            size, lines = self.fit(
                verse["arabic"], self.arabic_font, width, body * arabic_share, 9, 26
            )
            self.arabic.set_lines(lines)
            self.arabic.set_markup(
                f'<span font_desc="{self.arabic_font} {size}" '
                f'foreground="{self.theme.arabic}">'
                f"{GLib.markup_escape_text(verse['arabic'])}</span>"
            )
        if show_english:
            size, lines = self.fit(
                verse["english"], self.latin_font, width, body * english_share, 7, 15
            )
            self.english.set_lines(lines)
            self.english.set_markup(
                f'<span font_desc="{self.latin_font} {size}" '
                f'foreground="{self.theme.english}">'
                f"{GLib.markup_escape_text(verse['english'])}</span>"
            )

        self.set_tooltip_text(f"{verse['reference']} · {verse['meaning']}")

    def fit(self, text, family, width, height, min_pt, max_pt):
        """
        Largest point size whose wrapped block still fits the space it was given,
        plus how many lines of it that space holds — an ayah too long even at the
        floor size gets ellipsized rather than pushing the card around.
        """
        layout = self.create_pango_layout(text)
        layout.set_wrap(Pango.WrapMode.WORD_CHAR)
        layout.set_width(int(max(width, 40) * Pango.SCALE))
        size = min_pt
        candidate = max_pt
        while candidate >= min_pt:
            layout.set_font_description(Pango.FontDescription(f"{family} {candidate}"))
            _, block_height = layout.get_pixel_size()
            if block_height <= height:
                size = candidate
                break
            candidate -= 1

        layout.set_font_description(Pango.FontDescription(f"{family} {size}"))
        _, block_height = layout.get_pixel_size()
        per_line = max(1.0, block_height / max(1, layout.get_line_count()))
        return size, max(1, int(height // per_line))

    def shuffle_now(self):
        nxt = random.randrange(self.quran.total)
        if nxt == self.config["index"]:
            nxt = (nxt + 1) % self.quran.total
        self.config["index"] = nxt
        self.config["last_shuffle"] = int(time.time())
        self.show_verse()
        self.save_soon()
        self.schedule_tick()

    def schedule_tick(self):
        if self._tick_source is not None:
            GLib.source_remove(self._tick_source)
        interval = max(1, int(self.config["interval_minutes"])) * 60
        due = self.config["last_shuffle"] + interval - int(time.time())
        self._tick_source = GLib.timeout_add_seconds(
            max(5, min(due, interval)), self.on_tick
        )

    def on_tick(self):
        self._tick_source = None
        self.shuffle_now()   # which re-arms us
        return False

    # ------------------------------------------------------------------ interaction

    def on_button_press(self, _widget, event):
        if event.button == 1 and event.type == Gdk.EventType.BUTTON_PRESS:
            self.begin_move_drag(
                event.button, int(event.x_root), int(event.y_root), event.time
            )
            return True
        if event.button == 3:
            self.build_menu().popup_at_pointer(event)
            return True
        return False

    def on_grip_press(self, _widget, event):
        if event.button == 1:
            self.begin_resize_drag(
                Gdk.WindowEdge.SOUTH_EAST, event.button,
                int(event.x_root), int(event.y_root), event.time,
            )
            return True
        return False

    def on_configure(self, _widget, event):
        if (event.width, event.height) != (self.config["width"], self.config["height"]):
            self.config["width"] = event.width
            self.config["height"] = event.height
            self.show_verse()
            self.save_soon()
        return False

    def save_soon(self):
        """Dragging a corner fires a configure-event per frame; only write once."""
        if self._save_pending is not None:
            GLib.source_remove(self._save_pending)

        def write():
            self._save_pending = None
            self.config.save()
            return False

        self._save_pending = GLib.timeout_add(600, write)

    # ------------------------------------------------------------------------ menu

    def build_menu(self):
        menu = Gtk.Menu()

        item = Gtk.MenuItem(label="New ayah")
        item.connect("activate", lambda *_: self.shuffle_now())
        menu.append(item)

        item = Gtk.MenuItem(label="Copy ayah")
        item.connect("activate", lambda *_: self.copy_verse())
        menu.append(item)

        menu.append(Gtk.SeparatorMenuItem())

        menu.append(self.submenu("Theme", [
            (theme.label, theme.key == self.theme.key, self.pick_theme, theme.key)
            for theme in THEMES
        ]))
        menu.append(self.submenu("Size", [
            (f"{label}  ({w}×{h})",
             (self.config["width"], self.config["height"]) == (w, h),
             self.pick_size, (w, h))
            for label, w, h in SIZES
        ]))
        menu.append(self.submenu("Opacity", [
            (f"{int(level * 100)}%", abs(self.config["opacity"] - level) < 0.01,
             self.pick_opacity, level)
            for level in (1.0, 0.9, 0.75, 0.6)
        ]))
        menu.append(self.submenu("New ayah every", [
            (label, self.config["interval_minutes"] == minutes,
             self.pick_interval, minutes)
            for label, minutes in INTERVALS
        ]))

        menu.append(Gtk.SeparatorMenuItem())

        for label, key in (("Arabic", "show_arabic"),
                           ("English meaning", "show_english")):
            toggle = Gtk.CheckMenuItem(label=label)
            toggle.set_active(self.config[key])
            toggle.connect("toggled", self.on_toggle_text, key)
            menu.append(toggle)

        autostart = Gtk.CheckMenuItem(label="Start at login")
        autostart.set_active(os.path.exists(AUTOSTART_PATH))
        autostart.connect("toggled", self.on_toggle_autostart)
        menu.append(autostart)

        menu.append(Gtk.SeparatorMenuItem())

        item = Gtk.MenuItem(label="Quit")
        item.connect("activate", lambda *_: self.quit())
        menu.append(item)

        menu.show_all()
        return menu

    @staticmethod
    def submenu(title, entries):
        parent = Gtk.MenuItem(label=title)
        inner = Gtk.Menu()
        items = []
        for label, active, handler, value in entries:
            item = Gtk.RadioMenuItem(label=label)
            if items:
                item.join_group(items[0][0])
            items.append((item, active, handler, value))
            inner.append(item)
        # Tick the current choice before wiring anything up: set_active() emits, and a
        # handler firing while the menu is still being built would "pick" for the user.
        for item, active, _handler, _value in items:
            item.set_active(active)
        for item, _active, handler, value in items:
            item.connect(
                "activate",
                lambda widget, h=handler, v=value: widget.get_active() and h(v),
            )
        parent.set_submenu(inner)
        return parent

    def pick_theme(self, key):
        if key == self.theme.key:
            return
        self.theme = THEMES_BY_KEY[key]
        self.config["theme"] = key
        self.apply_theme()
        self.show_verse()
        self.save_soon()

    def pick_size(self, size):
        width, height = size
        self.config["width"], self.config["height"] = width, height
        self.resize(width, height)
        self.show_verse()
        self.save_soon()

    def pick_opacity(self, level):
        self.config["opacity"] = level
        Gtk.Widget.set_opacity(self, level)
        self.save_soon()

    def pick_interval(self, minutes):
        self.config["interval_minutes"] = minutes
        self.schedule_tick()
        self.save_soon()

    def on_toggle_text(self, item, key):
        other = "show_english" if key == "show_arabic" else "show_arabic"
        if not item.get_active() and not self.config[other]:
            item.set_active(True)   # keep at least one of them on
            return
        self.config[key] = item.get_active()
        self.show_verse()
        self.save_soon()

    def on_toggle_autostart(self, item):
        if item.get_active():
            os.makedirs(os.path.dirname(AUTOSTART_PATH), exist_ok=True)
            with open(AUTOSTART_PATH, "w", encoding="utf-8") as handle:
                handle.write(
                    "[Desktop Entry]\n"
                    "Type=Application\n"
                    f"Name={APP_NAME}\n"
                    f"Exec={sys.executable} {os.path.abspath(__file__)}\n"
                    "X-GNOME-Autostart-enabled=true\n"
                )
        elif os.path.exists(AUTOSTART_PATH):
            os.remove(AUTOSTART_PATH)

    def copy_verse(self):
        verse = self.quran.verse(self.config["index"])
        text = f"{verse['arabic']}\n\n“{verse['english']}”\n\n— {verse['reference']}"
        Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD).set_text(text, -1)

    def quit(self):
        self.config.save()
        Gtk.main_quit()


def main():
    widget = AyahWidget()
    widget.show_all()
    widget.show_verse()   # show_all() would otherwise re-show a hidden block
    try:
        Gtk.main()
    except KeyboardInterrupt:
        widget.config.save()


if __name__ == "__main__":
    main()
