export default function PlaceholderPage({ description, title }) {
  return (
    <section className="placeholder-page">
      <div className="placeholder-icon" aria-hidden="true">↗</div>
      <span className="section-kicker">EN PREPARACIÓN</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </section>
  );
}
