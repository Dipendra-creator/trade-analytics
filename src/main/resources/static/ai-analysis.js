'use strict';

const byId = id => document.getElementById(id);
let reconnectTimer;
let lastSnapshot;

function connect() {
  clearTimeout(reconnectTimer);
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const socket = new WebSocket(`${protocol}//${location.host}/ws/ai-analysis`);
  updateConnection('CONNECTING', '');
  socket.onopen = () => updateConnection('LIVE', 'live');
  socket.onmessage = event => {
    try {
      const snapshot = JSON.parse(event.data);
      if (!snapshot || !Array.isArray(snapshot.candidates)) throw new Error('Invalid payload');
      lastSnapshot = snapshot;
      render(snapshot);
    } catch (error) {
      showNotice('error', 'Analysis payload could not be read', 'The last valid trade screen remains visible.');
    }
  };
  socket.onerror = () => socket.close();
  socket.onclose = () => {
    updateConnection('RECONNECTING', 'offline');
    showNotice('error', 'Live analysis interrupted', 'Reconnecting while the server continues calculating.');
    reconnectTimer = setTimeout(connect, 1800);
  };
}

function render(snapshot) {
  const modeLabels = {
    OPENAI: 'AI + QUANT LIVE',
    QUANT_ONLY: 'QUANT LIVE',
    AI_PENDING: 'AI SYNCING',
    AI_ERROR: 'QUANT FALLBACK'
  };
  byId('analysisMode').textContent = modeLabels[snapshot.analysisMode] || snapshot.analysisMode;
  byId('modelName').textContent = snapshot.analysisMode === 'QUANT_ONLY'
    ? 'Add an OpenAI key in Settings for AI context'
    : snapshot.model;
  byId('regimeHeading').textContent = snapshot.regime;
  byId('marketSummary').textContent = snapshot.summary;
  byId('riskNote').textContent = snapshot.riskNote;
  byId('marketState').textContent = `MARKET ${snapshot.marketStatus}`;
  byId('marketStatus').textContent = `Market ${snapshot.marketStatus.toLowerCase()}`;
  byId('updatedAt').textContent = new Date(snapshot.timestamp).toLocaleTimeString('en-IN');
  byId('candidateCount').textContent = snapshot.candidates.length;
  updateDataAge(snapshot.marketDataTimestamp);
  renderCandidates(snapshot.candidates);
  const detail = snapshot.analysisMode === 'OPENAI'
    ? 'OpenAI context and deterministic trade levels are synchronized.'
    : 'Deterministic screening is live. AI context activates after an OpenAI key is saved.';
  showNotice('ready', 'Continuous analysis active', detail);
}

function renderCandidates(candidates) {
  const grid = byId('candidateGrid');
  if (!candidates.length) {
    grid.innerHTML = '<div class="empty-state"><strong>No qualifying setup right now</strong><span>The engine is still live and will publish a candidate as momentum and agreement strengthen.</span></div>';
    return;
  }
  grid.innerHTML = candidates.map((candidate, index) => {
    const tone = candidate.side === 'SHORT' ? 'short' : 'long';
    const returnTone = value => value >= 0 ? 'positive' : 'negative';
    return `<article class="candidate ${tone}">
      <header class="candidate-head">
        <div class="identity"><h3>${escapeHtml(candidate.symbol)}</h3><span>${escapeHtml(candidate.name)} / ${escapeHtml(candidate.sector)}</span></div>
        <div class="trade-labels"><span class="side">${escapeHtml(candidate.side)}</span>${candidate.state === 'NEW' ? '<span class="new-state">NEW</span>' : ''}</div>
      </header>
      <div class="candidate-body">
        <div class="levels">
          <div><span>ENTRY</span><strong>${number(candidate.entry)}</strong></div>
          <div class="stop"><span>STOP</span><strong>${number(candidate.stop)}</strong></div>
          <div class="target"><span>TARGET</span><strong>${number(candidate.target)}</strong></div>
        </div>
        <div class="evidence">
          <div><span>CONFIDENCE</span><strong>${number(candidate.confidence, 0)}%</strong></div>
          <div><span>RISK / REWARD</span><strong>1 : ${number(candidate.riskReward, 1)}</strong></div>
          <div><span>5 MIN</span><strong class="${returnTone(candidate.return5m)}">${signed(candidate.return5m)}%</strong></div>
          <div><span>15 MIN</span><strong class="${returnTone(candidate.return15m)}">${signed(candidate.return15m)}%</strong></div>
        </div>
      </div>
      <footer class="candidate-foot"><p>${escapeHtml(candidate.thesis)}</p><strong>#${index + 1} SCORE ${number(candidate.score, 1)}</strong></footer>
    </article>`;
  }).join('');
}

