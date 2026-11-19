export default function Brand({ className = '', onDark = false }: { className?: string; onDark?: boolean }) {
  const file = onDark ? 'getlancer-logo-on-dark.svg' : 'getlancer-logo.svg';

  return <span className={`spectral-wordmark paper-wordmark ${className}`.trim()} role="img" aria-label="getLancer">
    <img src={`/brand/orbit/${file}`} alt="" width={2494} height={490} />
  </span>;
}
