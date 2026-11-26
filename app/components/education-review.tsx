
'use client';

import {
  useCallback,
  useEffect,
  useEffectEvent,
  useRef,
  useState,
} from 'react';

import Link from 'next/link';
import { api, accountApi } from '@/lib/api';
import { downloadTemplateSource } from '@/lib/templates';

import {
  educationEvidence,
  educationLabel,
  educationModeLabel,
  type EducationRelease,
} from '@/lib/education';

import EducationPackage from './education-package';

function ReviewCard({
  release,
  onReviewed,
  onDenied,
  accountId,
  authority,
}: {
  release: EducationRelease;
  onReviewed: (value: EducationRelease) => void;
  onDenied: (error: Error) => void;
  accountId: string;
  authority: () => boolean;
}) {
  const mounted = useRef(true);
  const operation = useRef(0);

  const invalidateOperation = useCallback(() => {
    operation.current++;
  }, []);

  useEffect(() => {
    mounted.current = true;

    return () => {
      mounted.current = false;
      invalidateOperation();
    };
  }, [invalidateOperation]);

  const [reason, setReason] = useState('');

  const [decision, setDecision] = useState(
    release.status === 'PENDING'
      ? 'APPROVE'
      : 'SUSPEND'
  );

  const [rights, setRights] = useState(false);
  const [inspected, setInspected] = useState(false);

  const [packageReviewed, setPackageReviewed] =
    useState(false);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const summary = useRef<HTMLParagraphElement>(null);
  const s = release.snapshot;
  const binding = s.sourceBinding;

  useEffect(() => {
    if (error) {
      summary.current?.focus();
    }
  }, [error]);

  function failed(problem: unknown) {
    if (
      [401, 403].includes(
        (problem as { status?: number }).status || 0
      )
    ) {
      if (authority()) {
        onDenied(problem as Error);
      }
    } else if (
      mounted.current &&
      authority()
    ) {
      setError((problem as Error).message);
    }
  }

  async function inspect() {
    if (!binding) return;

    const current = ++operation.current;

    const active = () =>
      mounted.current &&
      authority() &&
      current === operation.current;

    setBusy(true);
    setError('');

    try {
      const path =
        s.mode === 'PAID'
          ? '/admin/templates/' +
            binding.templateId +
            '/versions/' +
            binding.versionId +
            '/package'
          : '/admin/education-releases/' +
            release.id +
            '/package';

      await downloadTemplateSource(
        path,
        'education-review-' + release.id + '.zip',
        s.mode === 'FREE' ? 'POST' : 'GET',
        {
          accountId,
          isCurrent: active,
        }
      );

      if (active()) {
        setInspected(true);
      }
    } catch (problem) {
      failed(problem);
    } finally {
      if (active()) {
        setBusy(false);
      }
    }
  }

  async function review() {
    const current = ++operation.current;

    const active = () =>
      mounted.current &&
      authority() &&
      current === operation.current;

    setBusy(true);
    setError('');

    try {
      const value = await accountApi<EducationRelease>(
        accountId,
        '/admin/education-releases/' +
          release.id +
          '/review',
        {
          method: 'POST',
          body: JSON.stringify({
            revision: release.revision,
            sourceHash: release.sourceHash,
            decision,
            reason,
            rightsReviewed: rights,
            packageReviewed,
          }),
        }
      );

      if (active()) {
        onReviewed(value);
      }
    } catch (problem) {
      failed(problem);
    } finally {
      if (active()) {
        setBusy(false);
      }
    }
  }

  return (
    <article className="kit-record">
      <div className="kit-badges">
        <span>
          {educationModeLabel(s.mode)}
        </span>

        <span>
          {educationLabel(release.status)}
        </span>

        <span>
          {educationLabel(s.category)}
        </span>
      </div>

      <h2>{s.title}</h2>

      <p>
        Release {release.id} · Revision{' '}
        {release.revision}
      </p>

      <p className="kit-source-hash">
        Frozen content SHA-256:{' '}
        {release.sourceHash || 'Not submitted'}
      </p>

      <p>{s.summary}</p>

      <p>
        Existing contribution: {s.contribution}
      </p>

      <details>
        <summary style={{ minHeight: 44 }}>
          Inspect category evidence and private academic details
        </summary>

        <dl>
          {(educationEvidence[s.category] || []).map(
            ([key, label]) => (
              <div
                style={{ marginBottom: 16 }}
                key={key}
              >
                <dt style={{ fontWeight: 600 }}>
                  {label}
                </dt>

                <dd
                  style={{
                    margin: 0,
                    whiteSpace: 'pre-wrap',
                    overflowWrap: 'anywhere',
                  }}
                >
                  {s.categoryEvidence[key] ||
                    'Not supplied.'}
                </dd>
              </div>
            )
          )}
        </dl>

        <p>
          Academic details:{' '}
          {[
            release.academic.institution,
            release.academic.academicYear,
            release.academic.branch,
          ]
            .filter(Boolean)
            .join(' · ') || 'Not supplied.'}
          {' · '}
          {release.academic.shareAcademicDetails
            ? 'Current public consent'
            : 'Private — do not publish'}
        </p>

        <p>
          Demo: {educationLabel(s.demoMode)}
          {' · '}
          {release.demoAvailability ||
            'Inspect current eligibility'}
        </p>

        {s.componentLinks.map(link => (
          <p
            style={{
              overflowWrap: 'anywhere',
            }}
            key={
              link.componentId +
              ':' +
              link.revision
            }
          >
            Component {link.componentId}:
            {link.revision} · {link.attribution}
            {' · '}
            {link.license}
          </p>
        ))}
      </details>

      <EducationPackage
        evidence={s.dataAiEvidence}
        category={s.category}
        pack={s.package}
        binding={binding}
        showcase={s.mode === 'SHOWCASE'}
      />

      {binding && (
        <button
          className="button"
          disabled={busy}
          onClick={() => void inspect()}
        >
          Inspect private source ZIP
        </button>
      )}

      {error && (
        <p
          className="error"
          role="alert"
          tabIndex={-1}
          ref={summary}
        >
          {error}
        </p>
      )}

      {release.reviewReason && (
        <p>
          Private review reason: {release.reviewReason}
        </p>
      )}

      {['PENDING', 'APPROVED'].includes(
        release.status
      ) && (
        <form
          className="form"
          onSubmit={event => {
            event.preventDefault();
            void review();
          }}
        >
          <label
            htmlFor={
              'education-decision-' + release.id
            }
          >
            Review decision
          </label>

          <select
            id={'education-decision-' + release.id}
            disabled={busy}
            value={decision}
            onChange={event =>
              setDecision(event.target.value)
            }
          >
            {release.status === 'PENDING' && (
              <>
                <option value="APPROVE">
                  Approve frozen release
                </option>

                <option value="CHANGES_REQUESTED">
                  Request a corrected release
                </option>
              </>
            )}

            <option value="SUSPEND">
              Suspend current release
            </option>
          </select>

          <label
            htmlFor={
              'education-reason-' + release.id
            }
          >
            Private decision reason
          </label>

          <textarea
            id={'education-reason-' + release.id}
            required
            minLength={20}
            maxLength={2000}
            disabled={busy}
            value={reason}
            onChange={event =>
              setReason(event.target.value)
            }
          />

          <label
            className="kit-consent"
            style={{
              minHeight: 44,
              alignItems: 'center',
            }}
          >
            <input
              type="checkbox"
              checked={rights}
              disabled={busy}
              onChange={event =>
                setRights(event.target.checked)
              }
            />

            I reviewed source rights, accepted
            contribution and attribution for
            this exact release.
          </label>

          <label
            className="kit-consent"
            style={{
              minHeight: 44,
              alignItems: 'center',
            }}
          >
            <input
              type="checkbox"
              checked={packageReviewed}
              disabled={
                busy ||
                (s.mode === 'FREE' && !inspected)
              }
              onChange={event =>
                setPackageReviewed(
                  event.target.checked
                )
              }
            />

            I inspected the package, license,
            evidence and included / excluded
            assets at this hash.
          </label>

          <button
            className="button primary"
            disabled={
              busy ||
              !release.sourceHash ||
              reason.trim().length < 20 ||
              (
                decision === 'APPROVE' &&
                (
                  !rights ||
                  !packageReviewed ||
                  (
                    s.mode === 'FREE' &&
                    !inspected
                  )
                )
              )
            }
          >
            Apply exact revision & hash decision
          </button>
        </form>
      )}
    </article>
  );
}

