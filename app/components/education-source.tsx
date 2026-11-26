
'use client';

import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import Link from 'next/link';
import { api } from '@/lib/api';

import {
  downloadTemplateSource,
  loadTemplateCheckout,
  templatePrice,
  templateStatus,
  type TemplateOrder,
} from '@/lib/templates';

import {
  type EducationOffer,
  type EducationRelease,
} from '@/lib/education';

import EducationPackage from './education-package';

type EducationSourceProps = {
  release: EducationRelease;
  preview?: boolean;
  previewPackageUrl?: string | null;
};

export default function EducationSource(
  props: EducationSourceProps
) {
  const { release, preview = false } = props;

  const releaseKey = JSON.stringify([
    release.id,
    release.productId,
    release.sourceHash,
    preview,
  ]);

  return (
    <EducationSourceContent
      key={releaseKey}
      {...props}
    />
  );
}

function EducationSourceContent({
  release,
  preview = false,
  previewPackageUrl,
}: EducationSourceProps) {
  const [remoteOffer, setOffer] =
    useState<EducationOffer | null>(null);

  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [consent, setConsent] = useState(false);

  const [order, setOrder] =
    useState<TemplateOrder | null>(null);

  const key = useRef('');
  const alive = useRef(true);
  const generation = useRef(0);
  const pending = useRef(false);
  const checkoutGeneration = useRef(0);

  const gatewayRef =
    useRef<{ close?: () => void } | null>(null);

  const summary =
    useRef<HTMLParagraphElement>(null);

  const {
    id: releaseId,
    productId,
    sourceHash,
  } = release;

  const offer: EducationOffer | null = preview
    ? {
        release,
        package: release.snapshot.sourceBinding,
        available: release.snapshot.mode === 'FREE',
        checkoutAvailable: false,
        reason:
          'Illustrative source offer. No merchant checkout is available.',
      }
    : remoteOffer;

  const invalidateCheckout = useCallback(() => {
    checkoutGeneration.current++;

    const previous = gatewayRef.current;
    gatewayRef.current = null;

    try {
      previous?.close?.();
    } catch {
      /* Obsolete callbacks remain invalid
         if the SDK cannot close. */
    }
  }, []);

  useEffect(() => {
    const generationRef = generation;

    alive.current = true;

    const current = ++generationRef.current;

    if (!preview) {
      api(
        '/education-releases/' +
          releaseId +
          '/source-offer'
      )
        .then((value) => {
          if (
            value.release?.id !== releaseId ||
            value.release?.productId !== productId ||
            value.release?.sourceHash !== sourceHash
          ) {
            throw new Error(
              'The source offer did not match this reviewed release. Refresh its current state.'
            );
          }

          if (
            alive.current &&
            current === generationRef.current
          ) {
            setOffer(value);
          }
        })
        .catch((problem: unknown) => {
          if (
            alive.current &&
            current === generationRef.current
          ) {
            setOffer(null);

            setError(
              problem instanceof Error
                ? problem.message
                : 'Unable to load the source offer.'
            );
          }
        });
    }

    return () => {
      alive.current = false;
      generationRef.current++;
      invalidateCheckout();
    };
  }, [
    releaseId,
    productId,
    sourceHash,
    preview,
    invalidateCheckout,
  ]);

  useEffect(() => {
    if (error) {
      summary.current?.focus();
    }
  }, [error]);

