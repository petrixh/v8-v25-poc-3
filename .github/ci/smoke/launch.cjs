// CI copy of tools/playwright/launch.cjs from the migration workspace (commit de0920c); unchanged below this line. See .github/CI.md.
// Shared Playwright launcher for all browser-driven agents.
// Vaadin 7/8 pick client bundles from the user agent; the default "HeadlessChrome" UA gets the
// ES5 bundle and breaks Polymer add-ons (migration-playbook/quirks/vaadin8.md). Always launch through here.
// Host allow-list: launch({ allowHosts }) and/or env PW_ALLOW_HOSTS (comma-separated) list the hosts the browser may
// reach; every other request is aborted and recorded in `blocked` (README.md "Blocking outside requests").
const path = require('path');
const http = require('http');
// Playwright: PW (path or module name) if set, else a local install, else the global npm install.
function loadPlaywright() {
  if (process.env.PW) return require(process.env.PW);
  try { return require('playwright'); } catch (e) { if (e.code !== 'MODULE_NOT_FOUND') throw e; }
  // Global install: ask npm where global modules live instead of assuming a fixed path.
  const root = require('child_process').execSync('npm root -g', { encoding: 'utf8' }).trim();
  return require(path.join(root, 'playwright'));
}
const pw = loadPlaywright();

const portOf = (u) => u.port || (/^(https|wss):$/.test(u.protocol) ? '443' : '80');

// Allowed hosts from the option (array or comma-separated string) plus PW_ALLOW_HOSTS. An entry is a host
// ("localhost", any port), host:port ("127.0.0.1:8080") or a URL (its host and port, the scheme's default port if it
// names none). Returns null when blocking is off: nothing listed, or any entry is "*".
function allowList(allowHosts) {
  const split = (v) => (Array.isArray(v) ? v : String(v || '').split(',')).map(s => String(s).trim()).filter(Boolean);
  const entries = [...split(allowHosts), ...split(process.env.PW_ALLOW_HOSTS)];
  if (!entries.length || entries.includes('*')) return null;
  return entries.map(e => {
    let u;
    const isUrl = /^[a-z][a-z0-9+.-]*:\/\//i.test(e);
    try { u = new URL(isUrl ? e : 'http://' + e); } catch (err) { throw new Error(`allowHosts: not a host, host:port or URL: ${e}`); }
    return { hostname: u.hostname, port: isUrl ? portOf(u) : u.port };
  });
}
const NETWORK = /^(https?|wss?):$/;
const allowed = (list, url) => {
  let u; try { u = new URL(url); } catch (e) { return true; }
  if (!NETWORK.test(u.protocol)) return true; // data:, blob:, about: never reach the network
  return list.some(a => a.hostname === u.hostname && (!a.port || a.port === portOf(u)));
};

// Backstop for what context routing cannot see: redirect targets (Playwright routes only the first URL of a
// redirect chain) and service worker fetches. Non-allowed hosts are sent to this local proxy, which records the
// request and refuses it. The browser does no DNS lookup for proxied hosts, and the proxy opens no connection.
function startRefusingProxy(record) {
  const srv = http.createServer((req, res) => { record(req.url); res.writeHead(403, { 'content-type': 'text/plain' }); res.end('Blocked: host not allowed'); });
  srv.on('connect', (req, sock) => { sock.on('error', () => {}); record('connect://' + req.url); sock.end('HTTP/1.1 403 Forbidden\r\n\r\n'); });
  srv.on('clientError', (e, sock) => sock.destroy());
  return new Promise((resolve, reject) => {
    srv.once('error', reject);
    srv.listen(0, '127.0.0.1', () => { srv.unref(); resolve(srv); });
  });
}

async function launch({ browser = process.env.PW_BROWSER || 'chromium', viewport = { width: 1400, height: 900 }, allowHosts } = {}) {
  const list = allowList(allowHosts);
  // Every aborted or refused request, in order: { time, url, resourceType, pageUrl, by: "route" | "proxy" }.
  const blocked = [];
  // The proxy starts first, so a failure leaves no browser running.
  const srv = list && await startRefusingProxy((url) => blocked.push({ time: new Date().toISOString(), url: url.slice(0, 1000), resourceType: null, pageUrl: null, by: 'proxy' }));
  let b;
  try { b = await pw[browser].launch(); } catch (e) { if (srv) srv.close(); throw e; }
  let userAgent;
  if (browser === 'chromium') {
    const probe = await b.newPage();
    userAgent = (await probe.evaluate(() => navigator.userAgent)).replace('HeadlessChrome', 'Chrome');
    await probe.close();
  }
  let proxy;
  if (list) {
    b.on('disconnected', () => srv.close());
    // Allowed hosts bypass the proxy. "<-loopback>" (first: Chromium ignores rules before it) stops Chromium's
    // implicit loopback bypass, so other local ports (another app copy) are refused too. Firefox: untested.
    const bypass = (browser === 'chromium' ? ['<-loopback>'] : []).concat(list.map(a => a.port ? `${a.hostname}:${a.port}` : a.hostname));
    proxy = { server: `http://127.0.0.1:${srv.address().port}`, bypass: bypass.join(',') };
  }
  // newContext() gives a fresh session (cookies, storage) with the same UA; use one per test user.
  // With an allow-list, the context (popups and _blank targets included) aborts requests to other hosts.
  const newContext = async () => {
    const c = await b.newContext({ viewport, userAgent, ...(proxy ? { proxy } : {}) });
    if (list) {
      await c.route((url) => !allowed(list, url.href), (route) => {
        const r = route.request();
        let pageUrl = null;
        try { pageUrl = r.frame().page().url(); } catch (e) { /* service worker request: no page */ }
        blocked.push({ time: new Date().toISOString(), url: r.url().slice(0, 1000), resourceType: r.resourceType(), pageUrl, by: 'route' });
        return route.abort('blockedbyclient');
      });
    }
    return c;
  };
  // isBlockedUrl(url): true when this launcher blocks the URL (aborted, or refused by the proxy with a 403 or a failed
  // tunnel). Runners use it to keep such responses and console errors out of their failures; they are in `blocked`.
  const isBlockedUrl = (url) => !!list && !!url && !allowed(list, url);
  return { browser: b, newContext, context: await newContext(), blocked, isBlockedUrl, allowHosts: list && list.map(a => a.port ? `${a.hostname}:${a.port}` : a.hostname) };
}

// A console error that only reports a request this launcher aborted (Chromium logs one per blocked subresource).
const isBlockedNote = (text) => /net::ERR_BLOCKED_BY_CLIENT/.test(text);

module.exports = { launch, pw, path, isBlockedNote };