export default function EducationReview() {
  const [items, setItems] =
    useState<EducationRelease[]>([]);

  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);

  const [session, setSession] = useState<{
    accountId: string | null;
    epoch: number;
  }>({
    accountId: null,
    epoch: 0,
  });

  const generation = useRef(0);
  const authority = useRef(0);
  const mounted = useRef(true);
  const account = useRef<string | null>(null);
  const summary = useRef<HTMLParagraphElement>(null);

  const invalidateReviewQueue = useCallback(() => {
    generation.current++;
    authority.current++;
  }, []);

  useEffect(() => {
    if (error) {
      summary.current?.focus();
    }
  }, [error]);

  function clearPrivate(problem: Error) {
    generation.current++;
    authority.current++;

    account.current = null;

    setSession({
      accountId: null,
      epoch: authority.current,
    });

    setItems([]);
    setMessage('');
    setHasMore(false);
    setLoading(false);

    setError(
      problem.message ||
      'Operator access or recent MFA is no longer current.'
    );
  }

  async function load(next = 0) {
    let current = ++generation.current;

    setLoading(true);
    setError('');

    try {
      let actor = account.current;

      if (!next) {
        const user = await api('/me');

        if (
          !mounted.current ||
          current !== generation.current
        ) {
          return;
        }

        if (actor && actor !== user.id) {
          clearPrivate(
            new Error('Your account changed.')
          );

          current = ++generation.current;

          setLoading(true);
          setError('');
        }

        actor = user.id;
      }

      const result = await accountApi(
        actor,
        '/admin/education-releases?page=' +
          next +
          '&size=20'
      );

      if (
        !mounted.current ||
        current !== generation.current
      ) {
        return;
      }

      account.current = actor;

      setSession({
        accountId: actor,
        epoch: authority.current,
      });

      setItems(old =>
        next
          ? [...old, ...result.items]
          : result.items
      );

      setPage(next);
      setHasMore(result.hasMore);
    } catch (problem) {
      if (
        mounted.current &&
        current === generation.current
      ) {
        clearPrivate(problem as Error);
      }
    } finally {
      if (
        mounted.current &&
        current === generation.current
      ) {
        setLoading(false);
      }
    }
  }

  const loadReviewQueue = useEffectEvent(() => {
    void load();
  });

  useEffect(() => {
    let active = true;

    mounted.current = true;

    queueMicrotask(() => {
      if (active) {
        loadReviewQueue();
      }
    });

    return () => {
      active = false;
      mounted.current = false;

      invalidateReviewQueue();
    };
  }, [invalidateReviewQueue]);

  const epoch = session.epoch;

  const isCurrent = () =>
    mounted.current &&
    epoch === authority.current;

  return (
    <>
      <header className="kit-detail-head">
        <div>
          <span className="eyebrow">
            Private operator review
          </span>

          <h1>Education release review.</h1>

          <p>
            Inspect exact source, category evidence,
            rights and license. Current administrator
            permission and recent MFA are required.
            A FREE package inspection is audited
            before approval.
          </p>
        </div>

        <button
          className="button"
          disabled={loading}
          onClick={() => void load()}
        >
          Refresh review queue
        </button>
      </header>

      {error && (
        <p
          role="alert"
          tabIndex={-1}
          className="error"
          ref={summary}
        >
          {error}
        </p>
      )}

      {error && (
        <p>
          No private queue data is shown.{' '}

          <Link
            className="button"
            href="/login"
          >
            Sign in and verify administrator MFA
          </Link>
        </p>
      )}

      {message && (
        <p
          role="status"
          className="kit-notice"
        >
          {message}
        </p>
      )}

      {loading ? (
        <p role="status">
          Loading private review queue…
        </p>
      ) : items.length ? (
        <div className="kit-admin-list">
          {items.map(release => (
            <ReviewCard
              release={release}
              key={
                release.id +
                ':' +
                release.revision +
                ':' +
                release.status +
                ':' +
                release.sourceHash
              }
              accountId={session.accountId!}
              authority={isCurrent}
              onDenied={problem => {
                if (isCurrent()) {
                  clearPrivate(problem);
                }
              }}
              onReviewed={value => {
                if (!isCurrent()) return;

                setItems(old =>
                  old.map(item =>
                    item.id === value.id
                      ? value
                      : item
                  )
                );

                setMessage(
                  'Reviewed release is now ' +
                  educationLabel(
                    value.status
                  ).toLowerCase() +
                  '. Current eligibility still governs public source access.'
                );
              }}
            />
          ))}
        </div>
      ) : (
        !error && (
          <section className="kit-empty">
            <h2>No releases await review.</h2>

            <p>
              Submitted releases appear here
              with their exact content revision
              and hash.
            </p>
          </section>
        )
      )}

      {hasMore && (
        <button
          className="button"
          disabled={loading}
          onClick={() => void load(page + 1)}
        >
          Load more private reviews
        </button>
      )}
    </>
  );
}
