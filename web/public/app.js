'use strict';

/* DriveDesk UI. The server holds every rule; this file only calls /api and draws the results. */

// ---------------------------------------------------------------- helpers
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const num = x => Number(x).toLocaleString('en-US');
const pct = (a, b) => b > 0 ? Math.round(a / b * 100) : 0;
const MS_DAY = 864e5;
const isoMs = iso => Date.parse(iso + 'T00:00:00Z');
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WKD = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const fmtUtc = (ms, { weekday = false } = {}) => {
  const d = new Date(ms);
  return `${weekday ? WKD[d.getUTCDay()] + ' ' : ''}${String(d.getUTCDate()).padStart(2, '0')} ${MON[d.getUTCMonth()]}`;
};
const fmtDate = iso => iso ? fmtUtc(isoMs(iso)) : '—';
const todayIso = () => new Date().toLocaleDateString('sv-SE');
const plural = (n, one, many = one + 's') => `${num(n)} ${n === 1 ? one : many}`;
const PRIORITY = { 1: 'Urgent', 2: 'Normal', 3: 'Lower' };
const CATS = ['FOOD', 'CLOTHING', 'MEDICINE', 'BOOK'];
const URGENT_DAYS = 3;

async function api(path, body) {
  const opts = body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
  const res = await fetch('/api' + path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.error || res.statusText);
    err.data = data;
    throw err;
  }
  return data;
}

const state = { meta: null, q: '', onQuery: null, flash: null, distCat: 'FOOD', invFilter: 'ALL' };
const view = $('#view');
const catLabel = id => (state.meta?.categories.find(c => c.id === id)?.label) || id;
const catDot = id => `<i class="dot c-${esc(id)}" aria-hidden="true"></i>`;
const catTag = id => `<span class="cat-tag c-${esc(id)}">${catDot(id)}${esc(catLabel(id))}</span>`;

const svgIco = d => `<svg class="ico" viewBox="0 0 16 16" aria-hidden="true">${d}</svg>`;
const ICON = {
  warn: svgIco('<path d="M8 2.3l6.3 11H1.7z"/><path d="M8 6.6v3.2M8 11.7v.1"/>'),
  clock: svgIco('<circle cx="8" cy="8" r="6"/><path d="M8 4.6V8l2.2 1.4"/>'),
  check: svgIco('<path d="M2.8 8.6l3.4 3.1L13.2 4.4"/>'),
  stop: svgIco('<circle cx="8" cy="8" r="6"/><path d="M4.2 11.8l7.6-7.6"/>'),
  pen: svgIco('<path d="M2.5 13.5l.7-3L11 2.7l2.3 2.3L5.5 12.8z"/>')
};

const notice = (kind, html) => `<div class="notice ${kind}" role="${kind === 'bad' ? 'alert' : 'status'}">${kind === 'bad' ? ICON.stop : kind === 'ok' ? ICON.check : ICON.warn}<div>${html}</div></div>`;
function takeFlash() {
  const f = state.flash;
  state.flash = null;
  return f ? notice(f.kind, f.html) : '';
}
const pageHead = (title, lede = '', actions = '') =>
  `<div class="page-head"><div><h1>${esc(title)}</h1>${lede ? `<p class="lede">${esc(lede)}</p>` : ''}</div><div class="actions">${actions}</div></div>`;
const panelHead = (title, note = '', right = '') =>
  `<div class="panel-h"><h2>${esc(title)}</h2>${right ? `<div class="r">${right}</div>` : note ? `<span class="note">${esc(note)}</span>` : ''}</div>`;

// DOM builder: values go in as text nodes, never as markup
function h(tag, cls, ...kids) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  for (const k of kids.flat()) if (k !== null && k !== undefined && k !== false) e.append(k);
  return e;
}

// ---------------------------------------------------------------- tooltip (one shared element)
const tipEl = $('#tip');
function tipShow(node, { x, y, above = false }) {
  tipEl.replaceChildren(node);
  tipEl.hidden = false;
  const w = tipEl.offsetWidth, hh = tipEl.offsetHeight, pad = 8;
  let left, top;
  if (above) {
    left = Math.min(Math.max(pad, x - w / 2), innerWidth - w - pad);
    top = y - hh - 10;
    if (top < pad) top = y + 28;
  } else {
    left = x + 16;
    if (left + w > innerWidth - pad) left = x - w - 16;
    left = Math.max(pad, left);
    top = Math.min(Math.max(pad, y), innerHeight - hh - pad);
  }
  tipEl.style.left = left + 'px';
  tipEl.style.top = top + 'px';
}
const tipHide = () => { tipEl.hidden = true; };

// every mark carries its own tooltip: hover and keyboard focus show the same thing
function bindTip(root, sel, build) {
  const show = el => {
    const node = build(el);
    if (!node) return;
    const r = el.getBoundingClientRect();
    tipShow(node, { x: r.left + r.width / 2, y: r.top, above: true });
  };
  root.addEventListener('pointerover', e => { const el = e.target.closest(sel); if (el) show(el); });
  root.addEventListener('pointerout', e => { const el = e.target.closest(sel); if (el && !el.contains(e.relatedTarget)) tipHide(); });
  root.addEventListener('focusin', e => { const el = e.target.closest(sel); if (el) show(el); });
  root.addEventListener('focusout', tipHide);
}

const tipLines = (head, ...rows) => h('div', '', h('div', 't-h', head), ...rows);
const tipRow = (cat, label, value) => h('div', 't-row' + (cat ? ' c-' + cat : ''), h('span', '', cat ? h('i') : null, label), h('b', '', value));

// ---------------------------------------------------------------- chart forms
const observers = [];
function disposeCharts() { observers.splice(0).forEach(o => o.disconnect()); tipHide(); }

function niceCap(v) {
  const steps = [2, 4, 6, 8, 10];
  for (let mag = 1; ; mag *= 10) for (const s of steps) if (s * mag >= v) return s * mag;
}

/** Whole-drive daily flow. Two panels share the day axis but not the scale (received and handed-out differ by an order of magnitude), so there is never a second y-axis. */
function buildFlow(drive, items, dists) {
  const n = drive.totalDays, start = isoMs(drive.startDate);
  const idx = iso => Math.round((isoMs(iso) - start) / MS_DAY);
  const zero = () => Object.fromEntries(CATS.map(c => [c, Array(n).fill(0)]));
  const inn = zero(), out = zero();
  items.forEach(i => { const k = idx(i.receivedOn); if (k >= 0 && k < n && inn[i.category]) inn[i.category][k] += i.initialQuantity; });
  dists.forEach(d => { if (d.status === 'BLOCKED') return; const k = idx(d.date); if (k >= 0 && k < n && out[d.category]) out[d.category][k] += d.quantity; });
  const tot = m => Array.from({ length: n }, (_, k) => CATS.reduce((a, c) => a + m[c][k], 0));
  return {
    n, inn, out, inTot: tot(inn), outTot: tot(out), today: Math.min(n - 1, Math.max(0, drive.day - 1)), ended: !!drive.ended,
    dates: Array.from({ length: n }, (_, k) => start + k * MS_DAY)
  };
}

