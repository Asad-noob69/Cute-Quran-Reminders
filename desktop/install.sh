#!/usr/bin/env bash
# Installs the fonts, the verse files and a launcher for the desktop widget.
# Everything lands under $HOME — no root, nothing outside your user account.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(dirname "$here")"

fonts="$HOME/.local/share/fonts/ayah-cutie"
data="$HOME/.local/share/ayah-cutie/data"
apps="$HOME/.local/share/applications"

echo "Installing fonts to $fonts"
mkdir -p "$fonts"
cp "$repo"/app/src/main/res/font/*.ttf "$fonts"/
fc-cache -f "$fonts" >/dev/null

echo "Copying the Qur'an to $data"
mkdir -p "$data"
cp "$repo"/app/src/main/assets/quran.txt "$repo"/app/src/main/assets/surahs.txt "$data"/

echo "Adding a launcher to $apps"
mkdir -p "$apps"
cat > "$apps/ayah-cutie.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Ayah Cutie
Comment=A random Qur'an verse on your desktop
Exec=python3 $here/ayah_widget.py
Icon=$here/ayah-cutie.svg
Terminal=false
Categories=Utility;
EOF

echo
echo "Done. Start it from your app grid, or run:"
echo "  python3 $here/ayah_widget.py"
echo
echo "To undo: rm -rf '$fonts' '$data' '$apps/ayah-cutie.desktop' \\"
echo "         \"\$HOME/.config/ayah-cutie\" \"\$HOME/.config/autostart/ayah-cutie.desktop\""
