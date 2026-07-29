const byId = id => document.getElementById(id);
const number = (value, digits = 2) => Number(value ?? 0).toLocaleString('en-IN', {minimumFractionDigits: digits, maximumFractionDigits: digits});
const money = value => `₹${number(value, 2)}`;
let actionAuthorization = '';

async function request(path) {
  const response = await fetch(path, {headers: {Accept: 'application/json'}, cache: 'no-store'});
  if (!response.ok) throw new Error(`${path} returned ${response.status}`);
  return response.json();
}

function renderSummary(summary) {
  const healthy = summary.status === 'HEALTHY';
  byId('healthBadge').textContent = summary.status;
  byId('healthBadge').classList.toggle('degraded', !healthy);
  byId('platformStatus').textContent = summary.status;
  byId('platformStatus').classList.toggle('degraded', !healthy);
  byId('marketStatus').textContent = `Market ${summary.marketStatus}`;
  byId('analyticsAge').textContent = summary.analyticsAgeSeconds < 0 ? 'NO DATA' : `${number(summary.analyticsAgeSeconds, 1)}s`;
  byId('coverage').textContent = `${summary.coverage}/50`;
  byId('feedStatus').textContent = summary.dhanConnected ? 'CONNECTED' : summary.marketStatus === 'OPEN' ? 'DISCONNECTED' : 'OFF HOURS';
  byId('packetCount').textContent = `${number(summary.packetsReceived, 0)} packets · ${summary.connectionAttempts} attempts`;
  byId('equity').textContent = money(summary.paper.currentEquity);
  byId('openPositions').textContent = number(summary.paper.openPositions, 0);
  byId('profitFactor').textContent = number(summary.paper.profitFactor, 2);
  byId('closedResults').textContent = `${summary.paper.wins}W / ${summary.paper.losses}L`;
  byId('riskState').textContent = summary.paper.riskHalted ? 'Daily loss limit reached' : 'Risk controls active';
  byId('riskState').classList.toggle('degraded', summary.paper.riskHalted);
  byId('entryMode').textContent = summary.paper.entriesPaused ? 'ENTRIES PAUSED' : 'AUTOMATIC ENTRIES ACTIVE';
  byId('entryMode').classList.toggle('degraded', summary.paper.entriesPaused);
  byId('updatedAt').textContent = `Updated ${new Date(summary.timestamp).toLocaleString('en-IN')} · uptime ${number(summary.uptimeMillis / 3600000, 1)} hours`;
}

function renderTrades(trades) {
  byId('tradeCount').textContent = `${trades.length} RECORDED`;
  if (!trades.length) return;
  byId('tradeRows').innerHTML = trades.map(trade => {
    const pnlValue = trade.state === 'OPEN' ? trade.livePnl : trade.netPnl;
    const pnl = pnlValue == null ? '--' : money(pnlValue);
    const pnlClass = pnlValue > 0 ? 'positive' : pnlValue < 0 ? 'negative' : '';
    const held = trade.heldSeconds < 60 ? `${trade.heldSeconds}s` : `${Math.floor(trade.heldSeconds / 60)}m`;
    const current = trade.currentPrice ?? trade.exitPrice;
    return `<tr><td>${trade.symbol}</td><td>${trade.side}</td><td>${trade.state}</td><td>${number(trade.entryPrice)}</td><td>${current == null ? '--' : number(current)}</td><td>${number(trade.stopPrice)}</td><td>${number(trade.targetPrice)}</td><td class="${pnlClass}">${pnl}</td><td>${trade.exitReason ?? held}</td></tr>`;
  }).join('');
}

function getAuthorization() {
  if (actionAuthorization) return actionAuthorization;
  const username = window.prompt('Settings administrator username');
  if (!username) return '';
  const password = window.prompt('Settings administrator password');
  if (!password) return '';
  actionAuthorization = `Basic ${btoa(`${username}:${password}`)}`;
  return actionAuthorization;
}

async function paperAction(path, confirmation) {
  if (confirmation && !window.confirm(confirmation)) return;
  const authorization = getAuthorization();
  if (!authorization) return;
  const buttons = [byId('scanTrades'), byId('closeAllTrades')];
  buttons.forEach(button => { button.disabled = true; });
  try {
    const response = await fetch(path, { method: 'POST', headers: { Authorization: authorization, Accept: 'application/json' } });
    if (response.status === 401) { actionAuthorization = ''; throw new Error('Administrator credentials were rejected.'); }
    if (!response.ok) throw new Error(`Action failed with HTTP ${response.status}.`);
    const result = await response.json();
    byId('actionStatus').textContent = result.message;
    await refresh();
  } catch (error) {
    byId('actionStatus').textContent = error.message;
  } finally {
    buttons.forEach(button => { button.disabled = false; });
  }
}

async function refresh() {
  try {
    const [summary, trades] = await Promise.all([request('/api/reliability/summary'), request('/api/paper/trades')]);
    renderSummary(summary);
    renderTrades(trades);
  } catch (error) {
    byId('healthBadge').textContent = 'UNAVAILABLE';
    byId('healthBadge').classList.add('degraded');
    byId('updatedAt').textContent = error.message;
  }
}

refresh();
setInterval(refresh, 2000);
byId('scanTrades').addEventListener('click', () => paperAction('/api/paper/actions/scan'));
byId('closeAllTrades').addEventListener('click', () => paperAction('/api/paper/actions/close-all', 'Close every open paper trade at the latest live price and pause new entries?'));
