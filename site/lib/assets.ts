/**
 * Turns a stored image reference into a URL.
 *
 * The original photographs now live in this project's own Supabase Storage and
 * are stored as absolute URLs, which pass through untouched. The legacy branch
 * below stays as a safety net: it expands the old `/assets/source/...` paths
 * against the previous deployment, so a reference the migration missed still
 * resolves rather than rendering as a broken image.
 */
/**
 * Overridable so the one-time migration in app/api/admin/migrate-images can be
 * exercised against a local source instead of the real deployment.
 */
export const LEGACY_ORIGIN =
  process.env.LEGACY_ASSET_ORIGIN ?? 'https://orleu-mangistau-kdi.vercel.app';

/** Assets still living on the old deployment all sit under this prefix. */
export const LEGACY_PREFIX = '/assets/source/';

export function assetUrl(path: string | null | undefined): string {
  if (!path) return '';
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) return path;
  if (path.startsWith(LEGACY_PREFIX)) return `${LEGACY_ORIGIN}${path}`;
  return path;
}
