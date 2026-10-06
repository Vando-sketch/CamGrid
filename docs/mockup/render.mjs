// Renders the README images in docs/images/ from the UI mockup in this folder, using Playwright's
// Chromium. The mockup is a clickable HTML copy of the app's screens with simulated video and
// example cameras (192.0.2.x is a documentation-only address range), so the images are mockups,
// not device screenshots.
// Usage: node docs/mockup/render.mjs   (needs the `playwright` package resolvable, e.g. NODE_PATH)
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "..", "images");
mkdirSync(out, { recursive: true });

// Only the 16:9 app screen is captured, at 960x540 CSS px and 2x scale (1920x1080 PNG).
const SCREEN_CSS = `
  .wrap { max-width: none !important; }
  .stage { grid-template-columns: 980px 210px !important; }
  .screen { width: 960px !important; font-size: 14px !important; }`;

// Grain on the fake video uses Math.random; a seeded generator keeps re-renders identical.
const SEEDED_RANDOM = () => {
  let s = 42;
  Math.random = () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 1000 }, deviceScaleFactor: 2 });
await page.clock.setFixedTime(new Date("2026-10-06T07:42:15"));
await page.addInitScript(SEEDED_RANDOM);
await page.goto(pathToFileURL(join(here, "camgrid-mockup.html")).href);
await page.addStyleTag({ content: SCREEN_CSS });
await page.evaluate(() => document.fonts.ready);

const screen = page.locator("#screen");
const tab = (name) => page.locator(".tab", { hasText: name }).click();
const shot = async (file) => {
  await screen.screenshot({ path: join(out, file), animations: "disabled" });
  console.log("wrote", file);
};
// Lets the simulated streams finish "connecting".
const settle = (ms = 3200) => page.waitForTimeout(ms);

// Hands the arrow keys to the app screen (a clicked tab keeps focus and ignores them).
const remote = async (...keys) => {
  await page.evaluate(() => document.activeElement?.blur());
  for (const key of keys) await page.keyboard.press(key);
  await page.waitForTimeout(300);
};

// Hero: 3x2 grid with six cameras. One example camera stays offline to show the retry badge.
await tab("Settings");
await page.locator('[data-a="c+"]').click();
await page.locator('[data-a="done"]').click();
await settle();
await shot("grid.png");

// Fullscreen with its overlay (shown for a few seconds after opening).
await tab("Fullscreen");
await page.waitForTimeout(1500);
await shot("fullscreen.png");

// Settings.
await tab("Settings");
await page.waitForTimeout(300);
await shot("settings.png");

// go2rtc import with the suggested cameras listed.
await tab("go2rtc import");
await page.waitForTimeout(300);
await shot("go2rtc-import.png");

// Last, because key presses leave the mockup in key mode (focus rings on every screen).
// 2x2 grid as on the Fire TV: the D-pad moves past the right edge to page 2, with focus shown.
await tab("Settings");
await page.locator('[data-a="c-"]').click();
await page.locator('[data-a="done"]').click();
await settle();
await remote("ArrowRight", "ArrowRight");
await settle();
await shot("grid-paging.png");

await browser.close();
