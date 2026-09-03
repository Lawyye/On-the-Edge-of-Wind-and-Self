import type { Metadata, Viewport } from 'next';
import './site.css';
import './portal.css';

export const metadata: Metadata = {
  title: '"ORLEU" Mangistau KDI',
  description: 'Педагог мәртебесі: кәсіби өсу және құзыреттілік — онлайн кәсіби қолдау жобасы',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="kk">
      <head>
        {/*
          Fonts are linked rather than bundled so the build never depends on
          reaching Google's servers; if the link fails the stacks in site.css
          fall back to Georgia/Arial and the layout still holds.
        */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Bitter:wght@400;700&family=Montserrat:wght@400;700&family=Nunito:wght@600;700&display=swap"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
