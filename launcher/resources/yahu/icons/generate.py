"""Generates the Yahu icon theme.

Every icon is drawn on a 24px grid with 2px square-capped strokes. One element per icon is drawn in the
grass accent so the set reads as a family. Run from this folder: python generate.py
"""

import os

TEXT = "#ECF3EA"
ACCENT = "#5BE36B"
GOLD = "#FFC94A"
DANGER = "#FF5A5F"

# name -> list of (path data, color, filled)
ICONS = {
    "new": [("M12 5v14M5 12h14", ACCENT, False), ("M3 3h4M3 3v4M21 3h-4M21 3v4M3 21h4M3 21v-4M21 21h-4M21 21v-4", TEXT, False)],
    "launch": [("M7 4l13 8-13 8z", ACCENT, True)],
    "kill": [("M6 6h12v12H6z", DANGER, True)],
    "settings": [("M4 7h10M18 7h2M4 17h2M10 17h10", TEXT, False), ("M14 5h4v4h-4zM6 15h4v4H6z", ACCENT, False)],
    "instance-settings": [("M3 5h18v14H3z", TEXT, False), ("M7 10h4M15 10h2M7 14h2M13 14h4", ACCENT, False)],
    "accounts": [("M8 4h8v8H8z", TEXT, False), ("M4 21v-3l3-3h10l3 3v3", ACCENT, False)],
    "noaccount": [("M8 4h8v8H8z", TEXT, False), ("M4 21v-3l3-3h10l3 3v3", TEXT, False), ("M17 3l4 4M21 3l-4 4", DANGER, False)],
    "viewfolder": [("M3 6h7l2 2h9v11H3z", TEXT, False), ("M7 13h10", ACCENT, False)],
    "centralmods": [("M3 6h7l2 2h9v11H3z", TEXT, False), ("M10 11h4v4h-4z", ACCENT, True)],
    "help": [("M4 4h16v16H4z", TEXT, False), ("M9 9.5V9h1V8h4v1h1v2h-1v1h-2v2", ACCENT, False), ("M11 16h2v2h-2z", ACCENT, True)],
    "about": [("M4 4h16v16H4z", TEXT, False), ("M12 11v6", ACCENT, False), ("M11 7h2v2h-2z", ACCENT, True)],
    "bug": [("M8 8h8v11H8z", TEXT, False), ("M10 5h4v3h-4z", ACCENT, True), ("M4 10h4M16 10h4M4 15h4M16 15h4", TEXT, False)],
    "checkupdate": [("M19 12a7 7 0 1 1-2-5", TEXT, False), ("M18 3v5h-5", ACCENT, False)],
    "refresh": [("M19 12a7 7 0 1 1-2-5", TEXT, False), ("M18 3v5h-5", ACCENT, False)],
    "copy": [("M8 8h12v12H8z", TEXT, False), ("M4 16V4h12", ACCENT, False)],
    "delete": [("M4 6h16M9 6V3h6v3", TEXT, False), ("M6 6l1 15h10l1-15", TEXT, False), ("M10 10v7M14 10v7", DANGER, False)],
    "export": [("M4 14v6h16v-6", TEXT, False), ("M12 15V3M7 8l5-5 5 5", ACCENT, False)],
    "rename": [("M4 20h4L19 9l-4-4L4 16z", TEXT, False), ("M13 7l4 4", ACCENT, False), ("M12 20h8", ACCENT, False)],
    "shortcut": [("M4 4h16v16H4z", TEXT, False), ("M9 15l6-6M10 9h5v5", ACCENT, False)],
    "tag": [("M3 3h9l9 9-9 9-9-9z", TEXT, False), ("M7 7h3v3H7z", ACCENT, True)],
    "star": [("M12 3l2.6 5.6 6.2.7-4.6 4.2 1.3 6.1L12 16.5 6.5 19.6l1.3-6.1-4.6-4.2 6.2-.7z", GOLD, True)],
    "log": [("M5 3h14v18H5z", TEXT, False), ("M8 8h8M8 12h8M8 16h5", ACCENT, False)],
    "notes": [("M5 3h10l4 4v14H5z", TEXT, False), ("M8 11h8M8 15h6", ACCENT, False)],
    "news": [("M3 5h14v14H3z", TEXT, False), ("M17 9h4v10h-4", TEXT, False), ("M6 9h8M6 13h8M6 16h5", ACCENT, False)],
    "language": [("M3 5h18v12H8l-5 4z", TEXT, False), ("M8 9h8M8 13h5", ACCENT, False)],
    "java": [("M5 10h12v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z", TEXT, False), ("M17 11h2v4h-2", TEXT, False), ("M9 3v4M13 3v4", ACCENT, False)],
    "proxy": [("M3 6h7v5H3zM14 13h7v5h-7z", TEXT, False), ("M10 8h4v7", ACCENT, False)],
    "server": [("M4 4h16v7H4zM4 13h16v7H4z", TEXT, False), ("M7 7h2v1H7zM7 16h2v1H7z", ACCENT, True)],
    "unknown_server": [("M4 4h16v7H4zM4 13h16v7H4z", TEXT, False), ("M15 7h2v1h-2zM15 16h2v1h-2z", DANGER, True)],
    "externaltools": [("M14 4l6 6-3 3-6-6zM11 7L3 15v6h6l8-8", TEXT, False), ("M3 21l5-5", ACCENT, False)],
    "custom-commands": [("M3 4h18v16H3z", TEXT, False), ("M7 9l3 3-3 3", ACCENT, False), ("M12 15h5", ACCENT, False)],
    "lock": [("M5 11h14v10H5z", TEXT, False), ("M8 11V6h8v5", TEXT, False), ("M11 14h2v4h-2z", ACCENT, True)],
    "unlock": [("M5 11h14v10H5z", TEXT, False), ("M8 11V6h8v1", TEXT, False), ("M11 14h2v4h-2z", ACCENT, True)],
    "worlds": [("M4 4h16v16H4z", TEXT, False), ("M4 13h5v-3h4v4h7", ACCENT, False)],
    "screenshots": [("M3 6h18v14H3z", TEXT, False), ("M8 3h8v3H8z", TEXT, False), ("M10 10h4v4h-4z", ACCENT, False)],
    "screenshot-placeholder": [("M3 6h18v14H3z", TEXT, False), ("M3 16l5-5 4 4 3-3 6 6", ACCENT, False)],
    "resourcepacks": [("M4 7l8-4 8 4-8 4z", ACCENT, False), ("M4 12l8 4 8-4M4 17l8 4 8-4", TEXT, False)],
    "datapacks": [("M4 4h16v16H4z", TEXT, False), ("M8 8h3v3H8zM13 13h3v3h-3z", ACCENT, True), ("M13 8h3v3h-3zM8 13h3v3H8z", TEXT, False)],
    "shaderpacks": [("M12 3l9 9-9 9-9-9z", TEXT, False), ("M12 7v10", ACCENT, False), ("M7 12h10", GOLD, False)],
    "loadermods": [("M4 9h6V4h4v5h6v6h-6v5h-4v-5H4z", TEXT, False), ("M10 10h4v4h-4z", ACCENT, True)],
    "coremods": [("M4 9h6V4h4v5h6v6h-6v5h-4v-5H4z", TEXT, False), ("M10 10h4v4h-4z", GOLD, True)],
    "appearance": [("M4 4h16v16H4z", TEXT, False), ("M4 4l16 16", ACCENT, False)],
    "cat": [("M5 9V4l4 3h6l4-3v5l1 4-3 7H7l-3-7z", TEXT, False), ("M9 12h1M14 12h1", ACCENT, False)],
    "launcher": [("M4 4h16v16H4z", TEXT, False), ("M8 7l3 4v6M16 7l-3 4", ACCENT, False)],
    "status-good": [("M5 5h14v14H5z", ACCENT, True), ("M8.5 12.5l2.5 2.5 4.5-5", "#0A0F0B", False)],
    "status-yellow": [("M5 5h14v14H5z", GOLD, True), ("M11 8h2v5h-2zM11 15h2v2h-2z", "#0A0F0B", True)],
    "status-bad": [("M5 5h14v14H5z", DANGER, True), ("M9 9l6 6M15 9l-6 6", "#0A0F0B", False)],
    "status-running": [("M5 5h14v14H5z", ACCENT, True), ("M10 8.5l5 3.5-5 3.5z", "#0A0F0B", True)],
    "discord": [("M4 6h16v10H10l-4 4v-4H4z", TEXT, False), ("M9 10h1M14 10h1", ACCENT, False)],
    "matrix": [("M4 4h3M4 4v16h3M20 4h-3M20 4v16h-3", TEXT, False), ("M9 9h6v6H9z", ACCENT, False)],
    "reddit-alien": [("M5 10h14v9H5z", TEXT, False), ("M12 10V5h4", ACCENT, False), ("M9 14h1M14 14h1", ACCENT, False)],
}