function flowSvg(f, W) {
  const L = 40, R = 2, plotW = Math.max(160, W - L - R), slot = plotW / f.n;
  const bw = Math.max(3, Math.min(18, slot - 5)), H = 76, t1 = 36, t2 = t1 + H + 52;
  const g = { W, L, plotW, slot, bw, H, t1, b1: t1 + H, t2, b2: t2 + H, total: t2 + H + 38 };
  const sum = a => a.reduce((x, y) => x + y, 0);

  const panel = (data, tot, top, title) => {
    const cap = niceCap(Math.max(...tot, 1)), base = top + H;
    let s = `<text class="ptitle" x="0" y="${top - 20}">${title}<tspan dx="8">${num(sum(tot))} units</tspan></text>`;
    [0.5, 1].forEach(fr => {
      const y = base - H * fr;
      s += `<line class="grid-l" x1="${L}" x2="${L + plotW}" y1="${y}" y2="${y}"/><text class="axis-t end" x="${L - 8}" y="${y + 3.5}">${num(cap * fr)}</text>`;
    });
    s += `<text class="axis-t end" x="${L - 8}" y="${base + 3.5}">0</text>`;
    const peak = tot.indexOf(Math.max(...tot));
    tot.forEach((total, k) => {
      if (!total) return;
      const x = L + k * slot + (slot - bw) / 2;
      let acc = 0;
      CATS.forEach(c => {
        const v = data[c][k];
        if (!v) return;
        const y0 = base - acc / cap * H, hh = v / cap * H;
        const first = acc === 0;
        acc += v;
        const top2 = y0 - hh, ht = Math.max(1.5, hh - (first ? 0 : 2));
        if (acc === total) {
          const r = Math.min(4, bw / 2, ht);
          s += `<path class="mk c-${c}" d="M${x},${top2 + ht}V${top2 + r}A${r},${r} 0 0 1 ${x + r},${top2}H${x + bw - r}A${r},${r} 0 0 1 ${x + bw},${top2 + r}V${top2 + ht}Z"/>`;
        } else {
          s += `<rect class="mk c-${c}" x="${x}" y="${top2}" width="${bw}" height="${ht}"/>`;
        }
      });
    });
    if (tot[peak] > 0) s += `<text class="peak" x="${L + (peak + .5) * slot}" y="${base - tot[peak] / cap * H - 6}">${num(tot[peak])}</text>`;
    return s + `<line class="base-l" x1="${L}" x2="${L + plotW}" y1="${base}" y2="${base}"/>`;
  };

  let s = `<svg viewBox="0 0 ${W} ${g.total}" width="${W}" height="${g.total}" tabindex="0" role="group" aria-label="Daily units received and handed out across the drive. Use the left and right arrow keys to read each day.">`;
  const tx = L + (f.today + .5) * slot;
  if (f.today + 1 < f.n) {
    const fx = L + (f.today + 1) * slot, fw = L + plotW - fx;
    s += `<rect class="future" x="${fx}" y="${t1 - 10}" width="${fw}" height="${g.b2 - t1 + 10}"/>`;
    if (fw > 90) s += `<text class="togo" x="${fx + 10}" y="${t1 + H / 4 + 4}">${f.n - f.today - 1} days to go</text>`;
  }
  s += `<rect class="hl" x="0" y="${t1 - 10}" width="${slot}" height="${g.b2 - t1 + 10}" hidden/>`;
  s += panel(f.inn, f.inTot, t1, 'Received') + panel(f.out, f.outTot, t2, 'Handed out');
  const every = slot * 5 >= 64 ? 5 : 10;
  for (let k = 0; k < f.n; k++) {
    if (k % every && k !== f.n - 1) continue;
    if (k !== f.n - 1 && f.n - 1 - k < every / 2) continue;
    s += `<text class="axis-t mid" x="${L + (k + .5) * slot}" y="${g.b2 + 17}">${fmtUtc(f.dates[k])}</text>`;
  }
  if (!f.ended) s += `<line class="today-l" x1="${tx}" x2="${tx}" y1="${t1 - 10}" y2="${g.b2 + 4}"/><text class="today-t" x="${tx}" y="${g.b2 + 33}">Today</text>`;
  s += '</svg>';
  return { html: s, g };
}

function flowDayTip(f, k) {
  const head = `${fmtUtc(f.dates[k], { weekday: true })} · day ${k + 1}`;
  if (k > f.today) return tipLines(head, h('div', 't-note', 'Not here yet.'));
  const block = (label, data, tot) => [
    h('div', 't-sec', h('span', '', label), h('b', '', num(tot[k]))),
    ...CATS.filter(c => data[c][k]).map(c => tipRow(c, catLabel(c), num(data[c][k])))
  ];
  return tipLines(head, ...block('Received', f.inn, f.inTot), ...block('Handed out', f.out, f.outTot));
}

function flowTable(f) {
  const rows = [];
  for (let k = 0; k < f.n; k++) {
    if (!f.inTot[k] && !f.outTot[k]) continue;
    const part = (d) => CATS.filter(c => d[c][k]).map(c => `${esc(catLabel(c))} ${num(d[c][k])}`).join(', ');
    rows.push(`<tr><td class="m">${fmtUtc(f.dates[k], { weekday: true })}</td>
      <td class="m num-col">${num(f.inTot[k])}</td><td class="m num-col">${num(f.outTot[k])}</td>
      <td class="wrap">${f.inTot[k] ? 'In: ' + part(f.inn) : ''}${f.inTot[k] && f.outTot[k] ? '<br>' : ''}${f.outTot[k] ? 'Out: ' + part(f.out) : ''}</td></tr>`);
  }
  const sum = a => a.reduce((x, y) => x + y, 0);
  return `<div class="scroll-x"><table class="tv"><thead><tr><th>Day</th><th class="num-col">Received</th><th class="num-col">Handed out</th><th>Detail</th></tr></thead><tbody>${rows.join('') || '<tr><td colspan="4">No activity yet.</td></tr>'}
    <tr><td class="m"><b>Total</b></td><td class="m num-col"><b>${num(sum(f.inTot))}</b></td><td class="m num-col"><b>${num(sum(f.outTot))}</b></td><td></td></tr></tbody></table></div>`;
}

function mountFlow(host, f, toggle) {
  let mode = 'chart', lastW = 0, geo = null, svg = null, cur = f.today;
  const hide = () => { tipHide(); const hl = $('.hl', host); if (hl) hl.setAttribute('hidden', ''); };
  const focusDay = k => {
    if (!svg || k < 0 || k >= f.n) return hide();
    cur = k;
    const hl = $('.hl', svg);
    hl.setAttribute('x', geo.L + k * geo.slot);
    hl.removeAttribute('hidden');
    const r = svg.getBoundingClientRect();
    tipShow(flowDayTip(f, k), { x: r.left + geo.L + (k + .5) * geo.slot, y: r.top + geo.t1 });
  };
  const draw = () => {
    if (mode === 'table') { host.innerHTML = flowTable(f); return; }
    lastW = Math.floor(host.clientWidth);
    if (!lastW) return;
    const out = flowSvg(f, lastW);
    host.innerHTML = out.html;
    geo = out.g; svg = host.firstElementChild;
    svg.addEventListener('pointermove', e => {
      const r = svg.getBoundingClientRect();
      const k = Math.floor((e.clientX - r.left - geo.L) / geo.slot);
      if (k < 0 || k >= f.n) hide(); else focusDay(k);
    });
    svg.addEventListener('pointerleave', hide);
    svg.addEventListener('focus', () => focusDay(cur));
    svg.addEventListener('blur', hide);
    svg.addEventListener('keydown', e => {
      const step = { ArrowLeft: -1, ArrowRight: 1 }[e.key];
      if (step) { e.preventDefault(); focusDay(Math.min(f.n - 1, Math.max(0, cur + step))); }
      else if (e.key === 'Home') { e.preventDefault(); focusDay(0); }
      else if (e.key === 'End') { e.preventDefault(); focusDay(f.today); }
      else if (e.key === 'Escape') hide();
    });
  };
  const ro = new ResizeObserver(() => { if (mode === 'chart' && Math.floor(host.clientWidth) !== lastW) draw(); });
  ro.observe(host);
  observers.push(ro);
  toggle.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    mode = b.dataset.mode;
    $$('button', toggle).forEach(x => x.setAttribute('aria-pressed', String(x === b)));
    tipHide();
    draw();
  });
  draw();
}

