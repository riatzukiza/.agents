#!/usr/bin/env node
import crypto from 'node:crypto';
import process from 'node:process';

const cfApiBase = process.env.CF_API_BASE || 'https://api.cloudflare.com/client/v4';

function requireEnv(key) {
  const value = process.env[key];
  if (!value) throw new Error(`${key} is required`);
  return value;
}

function accountId() {
  return requireEnv('CF_ACCOUNT_ID');
}

function zoneId() {
  return requireEnv('CF_ZONE_ID');
}

function authHeaders() {
  return {
    Authorization: `Bearer ${requireEnv('CF_API_TOKEN')}`,
    'Content-Type': 'application/json',
  };
}

function pretty(x) {
  return JSON.stringify(x, null, 2);
}

function printJson(x) {
  console.log(pretty(x));
}

function randomSecretB64() {
  return crypto.randomBytes(32).toString('base64');
}

function joinHostname(zoneName, host) {
  return host === '@' ? zoneName : `${host}.${zoneName}`;
}

function tunnelCnameTarget(tunnelId) {
  return `${tunnelId}.cfargotunnel.com`;
}

async function fetchJson(method, path, payload) {
  const url = `${cfApiBase}${path}`;
  const options = {
    method,
    headers: authHeaders(),
  };
  if (payload !== undefined) {
    options.body = JSON.stringify(payload);
  }
  const response = await fetch(url, options);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ${response.statusText}\n${text}`);
  }
  return text ? JSON.parse(text) : null;
}

async function verifyToken() {
  printJson(await fetchJson('GET', '/user/tokens/verify'));
}

async function listTunnels() {
  printJson(await fetchJson('GET', `/accounts/${accountId()}/cfd_tunnel?is_deleted=false`));
}

async function getTunnel(tunnelId) {
  printJson(await fetchJson('GET', `/accounts/${accountId()}/cfd_tunnel/${tunnelId}`));
}

async function createTunnel(name, secret) {
  printJson(await fetchJson('POST', `/accounts/${accountId()}/cfd_tunnel`, {
    name,
    secret: secret || randomSecretB64(),
  }));
}

async function deleteTunnel(tunnelId) {
  printJson(await fetchJson('DELETE', `/accounts/${accountId()}/cfd_tunnel/${tunnelId}`));
}

async function getTunnelToken(tunnelId) {
  printJson(await fetchJson('GET', `/accounts/${accountId()}/cfd_tunnel/${tunnelId}/token`));
}

async function listDnsRecords() {
  printJson(await fetchJson('GET', `/zones/${zoneId()}/dns_records`));
}

async function createDnsRoute(zoneName, hostname, target, proxied = true) {
  printJson(await fetchJson('POST', `/zones/${zoneId()}/dns_records`, {
    type: 'CNAME',
    name: joinHostname(zoneName, hostname),
    content: target,
    proxied,
  }));
}

async function createRouteForTunnel(zoneName, hostname, tunnelId, proxied = true) {
  await createDnsRoute(zoneName, hostname, tunnelCnameTarget(tunnelId), proxied);
}

async function deleteDnsRoute(recordId) {
  printJson(await fetchJson('DELETE', `/zones/${zoneId()}/dns_records/${recordId}`));
}

function usage() {
  return [
    'Usage:',
    '  node cf-tunnel-fetch.mjs verify-token',
    '  node cf-tunnel-fetch.mjs list-tunnels',
    '  node cf-tunnel-fetch.mjs get-tunnel <tunnel-id>',
    '  node cf-tunnel-fetch.mjs create-tunnel <name> [base64-secret]',
    '  node cf-tunnel-fetch.mjs delete-tunnel <tunnel-id>',
    '  node cf-tunnel-fetch.mjs get-tunnel-token <tunnel-id>',
    '  node cf-tunnel-fetch.mjs list-dns-records',
    '  node cf-tunnel-fetch.mjs create-dns-route <zone-name> <hostname|@> <target> [proxied=true]',
    '  node cf-tunnel-fetch.mjs create-route-for-tunnel <zone-name> <hostname|@> <tunnel-id> [proxied=true]',
    '  node cf-tunnel-fetch.mjs delete-dns-route <record-id>',
  ].join('\n');
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseProxied(raw) {
  if (raw === undefined || raw === 'true' || raw === '1') return true;
  if (raw === 'false' || raw === '0') return false;
  throw new Error(`proxied must be true or false, got: ${raw}`);
}

async function dispatch(args) {
  const [cmd, ...more] = args;
  switch (cmd) {
    case 'verify-token':
      await verifyToken();
      break;
    case 'list-tunnels':
      await listTunnels();
      break;
    case 'get-tunnel':
      if (!more[0]) fail('tunnel id required');
      await getTunnel(more[0]);
      break;
    case 'create-tunnel':
      if (!more[0]) fail('name required');
      await createTunnel(more[0], more[1]);
      break;
    case 'delete-tunnel':
      if (!more[0]) fail('tunnel id required');
      await deleteTunnel(more[0]);
      break;
    case 'get-tunnel-token':
      if (!more[0]) fail('tunnel id required');
      await getTunnelToken(more[0]);
      break;
    case 'list-dns-records':
      await listDnsRecords();
      break;
    case 'create-dns-route': {
      const [zoneName, hostname, target, proxiedRaw] = more;
      if (!zoneName || !hostname || !target) fail('zone-name, hostname and target required');
      await createDnsRoute(zoneName, hostname, target, parseProxied(proxiedRaw));
      break;
    }
    case 'create-route-for-tunnel': {
      const [zoneName, hostname, tunnelId, proxiedRaw] = more;
      if (!zoneName || !hostname || !tunnelId) fail('zone-name, hostname and tunnel-id required');
      await createRouteForTunnel(zoneName, hostname, tunnelId, parseProxied(proxiedRaw));
      break;
    }
    case 'delete-dns-route':
      if (!more[0]) fail('record id required');
      await deleteDnsRoute(more[0]);
      break;
    case 'help':
    case '-h':
    case '--help':
    default:
      if (cmd && cmd !== 'help' && cmd !== '-h' && cmd !== '--help') {
        fail(`unknown command: ${cmd}\n\n${usage()}`);
      }
      console.log(usage());
  }
}

async function main() {
  const args = process.argv.slice(2);
  const cmd = args[0];
  if (['help', '-h', '--help', undefined].includes(cmd)) {
    console.log(usage());
    return;
  }
  requireEnv('CF_API_TOKEN');
  requireEnv('CF_ACCOUNT_ID');
  try {
    await dispatch(args);
  } catch (error) {
    fail(error.stack || error.message || String(error));
  }
}

main();