def render(parts):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">']
    for d, color, filled in parts:
        if filled:
            out.append(f'<path d="{d}" fill="{color}"/>')
        else:
            out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2" stroke-linecap="square" stroke-linejoin="miter"/>')
    out.append("</svg>\n")
    return "".join(out)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    scalable = os.path.join(here, "scalable")
    os.makedirs(scalable, exist_ok=True)
    for name, parts in ICONS.items():
        with open(os.path.join(scalable, name + ".svg"), "w", newline="\n") as f:
            f.write(render(parts))

    with open(os.path.join(here, "index.theme"), "w", newline="\n") as f:
        f.write("[Icon Theme]\nName=Yahu\nComment=The Yahu Client icon set\nInherits=multimc\nDirectories=scalable\n\n"
                "[scalable]\nSize=48\nType=Scalable\nMinSize=16\nMaxSize=256\n")

    with open(os.path.join(here, "yahu_icons.qrc"), "w", newline="\n") as f:
        f.write('<!DOCTYPE RCC>\n<RCC version="1.0">\n    <qresource prefix="/icons/yahu">\n        <file>index.theme</file>\n')
        for name in sorted(ICONS):
            f.write(f"        <file>scalable/{name}.svg</file>\n")
        f.write("    </qresource>\n</RCC>\n")


if __name__ == "__main__":
    main()
