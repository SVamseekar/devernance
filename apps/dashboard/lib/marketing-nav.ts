import {
  BookOpen,
  CalendarClock,
  CalendarDays,
  ClipboardCheck,
  Code2,
  Database,
  FileSearch,
  FileText,
  FlaskConical,
  Gavel,
  GitCompare,
  GitPullRequestArrow,
  HelpCircle,
  History,
  Landmark,
  Layers,
  ListChecks,
  Lock,
  Network,
  Newspaper,
  Package,
  Scale,
  ScanSearch,
  ShieldCheck,
  Workflow,
} from "lucide-react";
import type { ComponentType } from "react";

import { GithubIcon } from "@/components/marketing/brand-icons";
import { siteConfig } from "@/lib/site-config";

/** Icon tile colours used by the mega menus (design board items 4–7). */
export type IconTone = "blue" | "violet" | "red" | "green" | "amber" | "orange" | "slate" | "indigo";

export type MegaItem = {
  href: string;
  label: string;
  description: string;
  icon: ComponentType<{ className?: string }>;
  tone: IconTone;
  external?: boolean;
};

export type MegaMenu = {
  label: string;
  /** Left-column heading and blurb. */
  heading: string;
  blurb: string;
  overview: { href: string; label: string };
  items: MegaItem[];
};

export type NavEntry = { label: string; href: string } | { label: string; menu: MegaMenu };

const productMenu: MegaMenu = {
  label: "Product",
  heading: "Product",
  blurb: "Everything you need to assess, document and release AI features.",
  overview: { href: "/product", label: "View product overview" },
  items: [
    { href: "/product#registry", label: "AI System Registry", description: "Register and manage systems", icon: Layers, tone: "blue" },
    { href: "/product#mapping", label: "Risk & Control Mapping", description: "EU AI Act controls, proposals you accept", icon: Network, tone: "violet" },
    { href: "/product/evidence", label: "Evidence Management", description: "Upload, search and map evidence", icon: FileSearch, tone: "red" },
    { href: "/product/evaluations", label: "Evaluations", description: "Connect your eval results", icon: FlaskConical, tone: "slate" },
    { href: "/product/data-contracts", label: "Data Contracts", description: "Monitor schema and drift", icon: Database, tone: "green" },
    { href: "/product#approvals", label: "Approvals", description: "Multi-stage sign-off", icon: ClipboardCheck, tone: "indigo" },
    { href: "/product/release-gate", label: "Release Gate", description: "CI/CD enforcement", icon: GitPullRequestArrow, tone: "orange" },
    { href: "/how-it-works", label: "How it works", description: "From register to release decision", icon: Workflow, tone: "violet" },
  ],
};

const euAiActMenu: MegaMenu = {
  label: "EU AI Act",
  heading: "EU AI Act",
  blurb: "Turn regulatory requirements into practical actions.",
  overview: { href: "/eu-ai-act", label: "Explore EU AI Act" },
  items: [
    { href: "/eu-ai-act", label: "Overview", description: "What the EU AI Act asks of AI features", icon: Landmark, tone: "violet" },
    { href: "/eu-ai-act#deadlines", label: "Key deadlines", description: "Dates and timelines, with sources", icon: CalendarClock, tone: "blue" },
    { href: "/tools/ai-act-check", label: "Free applicability check", description: "Which duties may apply, no sign-up", icon: ListChecks, tone: "green" },
    { href: "/tools/ai-act-deadlines", label: "Deadline calendar", description: "Every date, with an .ics download", icon: CalendarDays, tone: "orange" },
    { href: "/eu-ai-act#articles", label: "Article guides", description: "Key articles and the evidence behind them", icon: BookOpen, tone: "amber" },
    { href: "/method", label: "Methodology", description: "How we map and interpret", icon: ScanSearch, tone: "slate" },
  ],
};

const solutionsMenu: MegaMenu = {
  label: "Solutions",
  heading: "Solutions",
  blurb: "Built for teams that ship and assure AI.",
  overview: { href: "/who-its-for", label: "View all solutions" },
  items: [
    { href: "/who-its-for#engineering", label: "Engineering", description: "Build with confidence", icon: Code2, tone: "blue" },
    { href: "/who-its-for#compliance", label: "Compliance", description: "Turn obligations into actions", icon: ShieldCheck, tone: "green" },
    { href: "/who-its-for#legal", label: "Legal", description: "Maintain accountability", icon: Scale, tone: "orange" },
    { href: "/who-its-for#data", label: "Data", description: "Ensure data quality and contracts", icon: Database, tone: "slate" },
    { href: "/who-its-for#product", label: "Product owners", description: "Ship responsible AI features", icon: Package, tone: "indigo" },
    { href: "/who-its-for#audit", label: "Audit", description: "Get audit-ready", icon: Gavel, tone: "slate" },
  ],
};

