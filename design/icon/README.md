# CamGrid icon

A 2x2 grid of camera tiles with a lens in the middle; the red tile is the live camera.
All artwork is original and released under the repository's MIT license. The wordmark uses
[Inter](https://github.com/rsms/inter) (SIL Open Font License 1.1), baked into the SVGs as outlines.

| File | Use |
| --- | --- |
| `camgrid-icon.svg` | Full-bleed square icon (project image, 512/1024 PNG) |
| `camgrid-icon-legacy.svg` | Launcher icon for Android 7.1 and older (mipmap PNGs) |
| `camgrid-mark.svg` | The mark on a transparent background |
| `camgrid-tv-banner.svg` | Fire TV / Android TV banner, 320x180 dp |
| `camgrid-social-preview.svg` | GitHub social preview, 1280x640 |

## Regenerating

`generate.py` holds the geometry and colours. It writes the SVGs above and the adaptive icon layers
in `app/src/main/res/drawable/ic_launcher_*.xml`. `render.mjs` renders the PNGs, including the legacy
mipmaps and `drawable-xhdpi/banner.png`.

```sh
pip install fonttools
npm pack @fontsource/inter@5 && tar xzf fontsource-inter-*.tgz
python3 design/icon/generate.py package/files/inter-latin-800-normal.woff package/files/inter-latin-500-normal.woff
node design/icon/render.mjs   # needs the playwright package and its Chromium
```
