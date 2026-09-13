/* Sol-1 (day one) — sobriety & habit tracker that renders a lock screen wallpaper.
   All state lives in localStorage on the device. No network calls except fonts. */
(() => {
'use strict';

const KEY = 'sol1.v1';
const W = 1080, H = 2424;                  // Pixel 10 lock screen
const DAY = 86400000;
const MILESTONES = [1, 3, 7, 14, 21, 30, 60, 90, 100, 180, 200, 365, 500, 730, 1000, 1825, 3650];
const THEMES = {
  midnight: { name: 'Midnight', bg: ['#0b1020', '#1b2148'], glow: '#5b7cff', accent: '#8fb0ff' },
  forest:   { name: 'Forest',   bg: ['#07170f', '#12402c'], glow: '#2fbf7f', accent: '#8fe7bd' },
  ember:    { name: 'Ember',    bg: ['#1c0a0a', '#5a1f1a'], glow: '#ff7a45', accent: '#ffb38a' },
  ocean:    { name: 'Ocean',    bg: ['#04121f', '#0f3a5f'], glow: '#22b8e0', accent: '#8fe1ff' },
  plum:     { name: 'Plum',     bg: ['#160b22', '#3d1f5c'], glow: '#b06cff', accent: '#d9b8ff' },
  graphite: { name: 'Graphite', bg: ['#0e0e0e', '#2a2a2a'], glow: '#9a9a9a', accent: '#e6e6e6' },
};
const QUOTES = [
  'One day at a time. This one counts.',
  'You did not come this far to only come this far.',
  'The craving is a wave. Waves end.',
  'Future you is watching. Make them proud.',
  'Discipline is choosing what you want most over what you want now.',
  'Every number on this screen is a day you chose yourself.',
  'Slow is fine. Stopping is the only thing that resets this.',
  'You are not behind. You are here, and here is the right place.',
  'Hard days still count as days.',
  'Progress, not perfection.',
  'The urge will pass whether you act on it or not.',
  'Keep the streak. Keep the promise.',
  'Nothing you are craving is better than the number above.',
  'Show up today. That is the whole job.',
];

/* ---------- state ---------- */
const defaults = () => ({
  trackers: [],
  wall: { theme: 'midnight', y: 64, quote: true, since: true, milestone: true, guides: false, ids: [] },
});
let state = load();

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return defaults();
    const s = JSON.parse(raw);
    return { ...defaults(), ...s, wall: { ...defaults().wall, ...(s.wall || {}) } };
  } catch { return defaults(); }
}
function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch {} }
const uid = () => Math.random().toString(36).slice(2, 10);

