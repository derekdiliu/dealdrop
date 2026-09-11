import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  images: {
    // Deal images are served from Cloudflare R2's public development URL.
    remotePatterns: [{ protocol: "https", hostname: "**.r2.dev" }],
  },
};

export default nextConfig;
