// Renders the PNG exports from the SVGs written by generate.py, using Playwright's Chromium.
// Usage: node design/icon/render.mjs   (needs the `playwright` package resolvable, e.g. NODE_PATH)
import { readFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");

const here = dirname(fileURLToPath(import.meta.url));
const res = join(here, "..", "..", "app", "src", "main", "res");
const png = join(here, "png");

// [svg, output path, width px, height px]
const jobs = [
  ["camgrid-icon.svg", join(png, "camgrid-icon-512.png"), 512, 512],
  ["camgrid-icon.svg", join(png, "camgrid-icon-1024.png"), 1024, 1024],
  ["camgrid-mark.svg", join(png, "camgrid-mark-512.png"), 512, 512],
  ["camgrid-tv-banner.svg", join(png, "camgrid-tv-banner-640x360.png"), 640, 360],
  ["camgrid-social-preview.svg", join(png, "camgrid-social-preview-1280x640.png"), 1280, 640],
  ["camgrid-tv-banner.svg", join(res, "drawable-xhdpi", "banner.png"), 640, 360],
  ...Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 }).map(([d, px]) => [
    "camgrid-icon-legacy.svg",
    join(res, `mipmap-${d}`, "ic_launcher.png"),
    px,
    px,
  ]),
];

const browser = await chromium.launch();
for (const [svg, out, w, h] of jobs) {
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  const markup = readFileSync(join(here, svg), "utf8").replace(/width="\d+" height="\d+"/, `width="${w}" height="${h}"`);
  await page.setContent(`<html><body style="margin:0;background:transparent">${markup}</body></html>`);
  mkdirSync(dirname(out), { recursive: true });
  await page.screenshot({ path: out, omitBackground: true });
  await page.close();
  console.log(out);
}
await browser.close();
