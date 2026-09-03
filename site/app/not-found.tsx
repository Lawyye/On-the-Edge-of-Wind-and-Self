import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="not-found">
      <h1>Бет табылмады</h1>
      <Link href="/">
        <button type="button">Негізгі бетке оралу</button>
      </Link>
    </div>
  );
}
