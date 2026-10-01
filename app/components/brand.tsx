export default function Brand({ className = '' }: { className?: string }) {
  return <span className={`spectral-wordmark ${className}`} aria-label="getLancer">get<span>Lancer</span><i aria-hidden="true" /></span>;
}
