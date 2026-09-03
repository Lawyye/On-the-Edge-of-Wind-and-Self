/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Photos still live on the previous deployment until the curator uploads her own
  // through the admin panel. See lib/assets.ts.
  images: { unoptimized: true },
};

export default nextConfig;
