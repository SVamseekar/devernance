"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { ArrowRight, ChevronDown, ChevronRight, Menu, X } from "lucide-react";

import { AssuranceMark } from "@/components/marketing/brand-icons";
import { DemoButton } from "@/components/marketing/demo-button";
import { IconTile, type Tone } from "@/components/marketing/primitives";
import { iconToneClass, mainNav, type MegaItem, type MegaMenu } from "@/lib/marketing-nav";
import { menuAfterClick } from "@/lib/menu-toggle";
import { siteConfig } from "@/lib/site-config";
import { cn } from "@/lib/utils";

function MegaLink({ item, onNavigate }: { item: MegaItem; onNavigate: () => void }) {
  const body = (
    <>
      <IconTile icon={item.icon} className={iconToneClass[item.tone]} />
      <span>
        <span className="block text-sm font-semibold text-white">{item.label}</span>
        <span className="mt-0.5 block text-[13px] leading-snug text-on-dark-muted">{item.description}</span>
      </span>
    </>
  );
  const className =
    "flex items-start gap-3 rounded-xl p-2.5 transition-colors hover:bg-white/[0.06] focus-visible:bg-white/[0.06] focus-visible:outline-none";
  return item.external ? (
    <a href={item.href} target="_blank" rel="noopener noreferrer" className={className} onClick={onNavigate}>
      {body}
    </a>
  ) : (
    <Link href={item.href} className={className} onClick={onNavigate}>
      {body}
    </Link>
  );
}

