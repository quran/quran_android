// Renders the app icon set into assets/ using the preinstalled Playwright Chromium.
// Usage: node scripts/make-icons.js
const { chromium } = require('playwright');
const path = require('path');

const GREEN = '#1F6F5C';
const GREEN_DARK = '#164F42';
const GOLD = '#E3B964';
const CREAM = '#FBF4E6';

// eight-pointed star (two squares) with a ring and a dot, centred in a 100x100 box
function star(fill, ring, dot, scale = 1) {
  const s = 30 * scale;
  const sq = `x="${50 - s}" y="${50 - s}" width="${2 * s}" height="${2 * s}" rx="${2 * scale}"`;
  return `
    <g>
      <rect ${sq} fill="${fill}" />
      <rect ${sq} fill="${fill}" transform="rotate(45 50 50)" />
      <circle cx="50" cy="50" r="${17 * scale}" fill="none" stroke="${ring}" stroke-width="${3.2 * scale}" />
      <circle cx="50" cy="50" r="${6 * scale}" fill="${dot}" />
    </g>`;
}

const svg = (body, bg) => `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100%" height="100%">
  <defs>
    <radialGradient id="g" cx="50%" cy="35%" r="75%">
      <stop offset="0%" stop-color="${GREEN}" />
      <stop offset="100%" stop-color="${GREEN_DARK}" />
    </radialGradient>
  </defs>
  ${bg ?? ''}
  ${body}
</svg>`;

const bgFull = '<rect width="100" height="100" fill="url(#g)" />';

const icons = [
  // iOS / store icon: full bleed, no transparency
  { file: 'icon.png', size: 1024, html: svg(star(GOLD, GREEN_DARK, CREAM, 1.05), bgFull) },
  // Android adaptive icon: foreground must sit inside the central ~66% safe zone
  { file: 'android-icon-foreground.png', size: 1024, html: svg(star(GOLD, GREEN_DARK, CREAM, 0.72)), transparent: true },
  { file: 'android-icon-background.png', size: 1024, html: svg('', bgFull) },
  // monochrome (themed icons): only alpha matters, so cut the ring out with a mask
  {
    file: 'android-icon-monochrome.png',
    size: 1024,
    html: svg(
      `<mask id="m"><rect width="100" height="100" fill="white" />${star('none', 'black', 'none', 0.72).replace(/fill="none"/g, 'fill="transparent"')}</mask>` +
        `<g mask="url(#m)">${star('#FFFFFF', 'none', 'none', 0.72)}</g>` +
        star('none', 'none', '#FFFFFF', 0.72)
    ),
    transparent: true,
  },
  // splash: shown centred on the theme background
  { file: 'splash-icon.png', size: 1024, html: svg(star(GOLD, GREEN, CREAM, 1.2)), transparent: true },
  { file: 'favicon.png', size: 48, html: svg(star(GOLD, GREEN_DARK, CREAM, 1.05), bgFull) },
];

(async () => {
  const browser = await chromium.launch();
  for (const icon of icons) {
    const page = await browser.newPage({ viewport: { width: icon.size, height: icon.size } });
    await page.setContent(
      `<html><body style="margin:0;background:transparent;width:${icon.size}px;height:${icon.size}px">${icon.html}</body></html>`
    );
    await page.screenshot({
      path: path.join(__dirname, '..', 'assets', icon.file),
      omitBackground: !!icon.transparent,
    });
    await page.close();
    console.log('wrote', icon.file);
  }
  await browser.close();
})();