function stockBy(items) {
  const by = Object.fromEntries(CATS.map(c => [c, 0]));
  items.forEach(i => { if (i.usable && by[i.category] !== undefined) by[i.category] += i.quantity; });
  return { by, total: CATS.reduce((a, c) => a + by[c], 0) };
}

/** Nominal categories: one bar each, always that category's own colour. */
function catBars({ by, total }) {
  const max = Math.max(1, ...CATS.map(c => by[c]));
  return `<div class="cbars" role="list">${CATS.map(c => `<div role="listitem" class="c-${c}" data-c="${c}" tabindex="0" aria-label="${esc(catLabel(c))}: ${plural(by[c], 'unit')}, ${pct(by[c], total)}% of stock">
    <div class="cbar-top"><span>${catDot(c)}${esc(catLabel(c))}</span><span><b>${num(by[c])}</b><small>${pct(by[c], total)}%</small></span></div>
    <div class="cbar"><i style="width:${by[c] / max * 100}%"></i></div></div>`).join('')}</div>`;
}

function catStrip({ by, total }) {
  const bar = CATS.filter(c => by[c] > 0).map(c => `<i class="c-${c}" style="flex:${by[c]} 1 0" data-c="${c}" tabindex="0" aria-label="${esc(catLabel(c))}: ${plural(by[c], 'unit')}, ${pct(by[c], total)}% of stock"></i>`).join('');
  return `<div class="cstrip" role="group" aria-label="Units in stock by category">${bar}</div>
    <div class="cstrip-key">${CATS.map(c => `<span class="c-${c}">${catDot(c)}${esc(catLabel(c))}<b>${num(by[c])}</b></span>`).join('')}</div>`;
}

const stockTip = stock => el => {
  const c = el.dataset.c, v = stock.by[c];
  return tipLines(catLabel(c), tipRow(c, 'On the shelf', plural(v, 'unit')), h('div', 't-note', pct(v, stock.total) + '% of stock'));
};

function paceBar(n, withEdit = false) {
  const drive = state.meta.drive, pace = Math.min(1, drive.day / drive.totalDays);
  const frac = n.target > 0 ? n.collected / n.target : 0;
  const left = Math.max(1, drive.totalDays - drive.day);
  let status;
  if (n.met) status = `<span class="sev good">${ICON.check} Target met</span>`;
  else if (frac >= pace) status = `<span class="sev good">${ICON.check} On pace</span>`;
  else status = `<span class="sev warn">${ICON.warn} Behind pace</span>`;
  const togo = n.met ? '' : `<span>${num(n.remaining)} to go · ~${num(Math.ceil(n.remaining / left))}/day</span>`;
  return `<div class="need c-${esc(n.category)}">
    <div class="need-head"><span class="need-name">${catDot(n.category)}<b>${esc(n.itemName)}</b></span>
      <span class="need-nums">${num(n.collected)}<span> / ${num(n.target)}</span></span></div>
    <div class="pace" role="img" aria-label="${esc(n.itemName)}: ${pct(n.collected, n.target)}% of target collected; the drive is ${Math.round(pace * 100)}% through"><i class="fill" style="width:${Math.min(100, frac * 100)}%"></i><b class="tick" style="left:${pace * 100}%"></b></div>
    <div class="need-foot"><span class="l">${status}${togo}</span>${withEdit ? `<button class="btn small" type="button" data-edit="${esc(n.category)}|${esc(n.itemName)}|${n.target}" aria-label="Edit target for ${esc(n.itemName)}">${ICON.pen} Edit</button>` : ''}</div></div>`;
}

function paceKey() {
  const d = state.meta.drive;
  return `<div class="pace-key"><b></b> where the drive should be today: ${Math.round(d.day / d.totalDays * 100)}% of the way through</div>`;
}

function expiryChart(items, span = 7) {
  if (!items.length) return `<p class="empty">Nothing expires in the next ${span} days.</p>`;
  const rows = items.map((i, k) => {
    const d = Math.max(0, i.days), sev = i.days <= URGENT_DAYS ? 'crit' : 'warn', w = Math.min(100, d / span * 100);
    return `<div class="xp-row ${sev}" tabindex="0" data-k="${k}" aria-label="${esc(i.name)}, ${plural(i.quantity, 'unit')}, ${i.days === 0 ? 'expires today' : plural(i.days, 'day') + ' left'}">
      <span class="xp-name">${sev === 'crit' ? ICON.warn : ICON.clock}<span>${esc(i.name)}</span></span>
      <div class="xp-track"><i class="xp-bar" style="width:${w}%"></i><i class="xp-end" style="left:${w}%"></i></div>
      <span class="xp-val">${i.days === 0 ? 'today' : d + 'd'}</span></div>`;
  }).join('');
  const urgentAt = URGENT_DAYS / span * 100;
  return `<div class="xp-axis"><span></span><div class="xp-scale"><span style="left:0">today</span><span style="left:${urgentAt}%">+${URGENT_DAYS}d</span><span style="left:100%">+${span}d</span></div><span></span></div>${rows}
    <div class="xp-zones"><span class="sev crit">${ICON.warn} urgent: ${URGENT_DAYS} days or less</span><span class="sev warn">${ICON.clock} within ${span} days</span></div>`;
}

function heatmap(hm) {
  const max = Math.max(1, ...hm.rows.flatMap(r => r.values));
  const head = `<span></span>` + hm.days.map(d => `<span class="dlabel">${d.slice(8)}</span>`).join('') + `<span class="dlabel">Σ</span>`;
  let total = 0;
  const rows = hm.rows.map((r, ri) => {
    const sum = r.values.reduce((a, b) => a + b, 0);
    total += sum;
    return `<span class="rowlabel c-${esc(r.category)}">${catDot(r.category)}${esc(r.label)}</span>` + r.levels.map((l, i) =>
      `<button type="button" class="hc l${l}" data-r="${ri}" data-i="${i}" aria-label="${esc(r.label)}, ${fmtDate(hm.days[i])}: ${plural(r.values[i], 'unit')}"></button>`).join('') + `<span class="tot">${num(sum)}</span>`;
  }).join('');
  const tableRows = hm.rows.map(r => `<tr><td>${esc(r.label)}</td>${r.values.map(v => `<td class="m num-col">${v}</td>`).join('')}</tr>`).join('');
  return `<div class="scroll-x"><div class="heat" role="group" aria-label="Units handed out per category over the last 14 days: ${num(total)} in total">${head}${rows}</div></div>
    <div class="heat-key"><span>0</span>${[0, 1, 2, 3, 4].map(l => `<i class="hc l${l}"></i>`).join('')}<span>${max}</span><span>units / cell</span><span class="sum">${num(total)} units in 14 days</span></div>
    <details class="tv-details"><summary>Table view</summary><div class="scroll-x"><table class="tv"><thead><tr><th></th>${hm.days.map(d => `<th class="num-col">${d.slice(8)}</th>`).join('')}</tr></thead><tbody>${tableRows}</tbody></table></div></details>`;
}

function activityLog(entries) {
  if (!entries.length) return `<p class="empty">No activity yet.</p>`;
  return `<div class="log">${entries.map(e =>
    `<div class="log-line ${esc(e.type)}"><span class="when">${esc(e.when)}</span><span class="tag">${esc(e.type)}</span><span>${esc(e.text)}</span></div>`).join('')}</div>`;
}

function statusStamp(row) {
  return row.status === 'BLOCKED'
    ? `<span class="tag bad">${ICON.stop} Blocked · ${esc(row.note)}</span>`
    : `<span class="tag ok">${ICON.check} Handed over</span>`;
}

