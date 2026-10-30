#!/usr/bin/env node
// Builds the Vaadin 25 migration hand-off document: handoff.md -> handoff.html (self-contained, system fonts,
// print CSS) -> handoff.pdf (A4, headless Chromium). All three files are in this folder; run from anywhere.
//
// Needs Node.js 20+ and four npm packages. Install them once, in a folder outside the repository:
//
//   npm install --prefix /tmp/handoff-docs marked@18.1.0 pdf-lib@1.17.1 pdfjs-dist@5.7.284 playwright@1.63.0
//   /tmp/handoff-docs/node_modules/.bin/playwright install chromium
//   DOCS_DEPS=/tmp/handoff-docs node docs/vaadin25-migration/build.cjs
//
// Optional: --png <dir> also writes one PNG per PDF page, to look at the result (needs @napi-rs/canvas@0.1 in DOCS_DEPS).
//
// The status line ("status:" in the front matter of handoff.md) is printed on the cover, in every page header and
// footer, and as a bar on top of the HTML page. The contents gets page numbers from a first print (named
// destinations, read with pdfjs). The cover is printed on its own and put in front with pdf-lib.
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');

const HERE = __dirname;
// npm packages: from DOCS_DEPS (a folder with node_modules), else the usual Node.js lookup, else the global install.
function resolveDep(spec) {
  const paths = [process.env.DOCS_DEPS, HERE].filter(Boolean);
  try { return require.resolve(spec, { paths }); } catch (e) { if (e.code !== 'MODULE_NOT_FOUND') throw e; }
  const root = require('child_process').execSync('npm root -g', { encoding: 'utf8' }).trim();
  return require.resolve(spec, { paths: [path.dirname(root)] });
}
const req = (m) => require(resolveDep(m));
const { Marked } = req('marked');
const { PDFDocument } = req('pdf-lib');
const { chromium } = req('playwright');

const SRC = path.join(HERE, 'handoff.md');
const OUT_HTML = path.join(HERE, 'handoff.html');
const OUT_PDF = path.join(HERE, 'handoff.pdf');
const pngArg = process.argv.indexOf('--png');
const PNG_DIR = pngArg > 0 ? path.resolve(process.argv[pngArg + 1]) : null;

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const stripTags = (s) => s.replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'");

function frontMatter(text) {
  const m = /^---\r?\n([\s\S]*?)\r?\n---\r?\n/.exec(text);
  if (!m) return { meta: {}, body: text };
  const meta = {};
  for (const line of m[1].split(/\r?\n/)) {
    const kv = /^([A-Za-z][\w-]*):\s*(.*)$/.exec(line);
    if (kv) meta[kv[1].toLowerCase()] = kv[2].trim();
  }
  return { meta, body: text.slice(m[0].length) };
}

// Heading rules: level 1 and 2 numbered, " {-}" unnumbered, "# Appendix A. Title".
function markdownToHtml(md) {
  const headings = [];
  const used = new Set();
  let h1 = 0, h2 = 0, prefix = '';
  const slug = (t) => {
    const base = stripTags(t).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'section';
    let id = base, n = 2;
    while (used.has(id)) id = `${base}-${n++}`;
    used.add(id);
    return id;
  };
  const marked = new Marked({
    gfm: true,
    renderer: {
      heading(token) {
        let html = this.parser.parseInline(token.tokens);
        let numbered = token.depth <= 2;
        if (/\s*\{-\}\s*$/.test(html)) { html = html.replace(/\s*\{-\}\s*$/, ''); numbered = false; }
        let num = '';
        if (token.depth === 1) {
          h2 = 0;
          const app = /^Appendix ([A-Z])[.:]?\s+(.*)$/.exec(html);
          if (app) { prefix = app[1]; num = app[1] + '.'; html = 'Appendix ' + app[1] + '. ' + app[2]; num = ''; }
          else if (numbered) { prefix = String(++h1); num = prefix; }
          else prefix = '';
        } else if (token.depth === 2 && numbered && prefix) {
          num = `${prefix}.${++h2}`;
        }
        const id = slug(html);
        if (token.depth === 1 || (token.depth === 2 && num)) headings.push({ level: token.depth, id, num, html });
        return `<h${token.depth} id="${id}">${num ? `<span class="num">${num}</span>` : ''}${html}</h${token.depth}>\n`;
      },
      paragraph(token) {
        if (token.text.trim() === '[[TOC]]') return '<!--TOC-->\n';
        return `<p>${this.parser.parseInline(token.tokens || [])}</p>\n`;
      },
    },
  });
  let html = marked.parse(md);
  html = html.replace(/<table>/g, '<div class="table-wrap"><table>').replace(/<\/table>/g, '</table></div>');
  html = html.replace(/\[TO CONFIRM:([^\]]*)\]/g, '<span class="tbc">[TO CONFIRM:$1]</span>');
  return { html, headings };
}

