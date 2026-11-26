
'use client';

import { useSyncExternalStore } from 'react';
import { analyticsConsent } from '@/lib/analytics';

const CONSENT_KEY = 'getlancer.analytics';
const CONSENT_EVENT = 'getlancer:analytics-consent';

function subscribe(callback: () => void) {
  function handleStorage(event: StorageEvent) {
    if (event.key === CONSENT_KEY || event.key === null) {
      callback();
    }
  }

  window.addEventListener('storage', handleStorage);
  window.addEventListener(CONSENT_EVENT, callback);

  return () => {
    window.removeEventListener('storage', handleStorage);
    window.removeEventListener(CONSENT_EVENT, callback);
  };
}

function getSnapshot(): boolean {
  try {
    return analyticsConsent();
  } catch {
    return false;
  }
}

function getServerSnapshot(): boolean {
  return false;
}

export default function AnalyticsPreference() {
  const enabled = useSyncExternalStore(
    subscribe,
    getSnapshot,
    getServerSnapshot
  );

  function updateConsent(value: boolean) {
    try {
      localStorage.setItem(
        CONSENT_KEY,
        value ? 'yes' : 'no'
      );

      if (!value) {
        sessionStorage.removeItem(
          'getlancer.analytics-session'
        );

        sessionStorage.removeItem(
          'getlancer.source'
        );
      }
    } catch {
      // Storage may be unavailable.
    } finally {
      window.dispatchEvent(new Event(CONSENT_EVENT));
    }
  }

  return (
    <details className="wrap" style={{ paddingBlock: 16 }}>
      <summary>Privacy preferences</summary>

      <p>
        Optional first-party analytics help us understand
        which searches lead to useful connections. We do
        not include inquiry text or email addresses.
        Essential account and engagement records are
        kept separately.
      </p>

      <label>
        <input
          type="checkbox"
          checked={enabled}
          onChange={(event) =>
            updateConsent(event.target.checked)
          }
        />
        {' '}Allow optional usage analytics
      </label>
    </details>
  );
}