/* ---------- date helpers ---------- */
const pad = n => String(n).padStart(2, '0');
const dayKey = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const fromKey = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const toLocalInput = d => `${dayKey(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const fmtDate = d => d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
const fmtShort = d => d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;

/* ---------- stats ---------- */
function milestone(days) {
  const next = MILESTONES.find(m => m > days) ?? Math.ceil((days + 1) / 365) * 365;
  const prev = [...MILESTONES].reverse().find(m => m <= days) ?? 0;
  return { next, prev, toGo: next - days, progress: (days - prev) / (next - prev) };
}

function countStats(t, now = new Date()) {
  const start = new Date(t.start);
  const ms = Math.max(0, now - start);
  const days = Math.floor(ms / DAY);
  const rem = ms - days * DAY;
  const hours = Math.floor(rem / 3600000), mins = Math.floor(rem % 3600000 / 60000), secs = Math.floor(rem % 60000 / 1000);
  const hist = t.history || [];
  const longest = Math.max(days, ...hist.map(h => h.days));
  const total = days + hist.reduce((a, h) => a + h.days, 0);
  return { kind: 'count', days, hours, mins, secs, start, longest, total, resets: hist.length, ...milestone(days) };
}

function habitStats(t, now = new Date()) {
  const set = new Set(t.checkins || []);
  const today = dayKey(now);
  const doneToday = set.has(today);
  let d = doneToday ? now : addDays(now, -1);
  let streak = 0;
  while (set.has(dayKey(d))) { streak++; d = addDays(d, -1); }
  // longest run
  const keys = [...set].sort();
  let longest = 0, run = 0, prevKey = null;
  for (const k of keys) {
    run = (prevKey && dayKey(addDays(fromKey(prevKey), 1)) === k) ? run + 1 : 1;
    longest = Math.max(longest, run); prevKey = k;
  }
  const grid = Array.from({ length: 28 }, (_, i) => {
    const day = addDays(now, i - 27);
    return { key: dayKey(day), on: set.has(dayKey(day)), today: i === 27 };
  });
  return { kind: 'habit', days: streak, doneToday, longest: Math.max(longest, streak), total: set.size, grid, ...milestone(streak) };
}

const statsFor = t => t.type === 'habit' ? habitStats(t) : countStats(t);

/* ---------- UI: trackers ---------- */
const $ = s => document.querySelector(s);
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function renderTrackers() {
  const host = $('#trackers');
  if (!state.trackers.length) {
    host.innerHTML = `<div class="card empty"><b>No trackers yet.</b><br>Add a “days since” counter for sobriety, or a daily habit you want to keep.</div>`;
    return;
  }
  host.innerHTML = state.trackers.map(t => {
    const s = statsFor(t);
    const big = s.kind === 'count' && s.days === 0
      ? `${s.hours}<small>hours</small>`
      : `${s.days}<small>${s.kind === 'habit' ? 'day streak' : plural(s.days, 'day').split(' ')[1]}</small>`;
    const live = s.kind === 'count'
      ? `<div class="live" data-live="${t.id}">${plural(s.days, 'day')}, ${s.hours}h ${pad(s.mins)}m ${pad(s.secs)}s · since ${fmtDate(s.start)}</div>`
      : `<div class="live">${s.doneToday ? 'Checked in today ✓' : 'Not checked in yet today'}</div>`;
    const grid = s.kind === 'habit'
      ? `<div class="dots">${s.grid.map(g => `<i class="${g.on ? 'on' : ''}${g.today ? ' today' : ''}" title="${g.key}"></i>`).join('')}</div>` : '';
    const actions = s.kind === 'habit'
      ? `<button class="${s.doneToday ? '' : 'primary'}" data-act="checkin" data-id="${t.id}">${s.doneToday ? 'Undo today' : 'Check in today'}</button>`
      : `<button class="danger" data-act="slip" data-id="${t.id}">I slipped</button>`;
    return `<div class="card tracker" data-id="${t.id}">
      <div class="row"><div class="name grow">${esc(t.name)}</div>
        <button class="icon" data-act="edit" data-id="${t.id}" aria-label="Edit">✎</button>
        <button class="icon" data-act="delete" data-id="${t.id}" aria-label="Delete">✕</button></div>
      <div class="big">${big}</div>
      ${live}
      <div class="bar"><i style="width:${Math.round(s.progress * 100)}%"></i></div>
      <div class="ms">Next milestone: ${s.next} days · ${plural(s.toGo, 'day')} to go</div>
      <div class="stats"><span>Longest <b>${s.longest}</b></span><span>Total <b>${s.total}</b></span>${s.kind === 'count' ? `<span>Resets <b>${s.resets}</b></span>` : ''}</div>
      ${grid}
      <div class="actions">${actions}</div>
    </div>`;
  }).join('');
}

function tickLive() {
  for (const el of document.querySelectorAll('[data-live]')) {
    const t = state.trackers.find(x => x.id === el.dataset.live);
    if (!t) continue;
    const s = countStats(t);
    el.textContent = `${plural(s.days, 'day')}, ${s.hours}h ${pad(s.mins)}m ${pad(s.secs)}s · since ${fmtDate(s.start)}`;
  }
}

$('#trackers').addEventListener('click', e => {
  const btn = e.target.closest('button[data-act]');
  if (!btn) return;
  const t = state.trackers.find(x => x.id === btn.dataset.id);
  if (!t) return;
  switch (btn.dataset.act) {
    case 'checkin': {
      const k = dayKey(new Date());
      const set = new Set(t.checkins || []);
      set.has(k) ? set.delete(k) : set.add(k);
      t.checkins = [...set].sort();
      commit(set.has(k) ? 'Checked in. Nice.' : 'Today un-checked.');
      break;
    }
    case 'slip': openSlip(t); break;
    case 'edit': openDialog(t); break;
    case 'delete':
      if (confirm(`Delete “${t.name}” and its history?`)) {
        state.trackers = state.trackers.filter(x => x !== t);
        state.wall.ids = state.wall.ids.filter(id => id !== t.id);
        commit('Deleted.');
      }
  }
});

/* ---------- dialogs ---------- */
const dlg = $('#dlg'), slipDlg = $('#slipDlg');
let editing = null;

function openDialog(t) {
  editing = t || null;
  $('#dlgTitle').textContent = t ? 'Edit tracker' : 'New tracker';
  $('#fName').value = t ? t.name : '';
  const type = t ? t.type : 'count';
  dlg.querySelector(`input[name=fType][value=${type}]`).checked = true;
  $('#fStart').value = toLocalInput(t && t.start ? new Date(t.start) : new Date());
  for (const r of dlg.querySelectorAll('input[name=fType]')) r.disabled = !!t;
  syncTypeFields();
  dlg.showModal();
  setTimeout(() => $('#fName').focus(), 50);
}
function syncTypeFields() {
  const type = dlg.querySelector('input[name=fType]:checked').value;
  $('#countFields').classList.toggle('hidden', type !== 'count');
  $('#habitFields').classList.toggle('hidden', type !== 'habit');
}
dlg.addEventListener('change', e => { if (e.target.name === 'fType') syncTypeFields(); });
$('#dlgCancel').addEventListener('click', () => dlg.close());
$('#dlgForm').addEventListener('submit', e => {
  e.preventDefault();
  const name = $('#fName').value.trim();
  if (!name) return;
  const type = dlg.querySelector('input[name=fType]:checked').value;
  if (editing) {
    editing.name = name;
    if (editing.type === 'count') editing.start = startFromInput();
  } else {
    const t = { id: uid(), name, type, created: new Date().toISOString() };
    if (type === 'count') { t.start = startFromInput(); t.history = []; }
    else t.checkins = [];
    state.trackers.push(t);
    if (state.wall.ids.length < 3) state.wall.ids.push(t.id);
  }
  dlg.close();
  commit(editing ? 'Saved.' : 'Tracker added.');
});
function startFromInput() {
  const v = $('#fStart').value;
  const d = v ? new Date(v) : new Date();
  return (isNaN(d) ? new Date() : d).toISOString();
}
$('#addBtn').addEventListener('click', () => openDialog(null));

let slipping = null;
function openSlip(t) {
  slipping = t;
  const s = countStats(t);
  $('#slipText').textContent = `“${t.name}” is at ${plural(s.days, 'day')}, ${s.hours}h. This logs a reset and starts the count again from right now.`;
  slipDlg.showModal();
}
$('#slipCancel').addEventListener('click', () => slipDlg.close());
slipDlg.querySelector('form').addEventListener('submit', e => {
  e.preventDefault();
  if (!slipping) return;
  const s = countStats(slipping);
  slipping.history = slipping.history || [];
  slipping.history.push({ start: slipping.start, end: new Date().toISOString(), days: s.days });
  slipping.start = new Date().toISOString();
  slipDlg.close();
  commit('Reset logged. Day 0 starts now.');
});

/* ---------- wallpaper options ---------- */
function renderOptions() {
  $('#themes').innerHTML = Object.entries(THEMES).map(([k, t]) =>
    `<button class="swatch" data-theme="${k}" aria-pressed="${state.wall.theme === k}" title="${t.name}" style="background:linear-gradient(160deg,${t.bg[0]},${t.bg[1]} 60%,${t.glow})"></button>`).join('');
  $('#wallPick').innerHTML = state.trackers.length
    ? state.trackers.map(t => `<div class="check"><input type="checkbox" data-pick="${t.id}" ${state.wall.ids.includes(t.id) ? 'checked' : ''}><span>${esc(t.name)}</span></div>`).join('')
    : `<div class="hint">Add a tracker first.</div>`;
  $('#yPos').value = state.wall.y;
  $('#optSince').checked = state.wall.since;
  $('#optMilestone').checked = state.wall.milestone;
  $('#optQuote').checked = state.wall.quote;
  $('#optGuides').checked = state.wall.guides;
}
$('#themes').addEventListener('click', e => {
  const b = e.target.closest('[data-theme]'); if (!b) return;
  state.wall.theme = b.dataset.theme; commit();
});
$('#wallPick').addEventListener('change', e => {
  const id = e.target.dataset.pick; if (!id) return;
  const ids = state.wall.ids.filter(x => x !== id);
  if (e.target.checked) ids.push(id);
  if (ids.length > 3) { e.target.checked = false; toast('Up to three on the lock screen.'); return; }
  state.wall.ids = state.trackers.map(t => t.id).filter(x => ids.includes(x)); // keep tracker order
  commit();
});
$('#yPos').addEventListener('input', e => { state.wall.y = +e.target.value; save(); drawPreview(); });
for (const [id, key] of [['#optSince', 'since'], ['#optMilestone', 'milestone'], ['#optQuote', 'quote'], ['#optGuides', 'guides']]) {
  $(id).addEventListener('change', e => { state.wall[key] = e.target.checked; commit(); });
}

/* ---------- wallpaper rendering ---------- */
function selectedTrackers() {
  const sel = state.trackers.filter(t => state.wall.ids.includes(t.id));
  return sel.length ? sel : state.trackers.slice(0, 1);
}
function withAlpha(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`;
}
function fitFont(ctx, text, weight, size, maxWidth, min = 60) {
  do { ctx.font = `${weight} ${size}px ${FONT}`; if (ctx.measureText(text).width <= maxWidth) break; size -= 8; } while (size > min);
  return size;
}
function wrap(ctx, text, maxWidth) {
  const words = text.split(' '), lines = []; let line = '';
  for (const w of words) {
    const test = line ? line + ' ' + w : w;
    if (ctx.measureText(test).width > maxWidth && line) { lines.push(line); line = w; } else line = test;
  }
  if (line) lines.push(line);
  return lines;
}
const FONT = '"Inter Tight", Roboto, system-ui, sans-serif';

