
import Image from 'next/image';

export default function Brand({
  className = '',
  onDark = false,
}: {
  className?: string;
  onDark?: boolean;
}) {
  const file = onDark
    ? 'getlancer-logo-on-dark.svg'
    : 'getlancer-logo.svg';

  return (
    <span
      className={`spectral-wordmark paper-wordmark ${className}`.trim()}
      role="img"
      aria-label="getLancer"
    >
      <Image
        src={`/brand/orbit/${file}`}
        alt=""
        width={168}
        height={33}
        unoptimized
        loading="eager"
      />
    </span>
  );
}
