// Opens the real M3E Canvas app with design-only fragment documents.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { chromium } = require('C:/Users/joyins/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const base = __dirname;
const previous = path.resolve(base, '../../new-entry-v2/canvas');
const onlyBoard = process.argv[2];
const compact = text => text.replace(/\s+/g, ' ').trim();
const urlFor = doc => 'https://lnkiai.github.io/m3e-canvas/#docz=' +
  zlib.deflateRawSync(Buffer.from(JSON.stringify(doc))).toString('base64url');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 2400, height: 1250 }, deviceScaleFactor: 1 });
    for (const directory of onlyBoard ? [base] : [base, previous]) {
      const reportPath = path.join(directory, 'render-check.json');
      const results = onlyBoard && fs.existsSync(reportPath)
        ? JSON.parse(fs.readFileSync(reportPath, 'utf8')).results.filter(r => r.board !== onlyBoard) : [];
      const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
      for (const board of manifest) {
        if (onlyBoard && board.key !== onlyBoard) continue;
        const errors = [];
        const onError = error => errors.push(String(error));
        page.on('pageerror', onError);
        await page.goto('about:blank');
        await page.goto(board.url, { waitUntil: 'networkidle', timeout: 30000 });
        await page.evaluate(() => document.fonts.ready);
        const fit = page.locator('button').filter({ hasText: 'fit_screen' });
        if (await fit.count()) await fit.first().click();
        await page.waitForTimeout(450);
        const doc = JSON.parse(fs.readFileSync(path.join(directory, board.key+'.json'), 'utf8'));
        const body = compact(await page.locator('body').innerText());
        const missing = doc.groups.flatMap(g => g.items)
          .filter(i => i.label && !body.includes(compact(i.label)))
          .map(i => i.label);
        const frameLabels = doc.frames.map(frame => frame.name);
        await page.screenshot({ path: path.join(directory, board.key+'.png') });
        const result = { board: board.key, frames: doc.frames.length, missing, errors };
        results.push(result);
        console.log(JSON.stringify(result));
        page.off('pageerror', onError);
      }
      fs.writeFileSync(path.join(directory, 'render-check.json'), JSON.stringify({
        checkedAt: new Date().toISOString(),
        tool: 'M3E Canvas in headless Microsoft Edge',
        scope: 'Static design rendering only; no Android build or data migration',
        results
      }, null, 2));
      if (results.some(r => r.missing.length || r.errors.length)) throw new Error('Canvas import/render check failed');
    }
    // Individual close-ups remain editable through the board links.
    for (const [directory, key, frameIndex, output] of [
      [base, '01-create', 1, 'new-password'],
      [base, '01-create', 2, 'collapsed-title'],
      [base, '02-linked-details', 2, 'note-detail'],
      [base, '04-batch', 2, 'batch-item'],
      [previous, '07-type-switch', 0, 'type-below-title'],
      [previous, '07-type-switch', 3, 'collapsed-title'],
      [previous, '01-password-flow', 0, 'password-detail'],
    ]) {
      if (onlyBoard && key !== onlyBoard) continue;
      const doc = JSON.parse(fs.readFileSync(path.join(directory, key+'.json'), 'utf8'));
      const frame = doc.frames[frameIndex];
      doc.groups = doc.groups.filter(g => g.x >= frame.x && g.x < frame.x+412).map(g => ({ ...g, x: g.x-frame.x }));
      doc.frames = [{ ...frame, x: 0 }];
      await page.setViewportSize({ width: 1100, height: 1120 });
      for (const dark of output === 'new-password' || output === 'password-detail' ? [true, false] : [true]) {
        doc.theme.dark = dark;
        await page.goto('about:blank');
        await page.goto(urlFor(doc), { waitUntil: 'networkidle', timeout: 30000 });
        await page.evaluate(() => document.fonts.ready);
        await page.locator('button').filter({ hasText: 'fit_screen' }).first().click();
        await page.waitForTimeout(400);
        await page.screenshot({ path: path.join(directory, output+(dark ? '' : '-light')+'.png') });
      }
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
