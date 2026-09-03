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
 * Where the original photographs used to live. Empty by default: they have been
 * moved into this project's own storage, and hard-coding the old deployment
 * would be a trap if this project were ever renamed to that same name — the
 * fallback would then quietly point the site at itself.
 *
 * Set LEGACY_ASSET_ORIGIN to run the migration again from some other source.
 */
export const LEGACY_ORIGIN = process.env.LEGACY_ASSET_ORIGIN ?? '';

/** Assets still living on the old deployment all sit under this prefix. */
export const LEGACY_PREFIX = '/assets/source/';

export function assetUrl(path: string | null | undefined): string {
  if (!path) return '';
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) return path;
  if (LEGACY_ORIGIN && path.startsWith(LEGACY_PREFIX)) return `${LEGACY_ORIGIN}${path}`;
  return path;
}