function distTable(rows) {
  if (!rows.length) return `<p class="empty">No distributions yet. <a href="#/distribution">Hand over the first items.</a></p>`;
  return `<div class="scroll-x"><table><thead><tr><th>Date</th><th>Family</th><th>Category</th><th>Item</th><th class="num-col">Qty</th><th>Status</th></tr></thead><tbody>${rows.map(r => `
    <tr><td class="m">${fmtDate(r.date)}</td>
    <td><span class="id">${esc(r.beneficiaryId)}</span>${esc(r.beneficiaryName)}</td>
    <td>${catTag(r.category)}</td><td>${esc(r.itemName || '—')}</td>
    <td class="m num-col">${r.quantity}</td><td>${statusStamp(r)}</td></tr>`).join('')}</tbody></table></div>`;
}

function expiryStamp(i) {
  if (i.expiryStatus === 'none') return `<span class="muted">—</span>`;
  const d = i.daysToExpiry;
  if (i.expiryStatus === 'expired') return `<span class="tag bad">${ICON.stop} Expired ${Math.abs(d)}d ago</span>`;
  if (i.expiryStatus === 'urgent') return `<span class="tag bad">${ICON.warn} ${d}d left</span>`;
  if (i.expiryStatus === 'near') return `<span class="tag warn">${ICON.clock} ${d}d left</span>`;
  return `<span class="mono muted">${fmtDate(i.expiryDate)}</span>`;
}

// ---------------------------------------------------------------- views
const routes = {};

routes.overview = {
  title: 'Overview',
  async render() {
    const [d, items, dists] = await Promise.all([api('/overview'), api('/inventory'), api('/distributions')]);
    const s = d.stats, drive = d.drive;
    const stock = stockBy(items);
    const flow = buildFlow(drive, items, dists);
    const sorted = [...d.needs].sort((a, b) => (a.collected / a.target) - (b.collected / b.target));
    const soon = s.soonestDays === null ? `nothing within ${s.nearExpiryDays} days`
      : `soonest in ${s.soonestDays}d${s.urgentCount ? ' · ' + s.urgentCount + ' urgent' : ''}`;
    const lede = drive.ended ? 'This drive has ended. Close it from the Drive tab to start the next one.'
      : drive.daysLeft > 0 ? `${plural(drive.daysLeft, 'day')} left in the drive.` : 'Last day of the drive.';

    view.innerHTML = takeFlash() + pageHead('Overview', lede,
      `${drive.ended ? '<a class="btn" href="#/drive">Start next drive</a>' : ''}<a class="btn" href="#/distribution">Distribute</a><a class="btn primary" href="#/log">+ Log donation</a>`) + `
      <div class="grid"><section class="panel flush s12" aria-label="Headline numbers"><div class="kpis">
        <div class="kpi"><div class="kpi-l">Units in stock</div><div class="kpi-v">${num(s.unitsInStock)}</div><div class="kpi-s">${plural(s.lots, 'usable lot')}</div></div>
        <div class="kpi"><div class="kpi-l">Families served</div><div class="kpi-v">${s.familiesServed}<small>/ ${s.familiesTotal}</small></div>
          <div class="meter"><i style="width:${pct(s.familiesServed, s.familiesTotal)}%"></i></div></div>
        <div class="kpi"><div class="kpi-l">Targets met</div><div class="kpi-v">${s.targetsMet}<small>/ ${s.targetsTotal}</small></div>
          <div class="meter"><i style="width:${pct(s.targetsMet, s.targetsTotal)}%"></i></div></div>
        <div class="kpi"><div class="kpi-l">Expiring within ${s.nearExpiryDays} days</div><div class="kpi-v">${s.expiringCount}<small>${s.expiringCount === 1 ? 'lot' : 'lots'}</small></div>
          <div class="kpi-s">${esc(soon)}${s.expiredCount ? ` · <span class="sev crit">${s.expiredCount} expired</span>` : ''}</div></div>
      </div></section></div>

      <div class="grid"><section class="panel s12">
        ${panelHead('Flow of goods', '', `<div class="legend" aria-label="Categories">${CATS.map(c => `<span class="c-${c}">${catDot(c)}${esc(catLabel(c))}</span>`).join('')}</div>
          <div class="seg" id="flow-mode" role="group" aria-label="Flow of goods view"><button type="button" data-mode="chart" aria-pressed="true">Chart</button><button type="button" data-mode="table" aria-pressed="false">Table</button></div>`)}
        <div class="chart-host" id="flow"></div>
      </section></div>

      <div class="grid">
        <section class="panel s5">${panelHead('Targets', 'collected / target')}
          ${d.needs.length ? paceKey() + sorted.map(n => paceBar(n)).join('') : `<p class="empty">No targets yet. <a href="#/needs">Set the first one.</a></p>`}</section>
        <div class="s7 stack">
          <section class="panel">${panelHead('Stock by category', `${num(stock.total)} units`)}<div id="stock">${catBars(stock)}</div></section>
          <section class="panel">${panelHead('Expiring soon', 'days left')}<div id="expiry">${expiryChart(d.expiry, s.nearExpiryDays)}</div></section>
        </div>
      </div>

      <div class="grid">
        <section class="panel s7">${panelHead('Distribution by day', 'units · last 14 days')}<div id="heat">${heatmap(d.heatmap)}</div></section>
        <section class="panel s5">${panelHead('Activity', 'latest')}${activityLog(d.activity.slice(0, 6))}</section>
      </div>

      <div class="grid"><section class="panel s12">${panelHead('Recent distributions')}${distTable(d.recent)}</section></div>`;

    mountFlow($('#flow'), flow, $('#flow-mode'));
    bindTip($('#stock'), '[data-c]', stockTip(stock));
    bindTip($('#expiry'), '.xp-row', el => {
      const i = d.expiry[+el.dataset.k];
      const when = fmtUtc(isoMs(todayIso()) + i.days * MS_DAY);
      return tipLines(i.name, tipRow(i.category, catLabel(i.category), plural(i.quantity, 'unit')),
        h('div', 't-note', i.days === 0 ? 'Expires today' : `Expires ${when}, in ${plural(i.days, 'day')}`));
    });
    bindTip($('#heat'), '.hc[data-r]', el => {
      const r = d.heatmap.rows[+el.dataset.r], i = +el.dataset.i;
      return tipLines(fmtDate(d.heatmap.days[i]), tipRow(r.category, r.label, plural(r.values[i], 'unit')));
    });
  }
};

const EXTRA_FIELDS = {
  FOOD: `<div class="row2"><div class="field"><label for="f-expiry">Expiry date</label><input id="f-expiry" name="expiryDate" type="date" required></div>
    <label class="check"><input type="checkbox" name="perishable"> Perishable</label></div>`,
  MEDICINE: `<div class="row2"><div class="field"><label for="f-expiry">Expiry date</label><input id="f-expiry" name="expiryDate" type="date" required></div>
    <label class="check"><input type="checkbox" name="sealed" checked> Sealed packaging</label></div>`,
  CLOTHING: `<div class="row2"><div class="field"><label for="f-size">Size</label><input id="f-size" name="size" type="text" placeholder="M, L, Free"></div>
    <div class="field"><label for="f-season">Season</label><select id="f-season" name="season"><option>Winter</option><option>Summer</option><option>Monsoon</option><option>All</option></select></div></div>
    <div class="field"><label for="f-cond">Condition</label>${conditionSelect('f-cond')}</div>`,
  BOOK: `<div class="row2"><div class="field"><label for="f-subject">Subject</label><input id="f-subject" name="subject" type="text" placeholder="Science"></div>
    <div class="field"><label for="f-grade">Grade level</label><input id="f-grade" name="gradeLevel" type="text" placeholder="8"></div></div>
    <div class="field"><label for="f-cond">Condition</label>${conditionSelect('f-cond')}</div>`
};
function conditionSelect(id) {
  return `<select id="${id}" name="condition"><option value="NEW">New</option><option value="GOOD" selected>Good</option><option value="WORN">Worn</option><option value="DAMAGED">Damaged (will be rejected)</option></select>`;
}
const CLASS_CAPTION = {
  FOOD: 'creates FoodItem · implements Expirable', MEDICINE: 'creates MedicineItem · implements Expirable',
  CLOTHING: 'creates ClothingItem', BOOK: 'creates BookItem'
};