async function refreshRecentTrades() {
  try {
    const response = await fetch('/api/paper/recent?limit=3', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const trades = await response.json();
    const grid = byId('recentTradeGrid');
    byId('tradeMonitorState').textContent = trades.length ? `${trades.filter(x => x.state === 'OPEN').length} open · live P&L` : 'No paper trades yet';
    if (!trades.length) {
      grid.innerHTML = '<div class="trade-empty">No trades have been recorded yet. The monitor will populate when a setup is opened.</div>';
      return;
    }
    grid.innerHTML = trades.map(trade => {
      const pnl = Number(trade.livePnl || 0);
      const duration = trade.heldSeconds < 60 ? `${trade.heldSeconds}s` : `${Math.floor(trade.heldSeconds / 60)}m`;
      return `<article class="recent-trade ${trade.state.toLowerCase()}">
        <div class="trade-top"><h3>${escapeHtml(trade.symbol)} <span>${escapeHtml(trade.side)}</span></h3><span class="trade-state">${escapeHtml(trade.state)}</span></div>
        <div class="trade-price-line">
          <div><span>ENTRY</span><strong>${number(trade.entryPrice)}</strong></div>
          <div><span>${trade.state === 'OPEN' ? 'LIVE' : 'EXIT'}</span><strong>${number(trade.currentPrice || trade.exitPrice || trade.entryPrice)}</strong></div>
          <div><span>TARGET</span><strong>${number(trade.targetPrice)}</strong></div>
        </div>
        <div class="trade-progress" title="Progress toward target"><i style="width:${Math.max(0, Math.min(100, Number(trade.targetProgress || 0)))}%"></i></div>
        <div class="trade-stats"><div><span>${trade.exitReason ? 'EXIT REASON' : 'HELD'}</span><strong>${escapeHtml(trade.exitReason || duration)}</strong></div><div><span>${trade.state === 'OPEN' ? 'LIVE P&L' : 'NET P&L'}</span><strong class="${pnl >= 0 ? 'positive' : 'negative'}">${pnl >= 0 ? '+' : '-'}₹${number(Math.abs(pnl))}</strong></div></div>
      </article>`;
    }).join('');
  } catch (error) {
    byId('tradeMonitorState').textContent = 'Trade monitor reconnecting';
  }
}

function updateDataAge(timestamp) {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(timestamp).getTime()) / 1000));
  byId('dataAge').textContent = seconds < 2 ? 'NOW' : `${seconds}s ago`;
}

function updateConnection(label, stateClass) {
  const element = byId('connectionState');
  element.textContent = label;
  element.className = `connection ${stateClass}`.trim();
}

function showNotice(state, title, detail) {
  const notice = byId('feedNotice');
  notice.className = `feed-notice ${state}`;
  notice.innerHTML = `<strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span>`;
}

function number(value, decimals = 2) {
  return Number(value).toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}
function signed(value) { return `${Number(value) >= 0 ? '+' : ''}${number(value)}`; }
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  })[character]);
}

setInterval(() => {
  if (lastSnapshot) updateDataAge(lastSnapshot.marketDataTimestamp);
}, 1000);
refreshRecentTrades();
setInterval(refreshRecentTrades, 2000);
connect();
