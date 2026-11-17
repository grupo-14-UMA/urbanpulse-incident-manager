import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { performance } from 'node:perf_hooks';

const maximumCount = 5_000_000;
const countArgument = process.argv[2];

if (!countArgument || !/^\d+$/.test(countArgument)) {
  console.error('Uso: node scripts/generate-synthetic-incidents.mjs <número-de-incidencias>');
  process.exit(2);
}

const incidentCount = Number(countArgument);
if (!Number.isSafeInteger(incidentCount) || incidentCount < 1 || incidentCount > maximumCount) {
  console.error(`El número debe estar entre 1 y ${maximumCount.toLocaleString('es-ES')}.`);
  process.exit(2);
}

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const sql = readFileSync(resolve(scriptDirectory, 'generate-synthetic-incidents.sql'), 'utf8');
console.log(`Cargando ${incidentCount.toLocaleString('es-ES')} incidencias sintéticas en la base local...`);
const startedAt = performance.now();
const result = spawnSync('docker', [
  'compose', 'exec', '-T', 'db', 'sh', '-c',
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"',
  'sh', '-v', `incident_count=${incidentCount}`, '-f', '-',
], {
  cwd: resolve(scriptDirectory, '..'),
  input: sql,
  encoding: 'utf8',
  maxBuffer: 16 * 1024 * 1024,
});

if (result.stdout) process.stdout.write(result.stdout);
if (result.stderr) process.stderr.write(result.stderr);
if (result.error) {
  console.error(`No se pudo ejecutar Docker Compose: ${result.error.message}`);
  process.exit(1);
}
if (result.status !== 0) process.exit(result.status ?? 1);

const elapsedSeconds = (performance.now() - startedAt) / 1000;
console.log(`Carga completada: ${incidentCount.toLocaleString('es-ES')} incidencias en ${elapsedSeconds.toFixed(1)} s.`);