routes.log = {
  title: 'Log donation',
  async render() {
    const [donors, items] = await Promise.all([api('/donors'), api('/inventory')]);
    const picker = state.meta.categories.map((c, k) =>
      `<label class="c-${esc(c.id)}"><input type="radio" name="category" value="${esc(c.id)}" ${k === 0 ? 'checked' : ''}><span>${esc(c.label)}</span></label>`).join('');
    const donorOpts = donors.map(d => `<option value="${d.id}">${esc(d.id)} · ${esc(d.name)}</option>`).join('');
    view.innerHTML = takeFlash() + pageHead('Log a donation', 'Choosing a category swaps in the fields that belong to that kind of item.') + `
      <div class="grid">
        <form class="panel form s7" id="donation-form" novalidate>
          <fieldset class="picker"><legend>Category</legend>${picker}</fieldset>
          <span class="hint" id="class-caption"></span>
          <div class="row2">
            <div class="field"><label for="f-name">Item name</label><input id="f-name" name="name" type="text" required placeholder="Rice 5kg"></div>
            <div class="field"><label for="f-qty">Quantity</label><input id="f-qty" name="quantity" type="number" min="1" step="1" required value="1"></div>
          </div>
          <div id="extra" class="form"></div>
          <div class="field"><label for="f-donor">Donor</label>
            <select id="f-donor" name="donorId">${donorOpts}<option value="__new__">+ New donor…</option></select></div>
          <div class="sub-form" id="new-donor" hidden>
            <div class="row2"><div class="field"><label for="d-name">Donor name</label><input id="d-name" type="text"></div>
              <div class="field"><label for="d-phone">Phone</label><input id="d-phone" type="tel"></div></div>
            <label class="check"><input id="d-org" type="checkbox"> Organisation</label>
          </div>
          <div id="form-msg" aria-live="polite"></div>
          <div><button class="btn primary" type="submit">+ Log donation</button></div>
        </form>
        <section class="panel s5">${panelHead('Recent donations', 'newest first')}
          ${items.length ? `<table><thead><tr><th>Item</th><th class="num-col">Qty</th></tr></thead><tbody>${items.slice(0, 8).map(i =>
            `<tr><td><span class="id">${esc(i.id)}</span>${esc(i.name)}<div class="sub">${esc(i.donorName)} · ${esc(i.details)}</div></td><td class="m num-col">${i.initialQuantity}</td></tr>`).join('')}</tbody></table>`
            : `<p class="empty">No donations yet. <b>Log the first one.</b></p>`}</section>
      </div>`;

    const form = $('#donation-form');
    const swap = () => {
      const cat = form.category.value;
      $('#extra').innerHTML = EXTRA_FIELDS[cat];
      $('#class-caption').textContent = CLASS_CAPTION[cat];
    };
    const toggleDonor = () => { $('#new-donor').hidden = form.donorId.value !== '__new__'; };
    form.addEventListener('change', ev => {
      if (ev.target.name === 'category') swap();
      if (ev.target.name === 'donorId') toggleDonor();
    });
    swap();
    if (!donors.length) { form.donorId.value = '__new__'; }
    toggleDonor();

    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      const msg = $('#form-msg');
      const fd = new FormData(form);
      const body = Object.fromEntries(fd.entries());
      body.perishable = fd.has('perishable');
      body.sealed = fd.has('sealed');
      body.quantity = Number(body.quantity);
      msg.innerHTML = '';
      try {
        if (body.donorId === '__new__') {
          const donor = await api('/donors', { name: $('#d-name').value, phone: $('#d-phone').value, organisation: $('#d-org').checked });
          body.donorId = donor.id;
        }
        const item = await api('/donations', body);
        state.flash = { kind: 'ok', html: `Logged <span class="mono">${esc(item.id)}</span> ${esc(item.name)} ×${item.quantity}. ${esc(item.details)}.` };
        await route();
      } catch (e) {
        msg.innerHTML = notice('bad', esc(e.message));
      }
    });
  }
};

routes.inventory = {
  title: 'Inventory',
  async render() {
    const items = await api('/inventory');
    const stock = stockBy(items);
    const isExpiring = i => i.expiryStatus === 'urgent' || i.expiryStatus === 'near';
    const filters = [['ALL', 'All', () => true], ...state.meta.categories.map(c => [c.id, c.label, i => i.category === c.id]),
      ['EXPIRING', 'Expiring soon', isExpiring], ['EXPIRED', 'Expired', i => i.expiryStatus === 'expired']];
    view.innerHTML = takeFlash() + pageHead('Inventory', 'Every lot received, and what is left of it.', `<a class="btn primary" href="#/log">+ Log donation</a>`) + `
      <div class="grid"><section class="panel s12">${panelHead('On the shelf now', `${num(stock.total)} usable units`)}<div id="stock">${catStrip(stock)}</div></section></div>
      <div class="grid"><section class="panel s12">
        <div class="filters" id="chips" role="group" aria-label="Filter lots">${filters.map(([id, label, fn]) =>
          `<button type="button" data-f="${id}" aria-pressed="${state.invFilter === id}">${esc(label)}<span class="n">${items.filter(fn).length}</span></button>`).join('')}</div>
        <div id="inv-table" class="scroll-x"></div></section></div>`;
    bindTip($('#stock'), '[data-c]', stockTip(stock));
    const draw = () => {
      const q = state.q.trim().toLowerCase();
      const active = filters.find(f => f[0] === state.invFilter) || filters[0];
      const rows = items.filter(i => active[2](i) &&
        (!q || [i.id, i.name, i.donorName, i.details, i.category].join(' ').toLowerCase().includes(q)));
      $('#inv-table').innerHTML = rows.length ? `<table><thead><tr><th>Item</th><th>Category</th><th class="num-col">Left / received</th><th>Details</th><th>Donor</th><th>Expiry</th></tr></thead><tbody>${rows.map(i => `
        <tr><td><span class="id">${esc(i.id)}</span>${esc(i.name)}</td><td>${catTag(i.category)}</td>
        <td class="num-col"><div class="stock-cell c-${esc(i.category)}"><span>${i.quantity} / ${i.initialQuantity}</span><div class="bar"><i style="width:${pct(i.quantity, i.initialQuantity)}%"></i></div></div></td>
        <td class="muted">${esc(i.details)}</td>
        <td>${esc(i.donorName || '—')}</td><td>${expiryStamp(i)}</td></tr>`).join('')}</tbody></table>`
        : `<p class="empty">${items.length ? 'Nothing matches this filter.' : 'No donations yet. <a href="#/log">Log the first one.</a>'}</p>`;
    };
    $('#chips').addEventListener('click', ev => {
      const b = ev.target.closest('[data-f]');
      if (!b) return;
      state.invFilter = b.dataset.f;
      $$('#chips button').forEach(c => c.setAttribute('aria-pressed', String(c.dataset.f === state.invFilter)));
      draw();
    });
    state.onQuery = draw;
    draw();
  }
};