let measureCanvas;
function measureCtx() {
  if (!measureCanvas) { measureCanvas = document.createElement('canvas'); measureCanvas.width = 1; measureCanvas.height = 1; }
  const m = measureCanvas.getContext('2d');
  m.textAlign = 'center'; m.textBaseline = 'middle';
  return m;
}

function drawContent(ctx, sel, cy, theme, now) {
  const [primary, ...rest] = sel;
  let bottom = drawPrimary(ctx, primary, cy, theme, now);
  for (const t of rest) bottom = drawSecondary(ctx, t, bottom + 40, theme);
  return bottom;
}

function drawWallpaper(ctx, { guides = false } = {}) {
  const theme = THEMES[state.wall.theme] || THEMES.midnight;
  const now = new Date();
  const sel = selectedTrackers();
  const LIMIT = H - 290;                 // keep clear of the lock screen shortcuts
  const MIN_CY = 1000;                   // keep the label clear of the clock zone
  let cy = H * state.wall.y / 100;

  // dry run: measure how tall the block is, then shift it up if it collides with the bottom
  let quoteLines = [];
  if (sel.length) {
    const m = measureCtx();
    const bottom = drawContent(m, sel, cy, theme, now);
    if (state.wall.quote) {
      const dayOfYear = Math.floor((now - new Date(now.getFullYear(), 0, 0)) / DAY);
      m.font = `italic 400 38px ${FONT}`;
      quoteLines = wrap(m, QUOTES[dayOfYear % QUOTES.length], 820);
    }
    const quoteH = quoteLines.length ? quoteLines.length * 50 + 70 : 0;
    const overflow = bottom + quoteH - LIMIT;
    if (overflow > 0) cy = Math.max(MIN_CY, cy - overflow);
    if (cy === MIN_CY && bottom - (H * state.wall.y / 100 - cy) + quoteH > LIMIT) quoteLines = [];
  }

  // background
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, theme.bg[0]); g.addColorStop(1, theme.bg[1]);
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  const glow = ctx.createRadialGradient(W / 2, cy, 0, W / 2, cy, 760);
  glow.addColorStop(0, withAlpha(theme.glow, .32)); glow.addColorStop(1, withAlpha(theme.glow, 0));
  ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);
  const glow2 = ctx.createRadialGradient(W * .9, H * .08, 0, W * .9, H * .08, 700);
  glow2.addColorStop(0, withAlpha(theme.accent, .10)); glow2.addColorStop(1, withAlpha(theme.accent, 0));
  ctx.fillStyle = glow2; ctx.fillRect(0, 0, W, H);

  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  let bottom = cy;
  if (!sel.length) {
    ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.font = `500 44px ${FONT}`;
    ctx.fillText('Add a tracker to see it here', W / 2, cy);
  } else {
    bottom = drawContent(ctx, sel, cy, theme, now);
  }

  if (quoteLines.length) {
    ctx.font = `italic 400 38px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.62)';
    const blockH = (quoteLines.length - 1) * 50;
    let y = Math.max(bottom + 110, H - 330 - blockH);
    for (const l of quoteLines) { ctx.fillText(l, W / 2, y); y += 50; }
  }

  if (guides) {
    ctx.fillStyle = 'rgba(255,255,255,.10)';
    ctx.fillRect(0, 130, W, 620); ctx.fillRect(0, H - 250, W, 250);
    ctx.fillStyle = 'rgba(255,255,255,.55)'; ctx.font = `500 40px ${FONT}`;
    ctx.fillText('clock · date · notifications', W / 2, 440);
    ctx.fillText('shortcuts · fingerprint', W / 2, H - 125);
  }
}

function drawPrimary(ctx, t, cy, theme, now) {
  const s = t.type === 'habit' ? habitStats(t, now) : countStats(t, now);
  const showHours = s.kind === 'count' && s.days === 0;
  const number = String(showHours ? s.hours : s.days);
  const unit = showHours ? plural(s.hours, 'hour').split(' ')[1] : (s.kind === 'habit' ? 'day streak' : plural(s.days, 'day').split(' ')[1]);

  // label
  ctx.letterSpacing = '10px';
  ctx.font = `600 46px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.72)';
  ctx.fillText(t.name.toUpperCase(), W / 2, cy - 250);
  ctx.letterSpacing = '0px';

  // number
  const size = fitFont(ctx, number, 800, 360, W - 140);
  ctx.letterSpacing = '-14px';
  ctx.fillStyle = '#fff'; ctx.shadowColor = withAlpha(theme.glow, .45); ctx.shadowBlur = 60;
  ctx.fillText(number, W / 2, cy);
  ctx.shadowBlur = 0; ctx.letterSpacing = '0px';

  // unit
  ctx.font = `500 56px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.9)';
  let y = cy + size * .62;
  ctx.fillText(unit, W / 2, y);
  y += 78;

  if (state.wall.since && s.kind === 'count') {
    ctx.font = `400 38px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.6)';
    ctx.fillText(`since ${fmtDate(s.start)}`, W / 2, y); y += 70;
  }
  if (s.kind === 'habit') {
    // 28-day grid, 14 per row
    const cols = 14, r = 14, gap = 52, x0 = W / 2 - (cols - 1) * gap / 2;
    y += 10;
    s.grid.forEach((g, i) => {
      const x = x0 + (i % cols) * gap, yy = y + Math.floor(i / cols) * gap;
      ctx.beginPath(); ctx.arc(x, yy, r, 0, Math.PI * 2);
      ctx.fillStyle = g.on ? theme.accent : 'rgba(255,255,255,.14)'; ctx.fill();
      if (g.today && !g.on) { ctx.strokeStyle = 'rgba(255,255,255,.5)'; ctx.lineWidth = 3; ctx.stroke(); }
    });
    y += gap + 60;
  }
  if (state.wall.milestone) {
    const bw = 520, bh = 10, bx = W / 2 - bw / 2;
    roundRect(ctx, bx, y, bw, bh, 5, 'rgba(255,255,255,.14)');
    roundRect(ctx, bx, y, Math.max(bh, bw * s.progress), bh, 5, theme.accent);
    y += 52;
    ctx.font = `400 36px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.6)';
    ctx.fillText(`Next: ${s.next} days · ${s.toGo} to go`, W / 2, y); y += 40;
  }
  return y;
}

function drawSecondary(ctx, t, y, theme) {
  const s = statsFor(t);
  const unit = s.kind === 'habit' ? 'day streak' : plural(s.days, 'day').split(' ')[1];
  ctx.font = `400 42px ${FONT}`; ctx.fillStyle = 'rgba(255,255,255,.62)';
  const label = `${t.name}  ·  `;
  ctx.font = `700 46px ${FONT}`; const numW = ctx.measureText(`${s.days} ${unit}`).width;
  ctx.font = `400 42px ${FONT}`; const labW = ctx.measureText(label).width;
  const x0 = W / 2 - (labW + numW) / 2;
  ctx.textAlign = 'left';
  ctx.fillText(label, x0, y + 30);
  ctx.font = `700 46px ${FONT}`; ctx.fillStyle = theme.accent;
  ctx.fillText(`${s.days} ${unit}`, x0 + labW, y + 30);
  ctx.textAlign = 'center';
  return y + 80;
}

function roundRect(ctx, x, y, w, h, r, fill) {
  ctx.beginPath(); ctx.roundRect(x, y, w, h, r); ctx.fillStyle = fill; ctx.fill();
}

const preview = $('#preview');
function drawPreview() { drawWallpaper(preview.getContext('2d'), { guides: state.wall.guides }); }

async function exportBlob() {
  await fontsReady();
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  drawWallpaper(c.getContext('2d'));
  return new Promise(res => c.toBlob(res, 'image/png'));
}
const fileName = () => `sol-1-${dayKey(new Date())}.png`;

$('#shareBtn').addEventListener('click', async () => {
  const blob = await exportBlob();
  const file = new File([blob], fileName(), { type: 'image/png' });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try { await navigator.share({ files: [file], title: 'Lock screen' }); return; }
    catch (e) { if (e.name === 'AbortError') return; }
  }
  download(blob); toast('Sharing not available here, downloaded instead.');
});
$('#downloadBtn').addEventListener('click', async () => { download(await exportBlob()); toast('Saved to Downloads.'); });
function download(blob) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = fileName();
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}

/* ---------- backup ---------- */
$('#exportBtn').addEventListener('click', () => {
  const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = `sol-1-backup-${dayKey(new Date())}.json`;
  document.body.appendChild(a); a.click(); a.remove();
});
$('#importBtn').addEventListener('click', () => $('#importFile').click());
$('#importFile').addEventListener('change', async e => {
  const f = e.target.files[0]; if (!f) return;
  try {
    const s = JSON.parse(await f.text());
    if (!Array.isArray(s.trackers)) throw new Error('bad file');
    if (!confirm(`Replace current data with ${s.trackers.length} tracker(s) from the backup?`)) return;
    state = { ...defaults(), ...s, wall: { ...defaults().wall, ...(s.wall || {}) } };
    commit('Backup restored.');
  } catch { toast('That file is not a Sol-1 backup.'); }
  e.target.value = '';
});

/* ---------- misc ---------- */
let toastTimer;
function toast(msg) {
  if (!msg) return;
  const el = $('#toast'); el.textContent = msg; el.classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
}
function commit(msg) { save(); renderAll(); toast(msg); }
function renderAll() { renderTrackers(); renderOptions(); drawPreview(); }

async function fontsReady() {
  if (!document.fonts) return;
  try { await Promise.all([800, 600, 500, 400].map(w => document.fonts.load(`${w} 40px "Inter Tight"`))); } catch {}
}

// install prompt
let deferredInstall = null;
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); deferredInstall = e; $('#installBtn').classList.remove('hidden'); });
$('#installBtn').addEventListener('click', async () => { if (!deferredInstall) return; deferredInstall.prompt(); deferredInstall = null; $('#installBtn').classList.add('hidden'); });

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

renderAll();
fontsReady().then(drawPreview);
setInterval(tickLive, 1000);
setInterval(() => { renderTrackers(); drawPreview(); }, 60000);   // day rollover
document.addEventListener('visibilitychange', () => { if (!document.hidden) renderAll(); });

// expose for tests
window.__sol1 = { state, drawWallpaper, exportBlob, THEMES };
})();
