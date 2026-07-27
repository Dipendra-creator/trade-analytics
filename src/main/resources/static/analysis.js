const byId = id => document.getElementById(id);
const number = (value, digits = 2) => Number.isFinite(value)
  ? value.toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  : '--';
const signed = (value, suffix = '', digits = 2) => Number.isFinite(value)
  ? `${value >= 0 ? '+' : ''}${number(value, digits)}${suffix}`
  : '--';
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
})[character]);
const toneClass = value => value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';
const setTone = (element, value) => {
  element.classList.remove('positive', 'negative', 'neutral');
  element.classList.add(toneClass(value));
};

let snapshot = null;
let socket = null;
let reconnectTimer = null;
let lastReceivedAt = 0;
let filters = { query: '', sector: '', signal: '', sort: 'impact' };

function connect() {
  clearTimeout(reconnectTimer);
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${protocol}://${location.host}/ws/analytics`);
  updateConnection('CONNECTING', '');

  socket.onopen = () => updateConnection('LIVE SOCKET', 'live');
  socket.onmessage = event => {
    try {
      snapshot = JSON.parse(event.data);
      lastReceivedAt = Date.now();
      renderSnapshot();
    } catch (error) {
      showNotice('error', 'Snapshot could not be read', 'The server returned an invalid analytics payload.');
    }
  };
  socket.onerror = () => socket.close();
  socket.onclose = () => {
    updateConnection('RECONNECTING', 'offline');
    showNotice('error', 'Live feed interrupted', 'Keeping the last snapshot visible while the socket reconnects.');
    reconnectTimer = setTimeout(connect, 1800);
  };
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

function renderSnapshot() {
  const { index, forecast, breadth } = snapshot;
  const target = index.level + forecast.expectedPoints;
  const netImpact = index.totalAttributedPoints;
  const cancellation = breadth.absoluteImpactPoints > 0
    ? clamp((1 - Math.abs(netImpact) / breadth.absoluteImpactPoints) * 100, 0, 100)
    : 0;

  byId('marketState').textContent = `MARKET ${snapshot.marketStatus}`;
  byId('updatedAt').textContent = new Date(snapshot.timestamp).toLocaleTimeString('en-IN');
  byId('indexLevel').textContent = number(index.level);
  byId('indexChange').textContent = `${signed(index.changePoints)} (${signed(index.changePercent, '%')})`;
  setTone(byId('indexChange'), index.changePoints);
  byId('previousClose').textContent = number(index.previousClose);
  byId('syntheticLevel').textContent = number(index.syntheticLevel);
  byId('trackingDifference').textContent = `Tracking ${signed(index.trackingDifference)} pts`;
  setTone(byId('trackingDifference'), -Math.abs(index.trackingDifference));
  byId('attributedMove').textContent = `${signed(netImpact)} pts`;
  setTone(byId('attributedMove'), netImpact);
  byId('attributionCoverage').textContent = `Coverage ${snapshot.coverage}/50`;
  byId('forecastTarget').textContent = number(target);
  byId('forecastExpected').textContent = `${signed(forecast.expectedPoints)} expected points`;
  setTone(byId('forecastExpected'), forecast.expectedPoints);
  byId('confidence').textContent = `${number(forecast.confidence, 0)}%`;
  byId('forecastDirection').textContent = forecast.direction;
  setTone(byId('forecastDirection'), forecast.expectedPoints);

  byId('grossImpact').textContent = `${number(breadth.absoluteImpactPoints)} pts`;
  byId('netImpact').textContent = `${signed(netImpact)} pts`;
  setTone(byId('netImpact'), netImpact);
  byId('cancellationRatio').textContent = `${number(cancellation, 1)}%`;
  byId('topFiveShare').textContent = `${number(breadth.topFiveImpactShare, 1)}%`;

  renderRegime();
  renderForecast();
  renderSectors();
  populateSectorFilter();
  renderStocks();
  drawContributionChart();

  byId('exportButton').disabled = false;
  showNotice('ready', 'Live analytics synchronized', `${snapshot.coverage}/50 constituents are included in this calculation.`);
  updateFreshness();
}

function renderRegime() {
  const { breadth, forecast } = snapshot;
  const score = breadth.score;
  const concentration = breadth.topFiveImpactShare;
  let title = 'Balanced rotation';
  if (score >= .35) title = 'Broad risk-on participation';
  else if (score >= .12) title = 'Constructive positive breadth';
  else if (score <= -.35) title = 'Broad risk-off pressure';
  else if (score <= -.12) title = 'Defensive negative breadth';
  else if (concentration >= 60) title = 'Narrow leadership';

  let narrative = `${breadth.advances} stocks are advancing against ${breadth.declines} declining.`;
  if (concentration >= 60) narrative += ` The five largest drivers explain ${number(concentration, 0)}% of gross impact, so the move is concentrated.`;
  else narrative += ' Contribution is distributed enough to give breadth more explanatory value.';
  if (forecast.dispersion >= .5) narrative += ' Cross-sectional dispersion is elevated.';

  byId('regimeTitle').textContent = title;
  byId('regimeNarrative').textContent = narrative;
  byId('breadthNeedle').style.left = `${clamp((score + 1) * 50, 0, 100)}%`;
  byId('advancers').textContent = breadth.advances;
  byId('decliners').textContent = breadth.declines;
  byId('unchanged').textContent = breadth.unchanged;
  byId('breadthScore').textContent = signed(score, '', 3);
  setTone(byId('breadthScore'), score);
  byId('dispersion').textContent = `${number(forecast.dispersion, 3)}%`;
}

function renderForecast() {
  const { index, forecast } = snapshot;
  const expectedLevel = index.level + forecast.expectedPoints;
  const bandWidth = forecast.upperBound - forecast.lowerBound;
  const momentumPoints = index.level * .65 * forecast.momentumScore / 10000;
  const breadthPoints = forecast.expectedPoints - momentumPoints;
  const position = value => bandWidth > 0
    ? clamp((value - forecast.lowerBound) / bandWidth * 100, 0, 100)
    : 50;

  byId('forecastBadge').textContent = forecast.direction;
  byId('forecastBadge').className = `direction-badge ${toneClass(forecast.expectedPoints)}`;
  byId('lowerBound').textContent = number(forecast.lowerBound);
  byId('rangeCenter').textContent = number(expectedLevel);
  byId('upperBound').textContent = number(forecast.upperBound);
  byId('rangeCurrent').style.left = `${position(index.level)}%`;
  byId('rangeExpected').style.left = `${position(expectedLevel)}%`;
  byId('momentumScore').textContent = `${signed(forecast.momentumScore, ' bps')}`;
  setTone(byId('momentumScore'), forecast.momentumScore);
  byId('momentumPoints').textContent = `${signed(momentumPoints)} estimated points`;
  byId('breadthAdjustment').textContent = `${signed(breadthPoints)} pts`;
  setTone(byId('breadthAdjustment'), breadthPoints);
  byId('predictionWidth').textContent = `${number(bandWidth)} pts`;
  byId('diagnosticConfidence').textContent = `${number(forecast.confidence, 0)}%`;
}

function renderSectors() {
  const sectors = snapshot.sectors;
  if (!sectors.length) {
    byId('sectorRows').innerHTML = '<p class="empty-state">No sector attribution is available.</p>';
    return;
  }
  const maxImpact = Math.max(1, ...sectors.map(sector => Math.abs(sector.contributionPoints)));
  byId('sectorRows').innerHTML = sectors.map(sector => {
    const width = Math.abs(sector.contributionPoints) / maxImpact * 50;
    const barClass = sector.contributionPoints >= 0 ? 'positive-bar' : 'negative-bar';
    return `<div class="sector-row">
      <div class="sector-meta">
        <span>${escapeHtml(sector.sector)}<small>${number(sector.weight, 1)}% weight · ${sector.advances} up / ${sector.declines} down</small></span>
        <strong class="${toneClass(sector.contributionPoints)}">${signed(sector.contributionPoints)} pts</strong>
      </div>
      <div class="sector-track"><b class="${barClass}" style="width:${width}%"></b></div>
    </div>`;
  }).join('');
}

function populateSectorFilter() {
  const select = byId('sectorFilter');
  const current = select.value;
  const sectors = [...new Set(snapshot.stocks.map(stock => stock.sector))].sort();
  select.innerHTML = '<option value="">All sectors</option>' + sectors
    .map(sector => `<option value="${escapeHtml(sector)}">${escapeHtml(sector)}</option>`).join('');
  select.value = current;
}

function visibleStocks() {
  const query = filters.query.toLowerCase();
  const stocks = snapshot.stocks.filter(stock =>
    (!query || stock.symbol.toLowerCase().includes(query) || stock.name.toLowerCase().includes(query)) &&
    (!filters.sector || stock.sector === filters.sector) &&
    (!filters.signal || stock.signal === filters.signal));

  const sorters = {
    impact: (a, b) => Math.abs(b.contributionPoints) - Math.abs(a.contributionPoints),
    contribution: (a, b) => b.contributionPoints - a.contributionPoints,
    day: (a, b) => b.returnPercent - a.returnPercent,
    weight: (a, b) => b.weight - a.weight,
    risk: (a, b) => b.riskScore - a.riskScore,
    rank: (a, b) => a.rank - b.rank
  };
  return stocks.sort(sorters[filters.sort]);
}

function renderStocks() {
  const stocks = visibleStocks();
  byId('visibleCount').textContent = `${stocks.length} ${stocks.length === 1 ? 'stock' : 'stocks'}`;
  if (!stocks.length) {
    byId('stockRows').innerHTML = '<tr><td colspan="13" class="empty-state">No stocks match the selected filters.</td></tr>';
    return;
  }
  const maxRisk = Math.max(.001, ...snapshot.stocks.map(stock => stock.riskScore));
  byId('stockRows').innerHTML = stocks.map(stock => `<tr tabindex="0" data-symbol="${escapeHtml(stock.symbol)}" aria-label="Inspect ${escapeHtml(stock.symbol)}">
    <td><div class="stock-name"><span>${stock.rank}</span><div><strong>${escapeHtml(stock.symbol)}</strong><small>${escapeHtml(stock.name)}</small></div></div></td>
    <td>${escapeHtml(stock.sector)}</td>
    <td>${number(stock.weight, 2)}%</td>
    <td>${number(stock.price)}</td>
    <td>${number(stock.previousClose)}</td>
    <td class="${toneClass(stock.returnPercent)}">${signed(stock.returnPercent, '%')}</td>
    <td class="${toneClass(stock.return5m)}">${signed(stock.return5m, '%')}</td>
    <td class="${toneClass(stock.return15m)}">${signed(stock.return15m, '%')}</td>
    <td class="${toneClass(stock.return60m)}">${signed(stock.return60m, '%')}</td>
    <td class="${toneClass(stock.contributionPoints)}">${signed(stock.contributionPoints)}</td>
    <td>${number(stock.impactShare, 1)}%</td>
    <td><span class="risk-cell">${number(stock.riskScore, 3)}<i><b style="width:${stock.riskScore / maxRisk * 100}%"></b></i></span></td>
    <td><span class="signal ${toneClass(stock.contributionPoints)}">${escapeHtml(stock.signal)}</span></td>
  </tr>`).join('');
}

function drawContributionChart() {
  const canvas = byId('contributionChart');
  const context = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.clearRect(0, 0, width, height);

  const positive = [...snapshot.stocks].filter(stock => stock.contributionPoints > 0)
    .sort((a, b) => b.contributionPoints - a.contributionPoints).slice(0, 7);
  const negative = [...snapshot.stocks].filter(stock => stock.contributionPoints < 0)
    .sort((a, b) => a.contributionPoints - b.contributionPoints).slice(0, 7);
  const rows = [...positive, ...negative];
  if (!rows.length) return;

  const max = Math.max(.01, ...rows.map(stock => Math.abs(stock.contributionPoints)));
  const labelWidth = width < 520 ? 66 : 86;
  const numberWidth = width < 520 ? 48 : 62;
  const center = labelWidth + (width - labelWidth - numberWidth) / 2;
  const maxBarWidth = (width - labelWidth - numberWidth) / 2 - 8;
  const rowHeight = height / rows.length;
  context.font = '10px ui-monospace, Consolas, monospace';
  context.textBaseline = 'middle';

  context.strokeStyle = '#272c31';
  context.beginPath();
  context.moveTo(center, 0);
  context.lineTo(center, height);
  context.stroke();

  rows.forEach((stock, index) => {
    const y = index * rowHeight + rowHeight / 2;
    const barWidth = Math.abs(stock.contributionPoints) / max * maxBarWidth;
    context.fillStyle = '#8b949d';
    context.textAlign = 'left';
    context.fillText(stock.symbol, 0, y);
    context.fillStyle = stock.contributionPoints >= 0 ? '#5ee6a8' : '#ff6b76';
    if (stock.contributionPoints >= 0) context.fillRect(center, y - 4, barWidth, 8);
    else context.fillRect(center - barWidth, y - 4, barWidth, 8);
    context.textAlign = 'right';
    context.fillText(signed(stock.contributionPoints), width - 2, y);
  });
}

function openStockDetail(symbol) {
  const stock = snapshot.stocks.find(item => item.symbol === symbol);
  if (!stock) return;
  const momentum = [
    ['5m', stock.return5m], ['15m', stock.return15m], ['60m', stock.return60m], ['Day', stock.returnPercent]
  ];
  const maxMomentum = Math.max(.01, ...momentum.map(([, value]) => Math.abs(value)));
  const moveWord = stock.contributionPoints > .35 ? 'lifting' : stock.contributionPoints < -.35 ? 'dragging' : 'having a limited effect on';
  const momentumState = Math.sign(stock.return5m) === Math.sign(stock.return15m) && Math.sign(stock.return15m) === Math.sign(stock.return60m)
    ? 'Momentum is aligned across all measured horizons.'
    : 'Momentum is mixed across horizons, which weakens directional confirmation.';
  const concentrationState = stock.impactShare >= 10
    ? `Its ${number(stock.impactShare, 1)}% share of gross impact makes it a major current driver.`
    : `Its ${number(stock.impactShare, 1)}% share of gross impact limits its control of the broader move.`;

  byId('stockDetail').innerHTML = `<article class="stock-detail">
    <header><span>RANK ${stock.rank} · ${escapeHtml(stock.sector)} · ${number(stock.weight, 2)}% INDEX WEIGHT</span><h2>${escapeHtml(stock.symbol)}</h2><p>${escapeHtml(stock.name)}</p></header>
    <div class="detail-price"><strong>${number(stock.price)}</strong><span class="${toneClass(stock.returnPercent)}">${signed(stock.returnPercent, '%')}</span></div>
    <div class="detail-grid">
      <div><span>PREVIOUS CLOSE</span><strong>${number(stock.previousClose)}</strong><small>Day reference</small></div>
      <div><span>NIFTY CONTRIBUTION</span><strong class="${toneClass(stock.contributionPoints)}">${signed(stock.contributionPoints)} pts</strong><small>Weight adjusted</small></div>
      <div><span>IMPACT SHARE</span><strong>${number(stock.impactShare, 1)}%</strong><small>Share of gross move</small></div>
      <div><span>RISK SCORE</span><strong>${number(stock.riskScore, 3)}</strong><small>Weight × |5m return|</small></div>
      <div><span>5 MIN RETURN</span><strong class="${toneClass(stock.return5m)}">${signed(stock.return5m, '%')}</strong><small>Immediate momentum</small></div>
      <div><span>15 MIN RETURN</span><strong class="${toneClass(stock.return15m)}">${signed(stock.return15m, '%')}</strong><small>Forecast horizon</small></div>
      <div><span>60 MIN RETURN</span><strong class="${toneClass(stock.return60m)}">${signed(stock.return60m, '%')}</strong><small>Intraday context</small></div>
      <div><span>MODEL SIGNAL</span><strong class="${toneClass(stock.contributionPoints)}">${escapeHtml(stock.signal)}</strong><small>Contribution threshold</small></div>
    </div>
    <section class="momentum-curve"><h3>Momentum curve</h3><div class="momentum-bars">${momentum.map(([label, value]) => {
      const width = Math.abs(value) / maxMomentum * 50;
      return `<div class="momentum-row"><span>${label}</span><div><i class="${value >= 0 ? 'pos' : 'neg'}" style="width:${width}%"></i></div><strong class="${toneClass(value)}">${signed(value, '%')}</strong></div>`;
    }).join('')}</div></section>
    <section class="detail-reading"><h3>Current model reading</h3><p>${escapeHtml(stock.symbol)} is ${moveWord} Nifty by ${number(Math.abs(stock.contributionPoints))} points. ${momentumState} ${concentrationState}</p></section>
  </article>`;
  byId('stockDialog').showModal();
}

function exportSnapshot() {
  if (!snapshot) return;
  const blob = new Blob([JSON.stringify(snapshot, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `nifty-analysis-${new Date(snapshot.timestamp).toISOString().replace(/[:.]/g, '-')}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

function updateFreshness() {
  if (!lastReceivedAt) return;
  const seconds = Math.max(0, Math.floor((Date.now() - lastReceivedAt) / 1000));
  byId('dataFreshness').textContent = seconds < 2 ? 'Live snapshot received now' : `Last snapshot ${seconds}s ago`;
}

byId('stockSearch').addEventListener('input', event => { filters.query = event.target.value.trim(); if (snapshot) renderStocks(); });
byId('sectorFilter').addEventListener('change', event => { filters.sector = event.target.value; if (snapshot) renderStocks(); });
byId('signalFilter').addEventListener('change', event => { filters.signal = event.target.value; if (snapshot) renderStocks(); });
byId('sortBy').addEventListener('change', event => { filters.sort = event.target.value; if (snapshot) renderStocks(); });
byId('stockRows').addEventListener('click', event => { const row = event.target.closest('tr[data-symbol]'); if (row) openStockDetail(row.dataset.symbol); });
byId('stockRows').addEventListener('keydown', event => { const row = event.target.closest('tr[data-symbol]'); if (row && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); openStockDetail(row.dataset.symbol); } });
byId('closeDialog').addEventListener('click', () => byId('stockDialog').close());
byId('stockDialog').addEventListener('click', event => { if (event.target === byId('stockDialog')) byId('stockDialog').close(); });
byId('exportButton').addEventListener('click', exportSnapshot);
window.addEventListener('resize', () => { if (snapshot) drawContributionChart(); });
setInterval(updateFreshness, 1000);
connect();
