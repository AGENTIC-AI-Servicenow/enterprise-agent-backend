'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

function OAuthCallbackHandler() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [message, setMessage] = useState('Processing authentication...');

  useEffect(() => {
    // next/navigation's useSearchParams can change identity and re-trigger effects.
    // Also, some providers may return userId as "userId" while other flows may use "userid".
    const userId = searchParams.get('userId') || searchParams.get('userid');
    const success = searchParams.get('success');
    const error = searchParams.get('error');
    const details = searchParams.get('details');

    if (error) {
      setStatus('error');
      setMessage(`Authentication failed: ${details || error}`);
      // Redirect to login after 3 seconds
      setTimeout(() => router.push('/login'), 3000);
      return;
    }

    if (success === 'true' && userId) {
      // Store the userId in localStorage so api-client.ts can send X-User-Id header
      localStorage.setItem('user_id', userId);
      localStorage.setItem('auth_method', 'oauth');

      setStatus('success');
      setMessage(`Authenticated successfully! Redirecting to dashboard...`);

      // Redirect to dashboard
      setTimeout(() => router.push('/dashboard'), 1500);
    } else {
      setStatus('error');
      setMessage('Invalid callback parameters. Redirecting to login...');
      setTimeout(() => router.push('/login'), 3000);
    }
    // Only run once on mount; reading searchParams inside is enough.
    // This avoids the page being stuck in "processing" due to effect resets.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="max-w-md w-full bg-white dark:bg-gray-800 rounded-lg shadow-md p-8 text-center">
        {status === 'processing' && (
          <>
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-200">
              Processing Authentication
            </h2>
            <p className="mt-2 text-gray-500 dark:text-gray-400">{message}</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className="h-12 w-12 rounded-full bg-green-100 flex items-center justify-center mx-auto mb-4">
              <svg className="h-6 w-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-200">
              Authentication Successful
            </h2>
            <p className="mt-2 text-gray-500 dark:text-gray-400">{message}</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="h-12 w-12 rounded-full bg-red-100 flex items-center justify-center mx-auto mb-4">
              <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-200">
              Authentication Failed
            </h2>
            <p className="mt-2 text-gray-500 dark:text-gray-400">{message}</p>
          </>
        )}
      </div>
    </div>
  );
}

export default function AuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
        </div>
      }
    >
      <OAuthCallbackHandler />
    </Suspense>
  );
}
