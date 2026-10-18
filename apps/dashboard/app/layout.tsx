import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import { CookieConsent } from "@/components/cookie-consent";
import { Providers } from "@/components/providers";
import { siteConfig } from "@/lib/site-config";

// Fonts are vendored (SIL OFL, see app/fonts/) so builds never fetch from Google Fonts.
const plexSans = localFont({
  variable: "--font-sans",
  src: [
    { path: "./fonts/ibm-plex-sans-latin-400-normal.woff2", weight: "400", style: "normal" },
    { path: "./fonts/ibm-plex-sans-latin-500-normal.woff2", weight: "500", style: "normal" },
    { path: "./fonts/ibm-plex-sans-latin-600-normal.woff2", weight: "600", style: "normal" },
    { path: "./fonts/ibm-plex-sans-latin-700-normal.woff2", weight: "700", style: "normal" },
  ],
});

const plexSerif = localFont({
  variable: "--font-heading",
  src: [
    { path: "./fonts/ibm-plex-serif-latin-400-normal.woff2", weight: "400", style: "normal" },
    { path: "./fonts/ibm-plex-serif-latin-600-normal.woff2", weight: "600", style: "normal" },
    { path: "./fonts/ibm-plex-serif-latin-700-normal.woff2", weight: "700", style: "normal" },
  ],
});

const jetbrainsMono = localFont({
  variable: "--font-mono",
  src: [
    { path: "./fonts/jetbrains-mono-latin-400-normal.woff2", weight: "400", style: "normal" },
    { path: "./fonts/jetbrains-mono-latin-500-normal.woff2", weight: "500", style: "normal" },
  ],
});

const themeBootScript = `(function(){try{var t=localStorage.getItem("eu-ai-theme");var d=t==="dark";var r=document.documentElement;r.classList.toggle("dark",d);r.style.colorScheme=d?"dark":"light";}catch(e){}})();`;

export const metadata: Metadata = {
  metadataBase: new URL(siteConfig.url),
  title: {
    default: siteConfig.name,
    template: `%s | ${siteConfig.name}`,
  },
  description: siteConfig.description,
  keywords: [
    "EU AI Act compliance software",
    "EU AI Act",
    "AI system registry",
    "AI risk classification",
    "high-risk AI Annex III",
    "Article 50 transparency",
    "AI evidence pack",
    "AI release gate",
    "fail-closed AI governance",
    "Devernance",
  ],
  authors: [{ name: "Marti Soura Vamseekar", url: siteConfig.url }],
  alternates: {
    canonical: siteConfig.url,
  },
  openGraph: {
    title: siteConfig.name,
    description: siteConfig.description,
    siteName: siteConfig.name,
    locale: siteConfig.locale,
    type: "website",
    url: siteConfig.url,
    images: [{ url: "/opengraph-image", width: 1200, height: 630 }],
  },
  twitter: {
    card: "summary_large_image",
    title: siteConfig.name,
    description: siteConfig.description,
    images: ["/opengraph-image"],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en-GB"
      suppressHydrationWarning
      className={`${plexSans.variable} ${plexSerif.variable} ${jetbrainsMono.variable} h-full antialiased`}
    >
      <head>
        <script
          // Apply stored theme before paint to avoid light/dark flash.
          dangerouslySetInnerHTML={{ __html: themeBootScript }}
        />
      </head>
      <body className="min-h-full flex flex-col">
        <Providers>{children}</Providers>
        {process.env.NEXT_PUBLIC_CF_BEACON_TOKEN ? (
          <script
            defer
            src="https://static.cloudflareinsights.com/beacon.min.js"
            data-cf-beacon={JSON.stringify({ token: process.env.NEXT_PUBLIC_CF_BEACON_TOKEN })}
          />
        ) : null}
        {process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID ? (
          <CookieConsent measurementId={process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID} />
        ) : null}
      </body>
    </html>
  );
}
