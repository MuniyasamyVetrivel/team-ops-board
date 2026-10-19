// Writes the production Nginx config for a frontend build: the Content-Security-Policy allows exactly the inline
// scripts the built index.html contains (the theme script that runs before first paint), by their SHA-256 hashes.
//
//   node scripts/nginx-conf.ts <dist/index.html> <deploy/nginx/team-ops.conf.template> <out.conf>
//
// frontend/Dockerfile runs it after `npm run build`, so the hash always matches the file Nginx serves, whatever line
// endings the checkout had. It fails when the template or the page doesn't look as expected.
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const PLACEHOLDER = '__INLINE_SCRIPT_HASHES__';

/** The CSP source of each inline (non-src) script, e.g. `'sha256-…='`. */
export function inlineScriptHashes(html: string): string[] {
  const scripts = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)];
  return scripts.map((match) => `'sha256-${createHash('sha256').update(match[1], 'utf8').digest('base64')}'`);
}

export function renderNginxConf(html: string, template: string): string {
  if (!template.includes(PLACEHOLDER)) {
    throw new Error(`The Nginx template has no ${PLACEHOLDER}`);
  }
  const hashes = inlineScriptHashes(html);
  if (hashes.length === 0) {
    throw new Error('index.html has no inline script: remove the placeholder from the template instead');
  }
  return template.replaceAll(PLACEHOLDER, hashes.join(' '));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [indexPath, templatePath, outPath] = process.argv.slice(2);
  if (!indexPath || !templatePath || !outPath) {
    console.error('Usage: node scripts/nginx-conf.ts <index.html> <template> <out.conf>');
    process.exit(2);
  }
  const html = readFileSync(indexPath, 'utf8');
  writeFileSync(outPath, renderNginxConf(html, readFileSync(templatePath, 'utf8')));
  console.log(`Wrote ${outPath} with ${inlineScriptHashes(html).length} inline script hash(es)`);
}