routes.needs = {
  title: 'Needs',
  async render() {
    const needs = await api('/needs');
    const sorted = [...needs].sort((a, b) => (a.collected / a.target) - (b.collected / b.target));
    const catOpts = state.meta.categories.map(c => `<option value="${c.id}">${esc(c.label)}</option>`).join('');
    view.innerHTML = takeFlash() + pageHead('Needs', 'Targets for the drive, set against how much of the drive has already gone by.') + `
      <div class="grid">
        <section class="panel s7" id="need-list">${panelHead('Still needed', 'furthest behind first')}
          ${needs.length ? paceKey() + sorted.map(n => paceBar(n, true)).join('') : `<p class="empty">No targets yet. <b>Set the first one.</b></p>`}</section>
        <form class="panel form s5" id="need-form" novalidate>
          <h2>Set a target</h2>
          <div class="field"><label for="n-cat">Category</label><select id="n-cat" name="category">${catOpts}</select></div>
          <div class="field"><label for="n-name">Item name</label><input id="n-name" name="itemName" type="text" placeholder="Blanket" required>
            <span class="hint">Matches any donated item whose name contains this text.</span></div>
          <div class="field"><label for="n-target">Target quantity</label><input id="n-target" name="target" type="number" min="1" step="1" required></div>
          <div id="form-msg" aria-live="polite"></div>
          <div><button class="btn primary" type="submit">Save target</button></div>
        </form>
      </div>`;
    const form = $('#need-form');
    $('#need-list').addEventListener('click', ev => {
      const b = ev.target.closest('[data-edit]');
      if (!b) return;
      const [cat, name, target] = b.dataset.edit.split('|');
      form.category.value = cat; form.itemName.value = name; form.target.value = target;
      form.target.focus();
    });
    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      try {
        const n = await api('/needs', { category: form.category.value, itemName: form.itemName.value, target: Number(form.target.value) });
        state.flash = { kind: 'ok', html: `Target saved: ${esc(n.itemName)} ×${num(n.target)} (${num(n.collected)} collected).` };
        await route();
      } catch (e) {
        $('#form-msg').innerHTML = notice('bad', esc(e.message));
      }
    });
  }
};

const priorityTag = p => p === 1 ? `<span class="tag warn">${ICON.warn} Urgent</span>` : `<span class="tag plain">${PRIORITY[p]}</span>`;

routes.beneficiaries = {
  title: 'Beneficiaries',
  async render() {
    const list = await api('/beneficiaries');
    const maxUnits = Math.max(1, ...list.map(b => b.unitsReceived || 0));
    view.innerHTML = takeFlash() + pageHead('Beneficiaries', 'Registered families. A phone number can only be registered once.') + `
      <div class="grid">
        <section class="panel s8">${panelHead('Registered families', plural(list.length, 'family', 'families'))}
          ${list.length ? `<div class="scroll-x"><table><thead><tr><th>Family</th><th>Phone</th><th class="num-col">Size</th><th>Priority</th><th class="num-col">Received</th><th>Last aid</th></tr></thead><tbody>${list.map(b => `
            <tr><td><span class="id">${esc(b.id)}</span>${esc(b.name)}<div class="sub">${esc(b.address)}</div></td><td class="m">${esc(b.phone)}</td>
            <td class="m num-col">${b.familySize}</td><td>${priorityTag(b.priority)}</td>
            <td class="num-col"><div class="stock-cell"><span>${plural(b.unitsReceived || 0, 'unit')}</span><div class="bar"><i style="width:${pct(b.unitsReceived || 0, maxUnits)}%"></i></div></div></td>
            <td class="m">${fmtDate(b.lastReceived)}</td></tr>`).join('')}</tbody></table></div>`
            : `<p class="empty">No families yet. <b>Register the first one.</b></p>`}</section>
        <form class="panel form s4" id="ben-form" novalidate>
          <h2>Register a family</h2>
          <div class="field"><label for="b-name">Head of family</label><input id="b-name" name="name" type="text" required></div>
          <div class="row2"><div class="field"><label for="b-phone">Phone</label><input id="b-phone" name="phone" type="tel" required></div>
            <div class="field"><label for="b-size">Family size</label><input id="b-size" name="familySize" type="number" min="1" value="4" required></div></div>
          <div class="field"><label for="b-addr">Address / area</label><input id="b-addr" name="address" type="text"></div>
          <div class="field"><label for="b-pri">Priority</label><select id="b-pri" name="priority"><option value="1">1 · Urgent</option><option value="2" selected>2 · Normal</option><option value="3">3 · Lower</option></select></div>
          <div id="form-msg" aria-live="polite"></div>
          <div><button class="btn primary" type="submit">Register family</button></div>
        </form>
      </div>`;
    const form = $('#ben-form');
    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      try {
        const b = await api('/beneficiaries', {
          name: form.name.value, phone: form.phone.value, familySize: Number(form.familySize.value),
          address: form.address.value, priority: Number(form.priority.value)
        });
        state.flash = { kind: 'ok', html: `Registered <span class="mono">${esc(b.id)}</span> ${esc(b.name)}.` };
        await route();
      } catch (e) {
        const d = e.data || {};
        $('#form-msg').innerHTML = d.code === 'DUP'
          ? notice('bad', `<span class="mono">DUP</span> · <span class="mono">${esc(d.candidateId)}</span> matches <span class="mono">${esc(d.matchId)}</span> ${esc(d.matchName)} by phone. Not registered.`)
          : notice('bad', esc(e.message));
      }
    });
  }
};

routes.distribution = {
  title: 'Distribution',
  async render() {
    const cat = state.distCat;
    const [s, recent] = await Promise.all([api('/distribution/suggest?category=' + cat), api('/distributions')]);
    const lot = s.nextItem;
    const canGive = s.eligible.length > 0 && lot;
    view.innerHTML = takeFlash() + pageHead('Distribution', 'Pick a category. DriveDesk suggests who is eligible and which stock goes first.') + `
      <div class="filters" id="cats" role="group" aria-label="Category">${state.meta.categories.map(c =>
        `<button type="button" data-c="${c.id}" aria-pressed="${c.id === cat}">${catDot(c.id)}${esc(c.label)}</button>`).join('')}</div>
      <form class="grid" id="dist-form" novalidate>
        <section class="panel s7">${panelHead('Families', `${s.eligible.length} eligible · ${s.blocked.length} blocked`)}
          ${s.eligible.length ? `<div class="family-list" role="radiogroup" aria-label="Eligible families">${s.eligible.map((b, i) => `
            <label class="family"><input type="radio" name="beneficiaryId" value="${esc(b.id)}" ${i === 0 ? 'checked' : ''}>
              <span><span class="muted">${esc(b.id)}</span> ${esc(b.name)}<div class="meta">${b.familySize} people · ${esc(b.address || 'no address')}${b.lastReceived ? ' · last aid ' + fmtDate(b.lastReceived) : ''}</div></span>
              ${priorityTag(b.priority)}</label>`).join('')}</div>`
            : `<p class="empty">No eligible families. <a href="#/beneficiaries">Register one</a> or wait for the ${s.windowDays}-day window to pass.</p>`}
          ${s.blocked.length ? `<h3 class="group-label">Blocked: repeat within ${s.windowDays} days</h3><div class="family-list">${s.blocked.map(b => `
            <div class="family blocked"><span><span class="muted">${esc(b.id)}</span> ${esc(b.name)}
              <div class="meta">eligible again ${fmtDate(b.blockedUntil)}</div></span><span class="tag bad">${ICON.stop} Blocked · got ${fmtDate(b.blockedSince)}</span></div>`).join('')}</div>` : ''}
        </section>
        <section class="panel form s5">
          ${panelHead('Hand over')}
          ${lot ? `<div class="lot c-${esc(cat)}"><span class="hint">Next lot · earliest expiry first</span>
            <span class="big">${catDot(cat)}<span class="muted" style="font-weight:400;margin-right:6px">${esc(lot.id)}</span>${esc(lot.name)}</span>
            <span class="hint">${esc(lot.details)} · ${lot.quantity} in this lot</span><span>${expiryStamp(lot)}</span></div>
            <div class="hint">${num(s.availableUnits)} usable ${esc(catLabel(cat))} units in total</div>`
            : `<p class="empty">No usable ${esc(catLabel(cat))} in stock. <a href="#/log">Log a donation.</a></p>`}
          <div class="field"><label for="d-qty">Quantity</label><input id="d-qty" name="quantity" type="number" min="1" step="1" value="1" ${canGive ? '' : 'disabled'}></div>
          <div id="form-msg" aria-live="polite"></div>
          <div><button class="btn primary" type="submit" ${canGive ? '' : 'disabled'}>Hand over</button></div>
        </section>
      </form>
      <div class="grid"><section class="panel s12">${panelHead('Recent distributions')}${distTable(recent.slice(0, 8))}</section></div>`;

    $('#cats').addEventListener('click', ev => {
      const b = ev.target.closest('[data-c]');
      if (!b) return;
      state.distCat = b.dataset.c;
      route();
    });
    $('#dist-form').addEventListener('submit', async ev => {
      ev.preventDefault();
      const f = ev.target;
      try {
        const r = await api('/distributions', { beneficiaryId: f.beneficiaryId.value, category: cat, quantity: Number(f.quantity.value) });
        state.flash = { kind: 'ok', html: `Handed over ${r.total} ${esc(catLabel(cat))} unit${r.total === 1 ? '' : 's'} to <span class="mono">${esc(r.beneficiaryId)}</span> ${esc(r.beneficiaryName)}: ${r.lots.map(esc).join(', ')}.` };
        await route();
      } catch (e) {
        const blocked = e.data && (e.data.code === 'BLOCK' || e.data.code === 'STOCK');
        if (blocked) {
          state.flash = { kind: 'bad', html: `<span class="mono">${e.data.code}</span> · ${esc(e.message)}. Logged in the activity log.` };
          await route();
        } else {
          $('#form-msg').innerHTML = notice('bad', esc(e.message));
        }
      }
    });
  }
};

