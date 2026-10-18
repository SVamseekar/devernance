import Link from "next/link";

import { DevernanceMark, EuFlag, GithubIcon } from "@/components/marketing/brand-icons";
import { footerColumns, legalLinks } from "@/lib/marketing-nav";
import { siteConfig } from "@/lib/site-config";

function FooterLink({ href, label, external }: { href: string; label: string; external?: boolean }) {
  const className = "text-sm text-on-dark-muted transition-colors hover:text-white";
  if (external) {
    const isMail = href.startsWith("mailto:");
    return (
      <a href={href} className={className} {...(isMail ? {} : { target: "_blank", rel: "noopener noreferrer" })}>
        {label}
      </a>
    );
  }
  return (
    <Link href={href} className={className}>
      {label}
    </Link>
  );
}

/** Two rows: brand + link columns, then one legal line. Kept short so it does not dominate short pages. */
export function SiteFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-white/10 bg-navy-950 text-white">
      <div className="mx-auto max-w-7xl px-4 pt-10 pb-6 sm:px-6 lg:px-8">
        <div className="grid gap-8 lg:grid-cols-[1.1fr_3fr]">
          <div>
            <Link href="/" className="inline-flex items-center gap-2.5 text-base font-bold tracking-tight">
              <DevernanceMark className="h-7 w-7" />
              {siteConfig.shortName}
            </Link>
            <p className="mt-3 max-w-xs text-sm text-on-dark-muted">
              Release gates and signed evidence packs for AI features.
            </p>
            <a
              href={siteConfig.githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-4 inline-flex items-center gap-2 text-sm text-on-dark-muted transition-colors hover:text-white"
            >
              <GithubIcon className="h-4 w-4" />
              GitHub
            </a>
          </div>
          <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
            {footerColumns.map((column) => (
              <nav key={column.title} aria-label={column.title}>
                <h2 className="text-xs font-semibold uppercase tracking-[0.08em] text-white/60">{column.title}</h2>
                <ul className="mt-3 space-y-1.5">
                  {column.links.map((link) => (
                    <li key={`${column.title}-${link.href}`}>
                      <FooterLink {...link} />
                    </li>
                  ))}
                </ul>
              </nav>
            ))}
          </div>
        </div>

        <div className="mt-8 flex flex-col gap-3 border-t border-white/10 pt-5 text-xs text-on-dark-muted lg:flex-row lg:items-center lg:justify-between">
          <p className="flex items-center gap-2">
            <EuFlag className="h-4 w-6 shrink-0 rounded-[2px]" />
            © {year} {siteConfig.shortName}. {siteConfig.shortName} provides evidence and readiness tooling. It is not legal advice, not a notified body, and does not certify compliance.
          </p>
          <nav aria-label="Legal">
            <ul className="flex flex-wrap gap-x-4 gap-y-1">
              {legalLinks.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} className="hover:text-white">
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </div>
    </footer>
  );
}
