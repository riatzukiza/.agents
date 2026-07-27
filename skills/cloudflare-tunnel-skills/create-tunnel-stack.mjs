#!/usr/bin/env node
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const cfApiBase = process.env.CF_API_BASE || 'https://api.cloudflare.com/client/v4';
const configDir = process.env.CONFIG_DIR || '/etc/cloudflared';
const serviceName = process.env.SERVICE_NAME || 'cloudflared';
const runtime = process.env.RUNTIME || 'systemd';

function requireEnv(key) {
  const value = process.env[key];
  if (!value) throw new Error(`${key} is required`);
  return value;
}

async function fetchJson(method, path, payload) {
  const url = `${cfApiBase}${path}`;
  const options = {
    method,
    headers: {
      Authorization: `Bearer ${requireEnv('CF_API_TOKEN')}`,
      'Content-Type': 'application/json',
    },
  };
  if (payload !== undefined) {
    options.body = JSON.stringify(payload);
  }
  const response = await fetch(url, options);
  const text = await response.text();
  let parsed;
  try {
    parsed = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(`Non-JSON response (${response.status}): ${text}`);
  }
  if (!response.ok || !parsed.success) {
    throw new Error(`Cloudflare API error ${response.status}: ${JSON.stringify(parsed.errors || parsed)}`);
  }
  return parsed.result;
}

function randomSecretB64() {
  return execSync('openssl rand -base64 32', { encoding: 'utf8' }).trim();
}

function parseRules(args) {
  const rules = [];
  for (const arg of args) {
    const idx = arg.indexOf('=');
    if (idx === -1) throw new Error(`Invalid rule (expected host=port or host=service): ${arg}`);
    const host = arg.slice(0, idx).trim();
    let service = arg.slice(idx + 1).trim();
    if (/^\d+$/.test(service)) {
      service = `http://localhost:${service}`;
    }
    if (!host || !service) throw new Error(`Invalid rule: ${arg}`);
    rules.push({ host, service });
  }
  if (rules.length === 0) throw new Error('At least one host=service rule is required');
  return rules;
}

function usage() {
  return [
    'Usage:',
    '  node create-tunnel-stack.mjs <tunnel-name> <host=service>...',
    '',
    'Examples:',
    '  node create-tunnel-stack.mjs my-tunnel app.example.com=3000 api.example.com=3001',
    '  node create-tunnel-stack.mjs my-tunnel app.example.com=http://localhost:3000',
    '',
    'Environment:',
    '  CF_API_TOKEN          Cloudflare API token',
    '  CF_ACCOUNT_ID         Cloudflare account ID',
    '  CF_ZONE_ID            Cloudflare zone ID',
    '  CF_ZONE_NAME          Zone name (default: promethean.rest)',
    '  CONFIG_DIR            cloudflared config dir (default: /etc/cloudflared)',
    '  SERVICE_NAME          systemd service name (default: cloudflared)',
    '  RUNTIME               systemd|docker|manual (default: systemd)',
    '  CF_API_BASE           override API base URL',
    '',
    'The script creates the tunnel, DNS CNAME records, config, and systemd service.',
  ].join('\n');
}

function runCommand(command, env) {
  execSync(command, { stdio: 'inherit', env: { ...process.env, ...env } });
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length === 0 || ['help', '-h', '--help'].includes(args[0])) {
    console.log(usage());
    process.exit(0);
  }

  const tunnelName = args[0];
  const rules = parseRules(args.slice(1));

  const accountId = requireEnv('CF_ACCOUNT_ID');
  const zoneId = requireEnv('CF_ZONE_ID');
  const zoneName = process.env.CF_ZONE_NAME || 'promethean.rest';

  console.log(`Creating tunnel: ${tunnelName}`);
  const tunnel = await fetchJson('POST', `/accounts/${accountId}/cfd_tunnel`, {
    name: tunnelName,
    secret: randomSecretB64(),
  });
  const tunnelId = tunnel.id;
  console.log(`Tunnel created: ${tunnelId}`);

  const credentials = tunnel.credentials_file;
  if (!credentials) {
    throw new Error('Tunnel credentials not returned by API');
  }

  console.log('Creating DNS records...');
  for (const rule of rules) {
    const result = await fetchJson('POST', `/zones/${zoneId}/dns_records`, {
      type: 'CNAME',
      name: rule.host,
      content: `${tunnelId}.cfargotunnel.com`,
      proxied: true,
    });
    console.log(`  ${result.name} -> ${result.content}`);
  }

  console.log('Installing cloudflared...');
  const skillDir = path.dirname(new URL(import.meta.url).pathname);
  runCommand(`bash ${path.join(skillDir, 'cloudflared-setup.sh')} install`);

  console.log('Writing config and credentials...');
  const ingressRules = rules.map(r => `${r.host}=${r.service}`).join('\n');
  const setupEnv = {
    TUNNEL_ID: tunnelId,
    TUNNEL_NAME: tunnelName,
    TUNNEL_SECRET: credentials.TunnelSecret,
    CF_ACCOUNT_ID: accountId,
    INGRESS_RULES: ingressRules,
  };
  runCommand(`bash ${path.join(skillDir, 'cloudflared-setup.sh')} write-credentials`, setupEnv);
  runCommand(`bash ${path.join(skillDir, 'cloudflared-setup.sh')} write-config`, setupEnv);

  console.log('Installing systemd service...');
  runCommand(`bash ${path.join(skillDir, 'cloudflared-setup.sh')} install-service`, setupEnv);

  console.log('Validating config...');
  runCommand(`sudo cloudflared tunnel --config ${configDir}/config.yml ingress validate`);

  console.log('\nDone. Tunnel is healthy when cloudflared connects.');
  console.log('To check status:');
  console.log(`  sudo systemctl status ${serviceName}`);
  console.log('To view logs:');
  console.log(`  sudo journalctl -u ${serviceName} -f`);
  console.log('\nNOTE: If hostnames are nested like *.stealth.example.com, ensure a certificate covers that level.');
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