function tocHtml(headings, pages) {
  const items = headings.map((h) => {
    const page = pages ? (pages[h.id] ?? '') : '00';
    return `<li class="l${h.level}"><a href="#${h.id}"><span class="t-num">${h.num}</span><span class="t-title">${h.html}</span><span class="t-dots"></span><span class="t-page">${page}</span></a></li>`;
  });
  return `<nav class="toc"><h1 id="contents">Contents</h1><ol>\n${items.join('\n')}\n</ol></nav>`;
}

function coverHtml(meta) {
  const rows = [['Version', meta.version], ['Date', meta.date], ['Status', meta.status], ['Prepared for', meta.audience], ['Prepared by', meta.author]]
    .filter(([, v]) => v).map(([k, v]) => `<tr><td>${esc(k)}</td><td>${esc(v)}</td></tr>`).join('');
  return `<section class="cover">
  <div class="band">
    ${meta.kicker ? `<div class="kicker">${esc(meta.kicker)}</div>` : ''}
    <div class="title">${esc(meta.title)}</div>
    ${meta.subtitle ? `<div class="subtitle">${esc(meta.subtitle)}</div>` : ''}
    <div class="status">${esc(meta.status)}</div>
  </div>
  <div class="meta"><table>${rows}</table></div>
  ${meta.notice ? `<div class="foot">${esc(meta.notice)}</div>` : ''}
</section>`;
}

