#!/usr/bin/env python3
"""Desktop installer icons from design/icon/png/camgrid-icon-1024.png.

Writes camgrid.png (Linux, 512 px), camgrid.ico (Windows, 16 to 256 px) and camgrid.icns
(macOS, 16 to 1024 px, PNG-compressed entries). Needs ImageMagick (`convert`) on PATH.

    python3 desktop/icons/generate.py
"""
import pathlib
import struct
import subprocess
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE.parent.parent / "design" / "icon" / "png" / "camgrid-icon-1024.png"

# icns entry types holding PNG data, by pixel size.
ICNS_TYPES = [
    (b"icp4", 16), (b"icp5", 32), (b"icp6", 64), (b"ic07", 128),
    (b"ic08", 256), (b"ic09", 512), (b"ic10", 1024),
]


def resized_png(size: int, tmp: pathlib.Path) -> bytes:
    out = tmp / f"{size}.png"
    subprocess.run(["convert", str(SOURCE), "-resize", f"{size}x{size}", f"PNG32:{out}"], check=True)
    return out.read_bytes()


def main() -> None:
    with tempfile.TemporaryDirectory() as t:
        tmp = pathlib.Path(t)
        (HERE / "camgrid.png").write_bytes(resized_png(512, tmp))
        subprocess.run(
            ["convert", str(SOURCE), "-define", "icon:auto-resize=256,128,64,48,32,16", str(HERE / "camgrid.ico")],
            check=True,
        )
        entries = b""
        for kind, size in ICNS_TYPES:
            data = resized_png(size, tmp)
            entries += kind + struct.pack(">I", len(data) + 8) + data
        (HERE / "camgrid.icns").write_bytes(b"icns" + struct.pack(">I", len(entries) + 8) + entries)


if __name__ == "__main__":
    main()