routes.reports = {
  title: 'Reports',
  async render() {
    const [sum, donors] = await Promise.all([api('/reports/summary'), api('/donors')]);
    view.innerHTML = takeFlash() + pageHead('Reports', 'Plain-text reports you can copy into an email or print.') + `
      <div class="grid" id="reports">
        <section class="panel s6">${panelHead('Drive summary', '', `<button class="btn small" type="button" data-copy="sum">Copy</button><button class="btn small" type="button" id="print">Print</button>`)}
          <pre class="report" id="sum">${esc(sum.text)}</pre></section>
        <section class="panel s6">${panelHead('Donor acknowledgement', '', `<button class="btn small" type="button" data-copy="ack">Copy</button>`)}
          <div class="field" style="margin-bottom:12px"><label for="r-donor">Donor</label><select id="r-donor">${donors.length ? donors.map(d => `<option value="${d.id}">${esc(d.id)} · ${esc(d.name)}</option>`).join('') : '<option value="">No donors yet</option>'}</select></div>
          <pre class="report" id="ack"></pre></section>
      </div>`;
    const loadAck = async () => {
      const id = $('#r-donor').value;
      $('#ack').textContent = id ? (await api('/reports/acknowledgement?donorId=' + encodeURIComponent(id))).text : 'Register a donor to preview an acknowledgement.';
    };
    $('#r-donor').addEventListener('change', loadAck);
    $('#print').addEventListener('click', () => window.print());
    $('#reports').addEventListener('click', async ev => {
      const b = ev.target.closest('[data-copy]');
      if (!b) return;
      try {
        await navigator.clipboard.writeText($('#' + b.dataset.copy).textContent);
        b.textContent = 'Copied';
        setTimeout(() => { b.textContent = 'Copy'; }, 1500);
      } catch { b.textContent = 'Copy failed'; }
    });
    await loadAck();
  }
};

const fmtDateY = iso => iso ? `${fmtDate(iso)} ${iso.slice(0, 4)}` : '—';

routes.drive = {
  title: 'Drive',
  async render() {
    const [cur, past] = await Promise.all([api('/drive'), api('/drives/past')]);
    const st = cur.stats, pv = cur.preview;
    const status = cur.ended ? `<span class="tag bad">${ICON.stop} Ended</span>`
      : cur.daysLeft === 0 ? `<span class="tag warn">${ICON.clock} Last day</span>`
      : `<span class="tag ok">${ICON.check} Running</span>`;
    const progress = cur.ended ? `Ended on ${fmtDateY(cur.endDate)}`
      : `Day ${cur.day} of ${cur.totalDays} · ${cur.daysLeft === 0 ? 'last day' : plural(cur.daysLeft, 'day') + ' left'}`;

    view.innerHTML = takeFlash() + pageHead('Drive', 'This drive, how to close it, and every drive before it.') +
      (cur.ended ? notice('warn', `<b>${esc(cur.name)}</b> ran until ${fmtDateY(cur.endDate)}. You can keep working in it, or close it and start the next drive.`) : '') + `
      <div class="grid">
        <div class="s7 stack">
        <section class="panel">
          <div class="drive-top"><div><div class="drive-name">${esc(cur.name)}</div><div class="drive-sub">${esc(cur.location)}</div></div>${status}</div>
          <dl class="facts">
            <dt>Dates</dt><dd>${fmtDateY(cur.startDate)} to ${fmtDateY(cur.endDate)} · ${plural(cur.totalDays, 'day')}</dd>
            <dt>Progress</dt><dd>${progress}</dd>
            <dt>Received</dt><dd>${plural(st.unitsReceived, 'unit')} in ${plural(st.lots, 'lot')}</dd>
            <dt>Handed out</dt><dd>${plural(st.unitsHandedOut, 'unit')} to ${plural(st.familiesServed, 'family', 'families')}</dd>
            <dt>In stock now</dt><dd>${plural(cur.unitsInStock, 'usable unit')}</dd>
            <dt>People on file</dt><dd>${plural(cur.donors, 'donor')} · ${plural(cur.families, 'family', 'families')}</dd>
          </dl>
        </section>
        <section class="panel" id="past-list">${panelHead('Past drives', past.length ? plural(past.length, 'drive') : '')}
        ${past.length ? `<div class="scroll-x"><table><thead><tr><th>Drive</th><th class="num-col">Received</th><th class="num-col">Handed out</th><th class="num-col">Families</th><th></th></tr></thead><tbody>${past.map(p => `
          <tr><td><b>${esc(p.name)}</b><div class="sub">${fmtDateY(p.startDate)} to ${fmtDateY(p.closedOn)} · ${esc(p.location)}</div></td>
          <td class="m num-col">${num(p.unitsReceived)}</td><td class="m num-col">${num(p.unitsHandedOut)}</td><td class="m num-col">${p.familiesServed}</td>
          <td class="num-col"><button class="btn small" type="button" data-sum="${esc(p.folder)}" data-name="${esc(p.name)}">Summary</button></td></tr>`).join('')}</tbody></table></div>`
          : `<p class="empty">No past drives yet. When you close a drive it is kept here, with its summary.</p>`}</section>
        </div>
        <form class="panel form s5" id="next-form" novalidate>
          <h2>Close this drive and start the next</h2>
          <div class="field"><label for="x-name">New drive name</label><input id="x-name" name="name" type="text" maxlength="60" required placeholder="Spring Relief"></div>
          <div class="field"><label for="x-loc">Location</label><input id="x-loc" name="location" type="text" value="${esc(cur.location)}"></div>
          <div class="row2">
            <div class="field"><label for="x-start">Start date</label><input id="x-start" name="startDate" type="date" value="${todayIso()}" max="${todayIso()}" required></div>
            <div class="field"><label for="x-days">Length (days)</label><input id="x-days" name="durationDays" type="number" min="1" max="365" step="1" value="30" required></div>
          </div>
          <div class="opts"><label class="check"><input type="checkbox" name="carryStock" checked> Carry over usable stock</label><span class="hint" id="h-stock"></span></div>
          <div class="opts"><label class="check"><input type="checkbox" name="keepTargets" checked> Keep the targets</label><span class="hint" id="h-targets"></span></div>
          <span class="hint">The current drive is saved first, with its summary, under Past drives. Donors and families stay on file. Handovers from the last ${pv.windowDays} days stay too, so the repeat rule still applies on day one.</span>
          <div id="form-msg" aria-live="polite"></div>
          <div class="btn-row"><button class="btn primary" type="submit" id="x-go">Close this drive and start</button><button class="btn" type="button" id="x-cancel" hidden>Cancel</button></div>
        </form>
      </div>
      <div class="grid" id="past-view" hidden><section class="panel s12">
        <div class="panel-h"><h2 id="past-title">Summary</h2><div class="r"><button class="btn small" type="button" id="past-copy">Copy</button><button class="btn small" type="button" id="past-close">Close</button></div></div>
        <pre class="report" id="past-text"></pre></section></div>`;

    // what the two options would do, in plain numbers
    const form = $('#next-form');
    const hints = () => {
      $('#h-stock').textContent = form.carryStock.checked
        ? `${plural(pv.usableLots, 'lot')} (${num(pv.usableUnits)} units) move across.${pv.unusableLots ? ` ${plural(pv.unusableLots, 'expired or unusable lot')} stay in the archive only.` : ''}`
        : `The new drive starts with empty shelves. ${num(pv.usableUnits)} units stay in the archive only.`;
      $('#h-targets').textContent = form.keepTargets.checked
        ? (pv.targets ? `${plural(pv.targets, 'target')} copied. You can edit them on Needs.` : 'There are no targets to copy.')
        : 'The new drive starts with no targets.';
    };
    hints();
    form.carryStock.addEventListener('change', hints);
    form.keepTargets.addEventListener('change', hints);

    // two-step: the first press asks, the second does it
    let armed = false;
    const disarm = () => {
      armed = false;
      $('#x-go').textContent = 'Close this drive and start';
      $('#x-cancel').hidden = true;
      $('#form-msg').innerHTML = '';
    };
    form.addEventListener('input', disarm);
    $('#x-cancel').addEventListener('click', disarm);
    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      const name = form.name.value.trim();
      if (!armed) {
        if (!name) { $('#form-msg').innerHTML = notice('bad', 'Give the new drive a name.'); form.name.focus(); return; }
        armed = true;
        $('#x-go').textContent = 'Yes, close it and start';
        $('#x-cancel').hidden = false;
        $('#form-msg').innerHTML = notice('warn', `<b>${esc(cur.name)}</b> will be saved under Past drives, and <b>${esc(name)}</b> starts ${form.startDate.value === todayIso() ? 'today' : 'on ' + fmtDateY(form.startDate.value)}. The current stock, targets and activity log are replaced as chosen above.`);
        return;
      }
      $('#x-go').disabled = true;
      try {
        const r = await api('/drive/next', {
          name, location: form.location.value, startDate: form.startDate.value, durationDays: Number(form.durationDays.value),
          carryStock: form.carryStock.checked, keepTargets: form.keepTargets.checked
        });
        const moved = r.lotsCarried ? ` ${plural(r.unitsCarried, 'unit')} in ${plural(r.lotsCarried, 'lot')} carried over.` : '';
        state.flash = { kind: 'ok', html: `<b>${esc(r.drive.name)}</b> has started. <b>${esc(r.previousName)}</b> is saved under Past drives.${moved}` };
        location.hash = '#/overview';
      } catch (e) {
        $('#x-go').disabled = false;
        disarm();
        $('#form-msg').innerHTML = notice('bad', esc(e.message));
      }
    });

    // past drive summaries
    $('#past-list').addEventListener('click', async ev => {
      const b = ev.target.closest('[data-sum]');
      if (!b) return;
      try {
        const r = await api('/drives/past/summary?folder=' + encodeURIComponent(b.dataset.sum));
        $('#past-title').textContent = `${b.dataset.name}: summary`;
        $('#past-text').textContent = r.text;
      } catch (e) { $('#past-text').textContent = e.message; }
      $('#past-view').hidden = false;
      $('#past-view').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });
    $('#past-close').addEventListener('click', () => { $('#past-view').hidden = true; });
    $('#past-copy').addEventListener('click', async ev => {
      try { await navigator.clipboard.writeText($('#past-text').textContent); ev.target.textContent = 'Copied'; }
      catch { ev.target.textContent = 'Copy failed'; }
      setTimeout(() => { ev.target.textContent = 'Copy'; }, 1500);
    });
  }
};