// style.css (system fonts only, so the HTML needs no font files), plus the status marks.
function css(forPdf, status) {
  let c = fs.readFileSync(path.join(HERE, 'style.css'), 'utf8');
  if (/url\(/.test(c)) throw new Error('style: the HTML must stay self-contained (no url())');
  c += `
/* Status marks. Screen: a bar at the top. Print: the PDF build draws the status and page numbers in the page margin
   (Chromium header and footer); a browser printing handoff.html gets them from the @page margin boxes below. */
.status-bar { position: sticky; top: 0; z-index: 10; background: #7a5300; color: #fff; text-align: center; font-weight: 600; letter-spacing: 0.06em; padding: 1.5mm; font-size: 9pt; }
@media screen {
  body { max-width: 190mm; margin: 0 auto 20mm; padding: 0 8mm; }
  .cover { width: auto; height: auto; margin: 0 -8mm 10mm; }
  .cover .band { padding: 30mm 22mm 14mm; }
  h1 { margin-top: 14mm; }
  h1, h2, h3 { scroll-margin-top: 10mm; }
  .cover { width: auto; margin: 0 0 10mm; }
}
@media print { .status-bar { display: none; } }
`;
  if (!forPdf) c += `
@page { @top-right { content: "${status}"; font-size: 7.5pt; font-weight: bold; color: #7a5300; } @bottom-right { content: "Page " counter(page); font-size: 7.5pt; color: #5b6573; } }
@page cover { @top-right { content: none; } @bottom-right { content: none; } }
`;
  c += `
.cover .status { background: #fff; color: #7a5300; font-weight: 700; }
`;
  return c;
}

function headerFooter(meta) {
  const base = "font-family:'Liberation Sans',Arial,sans-serif;font-size:7.5pt;color:#5b6573;width:100%;margin:0 18mm;display:flex;justify-content:space-between;";
  return {
    header: `<div style="${base}border-bottom:0.5pt solid #d5dae1;padding-bottom:1.5mm;"><span>${esc(meta.header)}</span><span style="color:#7a5300;font-weight:bold;letter-spacing:0.06em;">${esc(meta.status)}</span></div>`,
    footer: `<div style="${base}"><span>Version ${esc(meta.version)} · ${esc(meta.date)} · ${esc(meta.status)}</span><span>Page <span class="pageNumber"></span> of <span class="totalPages"></span></span></div>`,
  };
}

async function pdfjs() {
  return import(pathToFileURL(resolveDep('pdfjs-dist/legacy/build/pdf.mjs')).href);
}

async function headingPages(buf, ids) {
  const lib = await pdfjs();
  const doc = await lib.getDocument({ data: new Uint8Array(buf), verbosity: 0 }).promise;
  const dests = await doc.getDestinations();
  const pages = {};
  for (const id of ids) if (dests[id] && dests[id][0]) pages[id] = (await doc.getPageIndex(dests[id][0])) + 1;
  await doc.destroy();
  return pages;
}

async function renderPngs(pdfPath, dir) {
  const lib = await pdfjs();
  const { createCanvas } = req('@napi-rs/canvas');
  fs.mkdirSync(dir, { recursive: true });
  const doc = await lib.getDocument({ data: new Uint8Array(fs.readFileSync(pdfPath)), verbosity: 0 }).promise;
  for (let i = 1; i <= doc.numPages; i++) {
    const p = await doc.getPage(i);
    const vp = p.getViewport({ scale: 110 / 72 });
    const canvas = createCanvas(Math.ceil(vp.width), Math.ceil(vp.height));
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    await p.render({ canvasContext: ctx, canvas, viewport: vp }).promise;
    fs.writeFileSync(path.join(dir, `page-${String(i).padStart(2, '0')}.png`), canvas.toBuffer('image/png'));
  }
  const n = doc.numPages;
  await doc.destroy();
  return n;
}

async function main() {
  const { meta, body } = frontMatter(fs.readFileSync(SRC, 'utf8'));
  for (const k of ['title', 'status', 'header', 'version', 'date']) if (!meta[k]) throw new Error(`front matter: ${k} missing`);
  const { html: content, headings } = markdownToHtml(body);
  const style = css(true, meta.status);
  const htmlStyle = css(false, meta.status);
  const doc = (pages, pdf) => `<!doctype html><html lang="en"${pdf ? ' class="pdf"' : ''}><head><meta charset="utf-8">`
    + `<meta name="viewport" content="width=device-width, initial-scale=1"><title>${esc(meta.title)} (${esc(meta.status)})</title>`
    + `<style>${pdf ? style : htmlStyle}</style></head><body>`
    + `<div class="status-bar">${esc(meta.status)}</div>`
    + (pdf ? '' : coverHtml(meta))
    + content.replace('<!--TOC-->', tocHtml(headings, pages)) + '</body></html>';

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'handoff-'));
  const bodyFile = path.join(tmp, 'body.html');
  const coverFile = path.join(tmp, 'cover.html');
  fs.writeFileSync(coverFile, `<!doctype html><html lang="en" class="pdf"><head><meta charset="utf-8"><style>${style}</style></head><body>${coverHtml(meta)}</body></html>`);
  const browser = await chromium.launch();
  try {
    const tab = await browser.newPage();
    const { header, footer } = headerFooter(meta);
    const print = async (html) => {
      fs.writeFileSync(bodyFile, html);
      await tab.goto(pathToFileURL(bodyFile).href, { waitUntil: 'load' });
      return tab.pdf({ format: 'A4', printBackground: true, preferCSSPageSize: true, displayHeaderFooter: true, headerTemplate: header, footerTemplate: footer, outline: true, tagged: true });
    };
    let pages = null, pdf = null;
    for (let pass = 1; pass <= 4; pass++) {
      pdf = await print(doc(pages, true));
      const found = await headingPages(pdf, headings.map((h) => h.id));
      const stable = pages && headings.every((h) => found[h.id] === pages[h.id]);
      pages = found;
      if (stable) break;
    }
    const missing = headings.filter((h) => !pages[h.id]);
    if (missing.length) console.warn(`build: no page for ${missing.map((h) => h.id).join(', ')}`);
    // Page numbers in the contents count from the first page after the cover, as the footer does.
    await tab.goto(pathToFileURL(coverFile).href, { waitUntil: 'load' });
    const coverPdf = await tab.pdf({ format: 'A4', printBackground: true, preferCSSPageSize: true, pageRanges: '1' });
    const merged = await PDFDocument.create();
    for (const part of [coverPdf, pdf]) {
      const src = await PDFDocument.load(part);
      for (const p of await merged.copyPages(src, src.getPageIndices())) merged.addPage(p);
    }
    merged.setTitle(`${meta.title} (${meta.status})`);
    merged.setAuthor(meta.author || '');
    merged.setSubject(meta.subtitle || '');
    merged.setCreator('docs/vaadin25-migration/build.cjs');
    fs.writeFileSync(OUT_PDF, await merged.save());
    fs.writeFileSync(OUT_HTML, doc(pages, false));
    console.log(`build: ${OUT_HTML}`);
    console.log(`build: ${OUT_PDF} (${merged.getPageCount()} pages: cover + ${merged.getPageCount() - 1})`);
  } finally {
    await browser.close();
    fs.rmSync(tmp, { recursive: true, force: true });
  }
  if (PNG_DIR) console.log(`build: ${await renderPngs(OUT_PDF, PNG_DIR)} page images in ${PNG_DIR}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
