const byId = id => document.getElementById(id);
const number = (value, digits = 2) => Number(value ?? 0).toLocaleString('en-IN', {minimumFractionDigits: digits, maximumFractionDigits: digits});
const money = value => `₹${number(value, 2)}`;

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
  byId('updatedAt').textContent = `Updated ${new Date(summary.timestamp).toLocaleString('en-IN')} · uptime ${number(summary.uptimeMillis / 3600000, 1)} hours`;
}

function renderTrades(trades) {
  byId('tradeCount').textContent = `${trades.length} RECORDED`;
  if (!trades.length) return;
  byId('tradeRows').innerHTML = trades.map(trade => {
    const pnl = trade.netPnl == null ? '--' : money(trade.netPnl);
    const pnlClass = trade.netPnl > 0 ? 'positive' : trade.netPnl < 0 ? 'negative' : '';
    return `<tr><td>${trade.symbol}</td><td>${trade.side}</td><td>${trade.state}</td><td>${number(trade.entryPrice)}</td><td>${number(trade.stopPrice)}</td><td>${number(trade.targetPrice)}</td><td>${trade.exitPrice == null ? '--' : number(trade.exitPrice)}</td><td class="${pnlClass}">${pnl}</td><td>${trade.exitReason ?? '--'}</td></tr>`;
  }).join('');
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
setInterval(refresh, 5000);
