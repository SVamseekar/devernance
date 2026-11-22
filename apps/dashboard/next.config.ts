import type { NextConfig } from "next";
import { securityHeaders } from "./lib/security-headers";

const nextConfig: NextConfig = {
  reactCompiler: true,
  // Self-host Docker image only (infra/Dockerfile.dashboard sets DOCKER_BUILD=1).
  // The Cloudflare Workers build (OpenNext, wrangler.jsonc) leaves this unset.
  ...(process.env.DOCKER_BUILD === "1" ? { output: "standalone" as const } : {}),
  // www.devernance.space is a custom domain on the same worker (wrangler.jsonc); send it to the apex.
  async redirects() {
    return [
      {
        source: "/:path*",
        has: [{ type: "host", value: "www.devernance.space" }],
        destination: "https://devernance.space/:path*",
        permanent: true,
      },
    ];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders() }];
  },
};

export default nextConfig;