function MegaPanel({ menu, id, onNavigate }: { menu: MegaMenu; id: string; onNavigate: () => void }) {
  return (
    <div
      id={id}
      className="absolute inset-x-0 top-full z-50 hidden px-4 pt-2 sm:px-6 lg:block lg:px-8"
      role="region"
      aria-label={`${menu.label} menu`}
    >
      <div className="mx-auto grid max-w-7xl grid-cols-[minmax(0,15rem)_1fr] overflow-hidden rounded-2xl border border-white/10 bg-navy-900/[0.98] shadow-2xl shadow-black/40 backdrop-blur-xl animate-fade-in">
        <div className="flex flex-col justify-between border-r border-white/10 p-6">
          <div>
            <p className="text-sm font-bold uppercase tracking-[0.08em] text-white">{menu.heading}</p>
            <p className="mt-3 text-[15px] leading-relaxed text-on-dark-muted">{menu.blurb}</p>
          </div>
          <Link
            href={menu.overview.href}
            onClick={onNavigate}
            className="mt-8 inline-flex items-center gap-1.5 text-sm font-semibold text-periwinkle hover:underline"
          >
            {menu.overview.label}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
        <ul className="grid grid-cols-2 gap-1 p-4">
          {menu.items.map((item) => (
            <li key={item.href}>
              <MegaLink item={item} onNavigate={onNavigate} />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/**
 * Public site header (design board items 1, 2, 4–8).
 * The header keeps the tone of the page it sits on: navy over dark pages, white over light ones.
 * Scrolling only adds depth (opaque background, shadow); it never flips colour, which would slice the page.
 */
export function SiteHeader({ tone }: { tone: Tone }) {
  const pathname = usePathname();
  const baseId = useId();
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [mobileSection, setMobileSection] = useState<string | null>(null);
  const [scrolled, setScrolled] = useState(false);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hoverOpenedAt = useRef<number | null>(null);
  const navRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const drawerRef = useRef<HTMLDivElement>(null);

  const onDark = tone === "dark" || mobileOpen;

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const closeAll = useCallback(() => {
    setOpenMenu(null);
    setMobileOpen(false);
  }, []);

  // Close menus on Escape and on clicks outside the nav.
  useEffect(() => {
    if (!openMenu) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpenMenu(null);
    }
    function onPointer(event: PointerEvent) {
      if (navRef.current && !navRef.current.contains(event.target as Node)) setOpenMenu(null);
    }
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onPointer);
    };
  }, [openMenu]);

  // Mobile drawer: trap focus, close on Escape, lock page scroll.
  useEffect(() => {
    if (!mobileOpen) return;
    const drawer = drawerRef.current;
    const toggle = toggleRef.current;
    const focusables = () =>
      drawer?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])') ?? ([] as unknown as NodeListOf<HTMLElement>);
    focusables()[0]?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileOpen(false);
        toggle?.focus();
        return;
      }
      if (event.key !== "Tab") return;
      const list = focusables();
      if (list.length === 0) return;
      const first = list[0];
      const last = list[list.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [mobileOpen]);

  function openWithHover(label: string) {
    if (closeTimer.current) clearTimeout(closeTimer.current);
    if (openMenu !== label) hoverOpenedAt.current = Date.now();
    setOpenMenu(label);
  }

  function closeWithDelay() {
    if (closeTimer.current) clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => setOpenMenu(null), 150);
  }

  function isActive(href: string) {
    return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
  }

  return (
    <header
      className={cn(
        "sticky top-0 z-40 transition-colors duration-200",
        // backdrop-blur would make the header the containing block of the fixed mobile drawer, so drop it while open.
        mobileOpen
          ? "border-b border-white/10 bg-navy-950 text-white"
          : onDark
            ? cn(
                "border-b border-white/10 text-white backdrop-blur-md",
                scrolled ? "bg-navy-950/95 shadow-lg shadow-black/30" : "bg-navy-950/70",
              )
            : cn("border-b border-line bg-white/95 text-ink backdrop-blur-md", scrolled && "shadow-sm shadow-navy-950/5"),
      )}
    >
      <div ref={navRef} className="relative" onMouseLeave={closeWithDelay}>
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-6 px-4 sm:px-6 lg:px-8">
          <Link href="/" className="flex items-center gap-2.5 text-[17px] font-bold tracking-tight" onClick={closeAll}>
            <AssuranceMark className="h-8 w-8" />
            {siteConfig.shortName}
          </Link>

          <nav aria-label="Main" className="hidden flex-1 items-center justify-center gap-1 lg:flex">
            {mainNav.map((entry) => {
              if ("href" in entry) {
                return (
                  <Link
                    key={entry.label}
                    href={entry.href}
                    aria-current={isActive(entry.href) ? "page" : undefined}
                    className={cn(
                      "rounded-lg px-3.5 py-2 text-sm font-medium transition-colors",
                      onDark ? "text-white/85 hover:text-white" : "text-ink/80 hover:text-ink",
                      isActive(entry.href) && (onDark ? "text-white" : "text-brand"),
                    )}
                    onMouseEnter={() => setOpenMenu(null)}
                  >
                    {entry.label}
                  </Link>
                );
              }
              const panelId = `${baseId}-${entry.label.replace(/\s+/g, "-").toLowerCase()}`;
              const expanded = openMenu === entry.label;
              return (
                <div key={entry.label} onMouseEnter={() => openWithHover(entry.label)}>
                  <button
                    type="button"
                    aria-expanded={expanded}
                    aria-controls={panelId}
                    onClick={() => {
                      const next = menuAfterClick(openMenu, entry.label, hoverOpenedAt.current, Date.now());
                      hoverOpenedAt.current = null;
                      setOpenMenu(next);
                    }}
                    className={cn(
                      "inline-flex items-center gap-1 rounded-lg border px-3.5 py-2 text-sm font-medium transition-colors",
                      expanded
                        ? "border-periwinkle/50 bg-navy-800 text-white"
                        : cn("border-transparent", onDark ? "text-white/85 hover:text-white" : "text-ink/80 hover:text-ink"),
                    )}
                  >
                    {entry.label}
                    <ChevronDown
                      className={cn("h-3.5 w-3.5 opacity-60 transition-transform", expanded && "rotate-180")}
                      aria-hidden="true"
                    />
                  </button>
                  {expanded ? <MegaPanel menu={entry.menu} id={panelId} onNavigate={closeAll} /> : null}
                </div>
              );
            })}
          </nav>

          <div className="hidden items-center gap-3 lg:flex">
            <Link
              href="/login"
              className={cn(
                "px-2 text-sm font-semibold transition-colors",
                onDark ? "text-white/85 hover:text-white" : "text-ink hover:text-brand",
              )}
            >
              Sign in
            </Link>
            <div className="hidden xl:block">
              <DemoButton variant={onDark ? "outlineOnDark" : "outline"} className="h-10 px-4 text-sm" />
            </div>
            <Link
              href="/signup"
              className={cn(
                "inline-flex h-10 items-center rounded-lg px-4 text-sm font-semibold transition-colors",
                onDark ? "bg-periwinkle text-navy-950 hover:bg-periwinkle-hover" : "bg-brand text-white hover:bg-brand-hover",
              )}
            >
              Start free
            </Link>
          </div>

          <button
            ref={toggleRef}
            type="button"
            className={cn(
              "inline-flex h-10 w-10 items-center justify-center rounded-lg lg:hidden",
              onDark ? "text-white hover:bg-white/10" : "text-ink hover:bg-mist",
            )}
            aria-expanded={mobileOpen}
            aria-controls={`${baseId}-mobile`}
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
            onClick={() => setMobileOpen((v) => !v)}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {mobileOpen ? (
        <div
          ref={drawerRef}
          id={`${baseId}-mobile`}
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
          className="fixed inset-0 z-50 flex flex-col overflow-y-auto bg-navy-950 px-4 pb-6 text-white lg:hidden"
        >
          <div className="flex h-16 shrink-0 items-center justify-between border-b border-white/10">
            <Link href="/" className="flex items-center gap-2.5 text-[17px] font-bold tracking-tight" onClick={closeAll}>
              <AssuranceMark className="h-8 w-8" />
              {siteConfig.shortName}
            </Link>
            <button
              type="button"
              className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-white hover:bg-white/10"
              aria-label="Close menu"
              onClick={() => {
                setMobileOpen(false);
                toggleRef.current?.focus();
              }}
            >
              <X className="h-5 w-5" />
            </button>
          </div>
          <nav aria-label="Mobile" className="flex-1 divide-y divide-white/5">
            {mainNav.map((entry) => {
              if ("href" in entry) {
                return (
                  <Link
                    key={entry.label}
                    href={entry.href}
                    className="flex items-center justify-between py-4 text-lg font-medium"
                    onClick={closeAll}
                  >
                    {entry.label}
                  </Link>
                );
              }
              const expanded = mobileSection === entry.label;
              const sectionId = `${baseId}-m-${entry.label.replace(/\s+/g, "-").toLowerCase()}`;
              return (
                <div key={entry.label}>
                  <button
                    type="button"
                    aria-expanded={expanded}
                    aria-controls={sectionId}
                    onClick={() => setMobileSection(expanded ? null : entry.label)}
                    className="flex w-full items-center justify-between py-4 text-left text-lg font-medium"
                  >
                    {entry.label}
                    <ChevronRight
                      className={cn("h-5 w-5 text-on-dark-muted transition-transform", expanded && "rotate-90")}
                      aria-hidden="true"
                    />
                  </button>
                  {expanded ? (
                    <ul id={sectionId} className="space-y-1 pb-4">
                      {entry.menu.items.map((item) => (
                        <li key={item.href}>
                          <MegaLink item={item} onNavigate={closeAll} />
                        </li>
                      ))}
                      <li className="px-2.5 pt-2">
                        <Link
                          href={entry.menu.overview.href}
                          onClick={closeAll}
                          className="inline-flex items-center gap-1.5 text-sm font-semibold text-periwinkle"
                        >
                          {entry.menu.overview.label}
                          <ArrowRight className="h-4 w-4" aria-hidden="true" />
                        </Link>
                      </li>
                    </ul>
                  ) : null}
                </div>
              );
            })}
          </nav>
          <div className="mt-6 [&_button]:h-12 [&_button]:w-full">
            <DemoButton />
          </div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Link
              href="/login"
              onClick={closeAll}
              className="inline-flex h-12 items-center justify-center rounded-lg border border-white/15 text-sm font-semibold"
            >
              Sign in
            </Link>
            <Link
              href="/signup"
              onClick={closeAll}
              className="inline-flex h-12 items-center justify-center rounded-lg bg-periwinkle text-sm font-semibold text-navy-950"
            >
              Start free
            </Link>
          </div>
        </div>
      ) : null}
    </header>
  );
}
