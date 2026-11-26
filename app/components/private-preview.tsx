
'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import ExternalLink from '@/app/components/external-link';
import { api } from '@/lib/api';

type ProjectMedia = {
  id: string;
  url: string;
  alt: string | null;
};

type PrivateProject = {
  title: string;
  summary: string | null;
  description: string | null;
  contribution: string | null;
  media: ProjectMedia[];
  liveUrl?: string | null;
};

type PrivatePreviewProps = {
  id: string;
};

export default function PrivatePreview({
  id,
}: PrivatePreviewProps) {
  return <PreviewForProject key={id} id={id} />;
}

function PreviewForProject({
  id,
}: PrivatePreviewProps) {
  const [project, setProject] =
    useState<PrivateProject | null>(null);

  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    api('/private/products/' + encodeURIComponent(id))
      .then((value: PrivateProject) => {
        if (active) {
          setProject(value);
        }
      })
      .catch((problem: unknown) => {
        if (active) {
          setError(
            problem instanceof Error
              ? problem.message
              : 'Private preview is unavailable.'
          );
        }
      });

    return () => {
      active = false;
    };
  }, [id]);

  return (
    <main id="main" className="wrap prose">
      <Link href="/workspace">← Workspace</Link>

      <p className="eyebrow">Private preview</p>

      {error ? (
        <div role="alert">
          <p>{error}</p>

          <Link href="/login">
            Log in with the email your builder authorized
          </Link>
        </div>
      ) : !project ? (
        <p role="status">Checking access…</p>
      ) : (
        <>
          <h1>{project.title}</h1>

          <p>{project.summary}</p>
          <p>{project.description}</p>

          <h2>Builder contribution</h2>

          <p>{project.contribution}</p>

          {project.media.map((media) => (
            <Image
              key={media.id}
              src={media.url}
              alt={media.alt ?? ''}
              width={1200}
              height={675}
              unoptimized
              loading="lazy"
              style={{
                width: '100%',
                height: 'auto',
              }}
            />
          ))}

          {project.liveUrl && (
            <ExternalLink
              href={project.liveUrl}
              rel="noopener noreferrer"
              target="_blank"
            >
              Open external demo ↗
            </ExternalLink>
          )}

          <p className="muted">
            Shared privately for review. Access expires
            automatically. External demos have their own
            access controls.
          </p>
        </>
      )}
    </main>
  );
}