// ---------------------------------------------------------------- shell: router, drive context, theme

let renderToken = 0;

function paintDateline(drive) {
  const day = Math.min(drive.day, drive.totalDays), start = isoMs(drive.startDate);
  const segs = Array.from({ length: drive.totalDays }, (_, k) => {
    const cls = drive.ended || k < day - 1 ? 'past' : k === day - 1 ? 'today' : '';
    return `<i class="${cls}" title="Day ${k + 1} · ${fmtUtc(start + k * MS_DAY)}"></i>`;
  }).join('');
  const tail = drive.ended ? ' · ended' : drive.daysLeft > 0 ? ' · ' + plural(drive.daysLeft, 'day') + ' left' : ' · last day';
  $('#dateline').innerHTML = `<span><a href="#/drive" title="Manage this drive"><b>${esc(drive.name)}</b></a> · ${esc(drive.location)}</span>
    <div class="ctx-r">${drive.ended ? '<a class="ctx-next" href="#/drive">Start the next drive</a>' : ''}<div class="segs" role="img" aria-label="Day ${day} of ${drive.totalDays}${drive.ended ? ', ended' : ''}">${segs}</div>
    <span>Day <b>${day}</b> of ${drive.totalDays}${tail}</span></div>`;
}

async function refreshMeta() {
  state.meta = await api('/meta');
  const { drive, expiringCount } = state.meta;
  paintDateline(drive);
  const badge = $('#nav-badge');
  badge.hidden = expiringCount === 0;
  badge.textContent = expiringCount;
  badge.setAttribute('aria-label', `${expiringCount} lots expiring soon`);
}

function currentRoute() {
  const name = location.hash.replace(/^#\//, '').split('?')[0];
  return routes[name] ? name : 'overview';
}

async function route() {
  const token = ++renderToken;
  const name = currentRoute();
  state.onQuery = null;
  disposeCharts();
  $$('#nav a').forEach(a => {
    const active = a.dataset.route === name;
    a.classList.toggle('active', active);
    if (active) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
  view.classList.add('loading');
  try {
    await refreshMeta();
    if (token !== renderToken) return;
    document.title = `${routes[name].title} · DriveDesk`;
    await routes[name].render();
    if (token !== renderToken) return;
  } catch (e) {
    if (token !== renderToken) return;
    view.innerHTML = notice('bad', `Could not load this screen: ${esc(e.message)}`);
  } finally {
    if (token === renderToken) view.classList.remove('loading');
  }
}

const themeBtn = $('#theme-toggle');
const ICON_MOON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z"/></svg>';
const ICON_SUN = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';
const isDark = () => {
  const t = document.documentElement.dataset.theme;
  return t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
};
function paintTheme() {
  themeBtn.innerHTML = isDark() ? ICON_SUN : ICON_MOON;
  themeBtn.setAttribute('aria-label', isDark() ? 'Switch to light mode' : 'Switch to dark mode');
}
themeBtn.addEventListener('click', () => {
  const next = isDark() ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem('dd-theme', next); } catch { /* storage unavailable */ }
  paintTheme();
});
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', paintTheme);
paintTheme();

$('#q').addEventListener('input', ev => {
  state.q = ev.target.value;
  if (state.onQuery) state.onQuery();
});
$('#search').addEventListener('submit', ev => {
  ev.preventDefault();
  if (currentRoute() !== 'inventory') location.hash = '#/inventory';
});
document.addEventListener('keydown', ev => {
  if (ev.key === '/' && !/^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement.tagName)) { ev.preventDefault(); $('#q').focus(); }
});

window.addEventListener('hashchange', route);
route();
