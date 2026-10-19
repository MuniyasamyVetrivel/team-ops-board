// @vitest-environment node
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import { inlineScriptHashes, PLACEHOLDER, renderNginxConf } from './nginx-conf';

const indexHtml = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const template = readFileSync(new URL('../../deploy/nginx/team-ops.conf.template', import.meta.url), 'utf8');

const sha256 = (text: string) => `'sha256-${createHash('sha256').update(text, 'utf8').digest('base64')}'`;

describe('production Nginx config', () => {
  it('allows the inline theme script by its hash, so the CSP needs no unsafe-inline for scripts', () => {
    const conf = renderNginxConf(indexHtml, template);
    const csp = conf.split('\n').find((line) => line.includes('Content-Security-Policy')) ?? '';

    expect(inlineScriptHashes(indexHtml)).toHaveLength(1);
    expect(csp).toContain(`script-src 'self' ${inlineScriptHashes(indexHtml)[0]};`);
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(conf).not.toContain(PLACEHOLDER);
  });

  it('hashes the script exactly as served, line endings included', () => {
    const lf = '<script>\n  run();\n</script>';
    const crlf = lf.replaceAll('\n', '\r\n');

    expect(inlineScriptHashes(lf)).toEqual([sha256('\n  run();\n')]);
    expect(inlineScriptHashes(crlf)).not.toEqual(inlineScriptHashes(lf));
    expect(inlineScriptHashes('<script type="module" src="/assets/app.js"></script>')).toEqual([]);
  });

  it('refuses a page without inline scripts and a template without the placeholder', () => {
    expect(() => renderNginxConf('<p>no scripts</p>', template)).toThrow(/no inline script/);
    expect(() => renderNginxConf(indexHtml, 'server {}')).toThrow(/no __INLINE_SCRIPT_HASHES__/);
  });

  it('keeps the limits and headers the deployment relies on', () => {
    expect(template).toContain('client_max_body_size 10m;');
    expect(template).toContain('real_ip_header CF-Connecting-IP;');
    expect(template).toContain('set_real_ip_from 172.28.0.0/24;');
    expect(template).toContain('proxy_set_header X-Forwarded-For $remote_addr;');
    expect(template).toContain('try_files $uri /index.html;');
    expect(template).toMatch(/gzip on;/);
    for (const header of ['Strict-Transport-Security', 'X-Content-Type-Options', 'X-Frame-Options', 'Referrer-Policy']) {
      expect(template).toMatch(new RegExp(`add_header ${header} .* always;`));
    }
    // Only the server block may add headers: an add_header inside a location would drop all of these there.
    const locations = template.split(/\n\s*location /).slice(1);
    expect(locations.every((block) => !block.includes('add_header'))).toBe(true);
  });
});
