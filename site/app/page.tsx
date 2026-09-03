import Shell from '@/components/Shell';
import { getContent } from '@/lib/content';
import { assetUrl } from '@/lib/assets';

export const dynamic = 'force-dynamic';

export default async function HomePage() {
  const content = await getContent();
  const { site, home } = content;

  return (
    <Shell content={content}>
      <div className="hero" style={{ backgroundImage: `url(${assetUrl(site.heroImage)})` }}>
        <div className="hero-shade" />
        <div className="billboard">
          <div className="billboard-title">{home.billboardTitle}</div>
          <div className="billboard-subtitle">{home.billboardSubtitle}</div>
        </div>
      </div>

      <section className="home-intro">
        <h1>{home.intro}</h1>
      </section>

      <section className="mission-section content-width">
        <p>{home.mission}</p>
      </section>

      <section className="goal-section home-split content-width">
        <img src={assetUrl(home.goalImage)} alt="" />
        <div>
          <h2 className="lined-title">{home.goalTitle}</h2>
          <p className="body-copy justified">{home.goal}</p>
        </div>
      </section>

      <section className="list-section content-width">
        <h2 className="section-title">{home.tasksTitle}</h2>
        <div className="home-split list-grid">
          <div className="image-accent">
            <img src={assetUrl(home.tasksImage)} alt="" />
            <span />
          </div>
          <ul className="source-list">
            {home.tasks.map((task, i) => (
              <li key={i}>{task}</li>
            ))}
          </ul>
        </div>
      </section>

      <section className="result-section content-width">
        <h2 className="section-title result-title">{home.resultsTitle}</h2>
        <div className="home-split list-grid">
          <div className="image-accent">
            <img src={assetUrl(home.resultsImage)} alt="" />
            <span />
          </div>
          <ul className="source-list">
            {home.results.map((result, i) => (
              <li key={i}>{result}</li>
            ))}
          </ul>
        </div>
      </section>

      <footer className="home-footer teal-section content-width">
        {home.footerLines.map((line, i) => (
          <p key={i}>{line}</p>
        ))}
      </footer>
    </Shell>
  );
}
