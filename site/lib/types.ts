export type EventLink = {
  label: string;
  url: string;
};

export type SiteEvent = {
  route: string;
  month: string;
  monthLabel: string;
  title: string;
  date: string;
  format: string;
  responsible: string;
  completion: string;
  materialsHeading: string;
  image: string;
  links: EventLink[];
  originalUrl?: string;
};

export type Curator = {
  name: string;
  description: string;
  image: string;
  barImage: string;
  accent: string;
};

export type SiteMeta = {
  title: string;
  logo: string;
  homeRoute: string;
  heroImage: string;
  eventHeroImage: string;
  colors: Record<string, string>;
};

export type HomeContent = {
  route: string;
  billboardTitle: string;
  billboardSubtitle: string;
  intro: string;
  mission: string;
  goalTitle: string;
  goal: string;
  goalImage: string;
  tasksTitle: string;
  tasks: string[];
  tasksImage: string;
  resultsTitle: string;
  results: string[];
  resultsImage: string;
  footerLines: string[];
};

export type CuratorsPage = {
  route: string;
  title: string;
  curators: Curator[];
  footerTitle: string;
  footerImage: string;
  documentTitle: string;
  documentUrl: string;
};

export type SiteContent = {
  site: SiteMeta;
  home: HomeContent;
  curatorsPage: CuratorsPage;
  events: SiteEvent[];
};

export type SubmissionStatus = 'pending' | 'published' | 'rejected';

export type Submission = {
  id: string;
  created_at: string;
  full_name: string;
  organization: string | null;
  region: string;
  event_route: string | null;
  title: string;
  description: string | null;
  file_path: string | null;
  file_name: string | null;
  file_size: number | null;
  mime: string | null;
  status: SubmissionStatus;
};

/** Public shape — deliberately omits ip_hash and anything not meant for visitors. */
export type PublicSubmission = Omit<Submission, 'file_path'> & {
  file_url: string | null;
};

export type PortalSettings = {
  /** When false the form still renders but refuses to accept files. */
  submissions_open: boolean;
  /**
   * When true a new document appears publicly right away. Off by default: the
   * curator asked for review-before-publish so strangers cannot post to the site.
   */
  auto_publish: boolean;
};
