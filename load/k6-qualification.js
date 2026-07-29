import http from 'k6/http';
import ws from 'k6/ws';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const wsUrl = baseUrl.replace(/^http/, 'ws');
const staleMessages = new Counter('stale_websocket_messages');
const websocketAge = new Trend('websocket_age_ms', true);

export const options = {
  scenarios: {
    rest_users: {
      executor: 'ramping-vus',
      exec: 'restUser',
      stages: [
        {duration: __ENV.RAMP_DURATION || '5m', target: 10},
        {duration: __ENV.HOLD_DURATION || '60m', target: 10},
        {duration: __ENV.BURST_DURATION || '10m', target: 20},
        {duration: '2m', target: 0},
      ],
    },
    websocket_users: {
      executor: 'constant-vus',
      exec: 'websocketUser',
      vus: 10,
      duration: __ENV.WS_DURATION || '75m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.995'],
    stale_websocket_messages: ['count==0'],
    websocket_age_ms: ['p(95)<3000'],
  },
};

export function restUser() {
  const responses = http.batch([
    ['GET', `${baseUrl}/api/stocks/analytics`],
    ['GET', `${baseUrl}/api/ai-analysis`],
    ['GET', `${baseUrl}/api/reliability/summary`],
    ['GET', `${baseUrl}/api/paper/portfolio`],
  ]);
  responses.forEach(response => check(response, {'REST status is 200': value => value.status === 200}));
  sleep(1);
}

export function websocketUser() {
  const response = ws.connect(`${wsUrl}/ws/analytics`, {}, socket => {
    socket.on('message', payload => {
      const snapshot = JSON.parse(payload);
      const age = Date.now() - Date.parse(snapshot.timestamp);
      websocketAge.add(age);
      if (age > 3000) staleMessages.add(1);
    });
    socket.setTimeout(() => socket.close(), 60000);
  });
  check(response, {'WebSocket upgrade is 101': value => value && value.status === 101});
  sleep(1);
}
