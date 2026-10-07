# View format

A view is CamGrid's screen layout. Views are stored in the app's (encrypted) config file as JSON and are meant to be served from elsewhere later, so a view never contains stream URLs or credentials: tiles refer to cameras by id only. The cameras themselves, with their URLs, stay on the device.

```json
{
  "id": "kitchen",
  "name": "Kitchen wall",
  "columns": 4,
  "rows": 2,
  "tiles": [
    { "x": 0, "y": 0, "w": 1, "h": 2, "camera": "go2rtc:front_door", "fit": "CROP" },
    { "x": 1, "y": 0, "w": 1, "h": 2, "camera": "go2rtc:garden",     "fit": "CROP" },
    { "x": 2, "y": 0, "w": 2, "h": 1, "camera": null,                 "fit": "FIT" },
    { "x": 2, "y": 1, "w": 2, "h": 1, "camera": null,                 "fit": "FIT" }
  ]
}
```

This is the example of two portrait tiles side by side, then two landscape tiles stacked. On a 16:9 screen a 4x2 canvas gives cells of 4:4.5, so a 1x2 tile is 4:9 (portrait) and a 2x1 tile is 16:9.

| Field | Meaning |
| --- | --- |
| `id` | Stable, unique id. Not blank. |
| `name` | Shown in settings and the page indicator. Optional. |
| `columns`, `rows` | The cell canvas, 1 to 12 each. Cells are stretched to fill the screen, so they are only square when the screen's aspect ratio matches. |
| `tiles` | At most 16. Each covers cells `x .. x+w-1`, `y .. y+h-1` (zero-based). Tiles must lie inside the canvas and must not overlap. Cells may stay empty. |
| `camera` | A camera id, or `null` for an auto tile. |
| `fit` | `FIT` (whole picture, black bars) or `CROP` (fills the tile, edges cut off). Defaults to `FIT`; unknown values fall back to `FIT`. |

Auto tiles take the cameras not fixed elsewhere in the same view, in the camera order from settings. When there are more such cameras than auto tiles, the view continues on further pages, with the fixed tiles unchanged.

A config file holds `views` (at least one, unique ids), `cameras`, `go2rtcBaseUrl` and `version`. Version 1 files had a single `layout` of `columns` x `rows` (1 to 4 each) instead of `views`; they load as one uniform view with the id `main`, whose auto tiles page exactly like the old grid. The same file, optionally encrypted, is what Settings > Backup exports; see [backup-format.md](backup-format.md).

Views are edited in the app under Settings > Views (see the [user guide](user-guide.md#views)). The editor only produces valid views: a move or resize that would leave the canvas or overlap another tile is ignored, and shrinking the canvas removes tiles that start outside it and cuts the rest to fit.

Keep live streams per page in mind: every tile with a camera is one decoder. A Fire TV Stick handles about 4 at once, and the editor shows a warning when a view has more than 4 tiles.
