
'use client';

import { useEffect, useState } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { api } from '@/lib/api';
import { safeHostedUrl } from '@/lib/hosting';

type PublicDemo = {
  id: string;
  title: string;
  version: string;
  url: string;
  expiresAt: string;
};

type HostedDemosProps = {
  productId: string;
};

export default function HostedDemos({
  productId,
}: HostedDemosProps) {
  return (
    <HostedDemosContent
      key={productId}
      productId={productId}
    />
  );
}

function HostedDemosContent({
  productId,
}: HostedDemosProps) {
  const [items, setItems] = useState<PublicDemo[]>([]);

  useEffect(() => {
    let current = true;

    const controller = new AbortController();

    let expiryTimer:
      | ReturnType<typeof setTimeout>
      | undefined;

    function updateEligible(demos: PublicDemo[]) {
      if (!current) return;

      if (expiryTimer !== undefined) {
        clearTimeout(expiryTimer);
        expiryTimer = undefined;
      }

      const now = Date.now();

      const eligible = demos.filter((item) => {
        const expiresAt = Date.parse(item.expiresAt);

        return (
          Boolean(safeHostedUrl(item.url)) &&
          Number.isFinite(expiresAt) &&
          expiresAt > now
        );
      });

      setItems(eligible);

      if (eligible.length > 0) {
        const nextExpiry = Math.min(
          ...eligible.map((item) =>
            Date.parse(item.expiresAt)
          )
        );

        const delay = Math.min(
          Math.max(nextExpiry - now, 1),
          2_147_483_647
        );

        expiryTimer = setTimeout(() => {
          updateEligible(eligible);
        }, delay);
      }
    }

    api(
      '/hosting/products/' +
        encodeURIComponent(productId),
      { signal: controller.signal }
    )
      .then((result) => {
        if (!current) return;

        const demos: PublicDemo[] = Array.isArray(
          result.items
        )
          ? result.items
          : [];

        updateEligible(demos);
      })
      .catch(() => {
        if (current && !controller.signal.aborted) {
          setItems([]);
        }
      });

    return () => {
      current = false;

      controller.abort();

      if (expiryTimer !== undefined) {
        clearTimeout(expiryTimer);
      }
    };
  }, [productId]);

  if (!items.length) return null;

  return (
    <section
      className="panel"
      aria-label="Hosted static frontends"
    >
      <h2>Hosted frontend demos</h2>

      <p>
        Reviewed built frontend files on a separate
        demo origin.
      </p>

      {items.map((item) => (
        <div className="stack" key={item.id}>
          <a
            className="button"
            href={safeHostedUrl(item.url)}
            target="_blank"
            rel="noopener noreferrer"
            referrerPolicy="no-referrer"
          >
            Open {item.title}{' '}
            <ArrowUpRight
              size={16}
              aria-hidden="true"
            />
          </a>

          <p className="muted">
            Version {item.version} · Expires{' '}
            {new Date(
              item.expiresAt
            ).toLocaleDateString('en-GB')}
          </p>
        </div>
      ))}
    </section>
  );
}