const resourcesMenu: MegaMenu = {
  label: "Resources",
  heading: "Resources",
  blurb: "Guides, documentation and updates.",
  overview: { href: "/blog", label: "View all resources" },
  items: [
    { href: "/docs", label: "Docs", description: "Quickstart, API and evidence packs", icon: FileText, tone: "blue" },
    { href: "/blog", label: "Blog / Updates", description: "News and product updates", icon: Newspaper, tone: "red" },
    { href: "/changelog", label: "Changelog", description: "What shipped, release by release", icon: History, tone: "violet" },
    { href: "/security", label: "Security", description: "How we protect your data", icon: Lock, tone: "green" },
    { href: "/compare/release-gates-vs-document-generators", label: "Compare", description: "Release gates and other approaches", icon: GitCompare, tone: "amber" },
    { href: "/faq", label: "FAQ", description: "Dates, risk class, evidence packs", icon: HelpCircle, tone: "indigo" },
    { href: siteConfig.evgraphUrl, label: "GitHub / evgraph", description: "Open source and CLI", icon: GithubIcon, tone: "slate", external: true },
  ],
};

export const mainNav: NavEntry[] = [
  { label: "Product", menu: productMenu },
  { label: "EU AI Act", menu: euAiActMenu },
  { label: "Solutions", menu: solutionsMenu },
  { label: "Resources", menu: resourcesMenu },
  { label: "Pricing", href: "/pricing" },
];

export type FooterColumn = { title: string; links: { href: string; label: string; external?: boolean }[] };

export const footerColumns: FooterColumn[] = [
  {
    title: "Product",
    links: [
      { href: "/product", label: "Product overview" },
      ...productMenu.items.filter(({ href }) => !href.includes("#")).map(({ href, label }) => ({ href, label })),
      { href: "/pricing", label: "Pricing" },
    ],
  },
  {
    title: "Solutions",
    links: solutionsMenu.items.map(({ href, label }) => ({ href, label })),
  },
  {
    title: "Resources",
    links: [
      { href: "/docs", label: "Docs" },
      { href: "/blog", label: "Blog / Updates" },
      { href: "/changelog", label: "Changelog" },
      { href: "/eu-ai-act", label: "EU AI Act" },
      { href: "/tools/ai-act-check", label: "Free AI Act check" },
      { href: "/tools/ai-act-deadlines", label: "Deadline calendar" },
      { href: "/method", label: "Methodology" },
      { href: "/faq", label: "FAQ" },
    ],
  },
  {
    title: "Company",
    links: [
      { href: "/request-demo", label: "Request demo" },
      { href: "/contact", label: "Contact" },
      { href: "/security", label: "Security" },
      { href: "/subprocessors", label: "Subprocessors" },
      ...(siteConfig.statusUrl ? [{ href: siteConfig.statusUrl, label: "Status", external: true }] : []),
      { href: siteConfig.githubUrl, label: "GitHub", external: true },
      { href: siteConfig.evgraphUrl, label: "evgraph", external: true },
    ],
  },
];

export const legalLinks = [
  { href: "/privacy", label: "Privacy" },
  { href: "/terms", label: "Terms" },
  { href: "/dpa", label: "DPA" },
  { href: "/msa", label: "MSA" },
  { href: "/order-form", label: "Order Form" },
  { href: "/refunds", label: "Refunds" },
  { href: "/disclaimer", label: "Disclaimer" },
] as const;

export const iconToneClass: Record<IconTone, string> = {
  blue: "bg-[#2f6bff] text-white",
  violet: "bg-[#7c4dff] text-white",
  red: "bg-[#ef4444] text-white",
  green: "bg-[#16a34a] text-white",
  amber: "bg-[#f59e0b] text-white",
  orange: "bg-[#f97316] text-white",
  slate: "bg-[#3a4466] text-white",
  indigo: "bg-[#4f46e5] text-white",
};
