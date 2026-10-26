// CI copy of tools/playwright/smoke.cjs from the migration workspace (commit de0920c); unchanged below this line. See .github/CI.md.
// Smoke check: log in, open each route by URL and each view from the menu, fail on console/page errors, HTTP >= 400,
// collapsed layouts or a visit that left the app (ended on a blocked host).
// Usage: node tools/playwright/smoke.cjs <config.json> <baseUrl> <outDir>
// Writes <outDir>/<user>-<view>.png, <outDir>/<user>-route-<path>.png (full page), <outDir>/smoke.json and
// <outDir>/blocked-requests.json. Exit 1 on any failure.
// Only the base URL's host (plus PW_ALLOW_HOSTS) is reachable; other requests are aborted and logged. PW_ALLOW_HOSTS=* turns that off.
const fs = require('fs');
const os = require('os');
const { launch, path, isBlockedNote } = require('./launch.cjs');

// A user's password: inline `password`, or by reference so it stays out of the tracked config: `passwordEnv` (an env
// var name) or `passwordFile` (outside the repo; relative to the config file, ~ for home) with `passwordKey` (a
// KEY=VALUE or Java properties line; without a key the whole file minus a final newline). Errors name the source, never the value.
function passwordOf(user, cfgDir) {
  const given = ['password', 'passwordEnv', 'passwordFile'].filter(k => user[k] !== undefined);
  if (given.length !== 1) throw new Error(`user ${user.name}: give exactly one of password, passwordEnv, passwordFile (got ${given.join(', ') || 'none'})`);
  if (user.password !== undefined) return String(user.password);
  if (user.passwordEnv !== undefined) {
    const v = process.env[user.passwordEnv];
    if (!v) throw new Error(`user ${user.name}: env var ${user.passwordEnv} is not set`);
    return v;
  }
  const file = path.resolve(cfgDir, String(user.passwordFile).replace(/^~(?=$|\/)/, os.homedir()));
  let text;
  try { text = fs.readFileSync(file, 'utf8'); } catch (e) { throw new Error(`user ${user.name}: cannot read passwordFile ${file} (${e.code})`); }
  if (!user.passwordKey) return text.replace(/\r?\n$/, '');
  for (const line of text.split(/\r?\n/)) {
    const l = line.replace(/^\s+/, '');
    if (!l || l[0] === '#' || l[0] === '!') continue;
    const m = /^([^=:\s]+)\s*[=:]\s?(.*)$/.exec(l);
    if (m && m[1] === user.passwordKey) return m[2].replace(/^(["'])(.*)\1$/, '$2');
  }
  throw new Error(`user ${user.name}: key ${user.passwordKey} not found in ${file}`);
}

(async () => {
  const [cfgPath, base, out] = process.argv.slice(2);
  if (!cfgPath || !base || !out) {
    console.error('Usage: node tools/playwright/smoke.cjs <config.json> <baseUrl> <outDir>  (config shape: tools/playwright/README.md)');
    process.exit(2);
  }
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
  fs.mkdirSync(out, { recursive: true });
  // Resolve every password before the browser starts, so a missing one fails fast.
  const passwords = cfg.users.map(u => passwordOf(u, path.dirname(path.resolve(cfgPath))));
  const { browser, newContext, blocked, allowHosts, isBlockedUrl } = await launch({ allowHosts: [base] });
  const results = [];
  const ignore = (cfg.ignoreScrollSelectors || ['.v-grid-scroller', '.v-grid-tablewrapper', '.v-grid-body', '.v-table-body']).join(',');
  // File names from user and view names: lower case, [a-z0-9-] only.
  const slug = s => String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'x';
  const writeResults = () => {
    fs.writeFileSync(path.join(out, 'smoke.json'), JSON.stringify(results, null, 2));
    fs.writeFileSync(path.join(out, 'blocked-requests.json'), JSON.stringify({ allowHosts, blocked }, null, 2));
  };
  try {
  for (const [ui, user] of cfg.users.entries()) {
    // Fresh context per user: a shared one keeps the previous user's session cookie.
    const context = await newContext();
    const page = await context.newPage();
    const errors = [], badResponses = [];
    page.on('pageerror', e => errors.push('pageerror: ' + e.message.split('\n')[0]));
    // A blocked request (aborted, or refused by the proxy with a 403) is in blocked-requests.json, not an error.
    const blockedMsg = m => isBlockedNote(m.text()) || isBlockedUrl(m.location().url);
    page.on('console', m => { if (m.type() === 'error' && !blockedMsg(m)) errors.push('console: ' + m.text().split('\n')[0]); });
    page.on('response', r => { if (r.status() >= 400 && !isBlockedUrl(r.url())) badResponses.push(r.status() + ' ' + r.url()); });
    // A main-frame navigation to a blocked host (a redirect out, refused as a failed https tunnel): the visit left the app.
    let blockedNav = null;
    page.on('requestfailed', r => { if (r.isNavigationRequest() && r.frame() === page.mainFrame() && isBlockedUrl(r.url())) blockedNav = r.url(); });
    await page.goto(base + (cfg.loginPath || '/'));
    await page.fill(cfg.login.user, user.username);
    await page.fill(cfg.login.password, passwords[ui]);
    await page.click(cfg.login.submit);
    await page.waitForSelector(cfg.loggedInSelector, { state: 'attached', timeout: 30000 });
    // Routes first, opened by URL: an app whose router has no routes fails here even if the menu works.
    const visits = (user.routes || cfg.routes || []).map(route => ({ route }))
      .concat((user.views || []).map(view => ({ view })));
    for (const { route, view } of visits) {
      const before = errors.length, beforeBad = badResponses.length, beforeBlocked = blocked.length;
      blockedNav = null;
      if (route !== undefined) {
        // HTTP >= 400 (e.g. a 404 for an unregistered route) is caught by the response listener above.
        // goto returns null for hash routes (#!view); that is not a failure.
        // A goto that fails because it was redirected to a blocked host is reported as leftApp, not as a goto error.
        await page.goto(base + route).catch(e => { if (!blockedNav) errors.push('goto: ' + e.message.split('\n')[0]); });
      } else {
        // Substring match by default ("Users" also matches "Users and roles"); exactNav matches the whole text.
        const nav = cfg.exactNav ? page.locator(cfg.navSelector).filter({ hasText: new RegExp('^\\s*' + view.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*$') })
          : page.locator(cfg.navSelector, { hasText: view });
        await nav.first().click();
      }
      await page.waitForTimeout(cfg.settleMs || 3000);
      // Tallest scroll container: catches layouts that collapse and stack content far below the fold.
      // Virtual-scrolling grids are ignored: their scroll height is row count x row height by design.
      const maxScroll = await page.evaluate(sel =>
        Math.max(document.body.scrollHeight, ...[...document.querySelectorAll('*')]
          .filter(e => !sel || !e.closest(sel)).map(e => e.scrollHeight)), ignore);
      const viewportHeight = page.viewportSize().height;
      const file = path.join(out, `${slug(user.name)}-${route !== undefined ? 'route-' + slug(route) : slug(view)}.png`);
      await page.screenshot({ path: file, fullPage: true });
      const r = {
        user: user.name, ...(route !== undefined ? { route } : { view }), screenshot: file, maxScrollHeight: maxScroll,
        errors: errors.slice(before), badResponses: badResponses.slice(beforeBad), blocked: blocked.length - beforeBlocked,
        // The URL outside the allow-list the visit ended on (redirected or navigated out), else null. Fails the visit.
        leftApp: isBlockedUrl(page.url()) ? page.url() : blockedNav,
      };
      r.pass = r.errors.length === 0 && r.badResponses.length === 0 && !r.leftApp && maxScroll <= (cfg.maxScrollFactor || 4) * viewportHeight;
      results.push(r);
      console.log(`${r.pass ? 'PASS' : 'FAIL'} ${user.name} ${route !== undefined ? 'route ' + route : view} scroll=${maxScroll} errors=${r.errors.length} http>=400=${r.badResponses.length} blocked=${r.blocked}${r.leftApp ? ` LEFT THE APP to ${r.leftApp}` : ''}`);
    }
    await page.close();
    await context.close();
  }
  } finally {
    writeResults();
    await browser.close();
  }
  const failed = results.filter(r => !r.pass).length;
  console.log(`${results.length - failed} of ${results.length} view checks pass. Look at the screenshots before claiming a view renders.`);
  console.log(allowHosts ? `${blocked.length} request(s) to other hosts than ${allowHosts.join(', ')} blocked: ${path.join(out, 'blocked-requests.json')}` : 'Host blocking off (PW_ALLOW_HOSTS=*).');
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('SMOKE ERROR', e.message.split('\n')[0]); process.exit(1); });
