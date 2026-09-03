/**
 * The original site's photographs still live on the previous deployment.
 * They could not be copied into this repo: the sandbox that built this site is
 * blocked from downloading binaries off that host, so the images are referenced
 * where they already are.
 *
 * This is deliberately a single choke point. Once the curator uploads her own
 * photographs through the admin panel, the stored value becomes an absolute
 * Supabase Storage URL and passes through untouched — so the dependency on the
 * old deployment disappears on its own, image by image, with no code change.
 */
const LEGACY_ORIGIN = 'https://orleu-mangistau-kdi.vercel.app';

export function assetUrl(path: string | null | undefined): string {
  if (!path) return '';
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) return path;
  if (path.startsWith('/assets/source/')) return `${LEGACY_ORIGIN}${path}`;
  return path;
}
