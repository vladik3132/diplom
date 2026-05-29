import {
  KEYCLOAK_AUTH_URL, KEYCLOAK_TOKEN_URL, KEYCLOAK_LOGOUT_URL,
  KEYCLOAK_CLIENT_ID, KEYCLOAK_REDIRECT_URI,
} from './keycloakConfig';

// ─── PKCE Utilities ───

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let str = '';
  bytes.forEach((b) => { str += String.fromCharCode(b); });
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function generateCodeVerifier(): string {
  const array = new Uint8Array(64);
  crypto.getRandomValues(array);
  return base64UrlEncode(array.buffer);
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(digest);
}

// ─── Auth URLs ───

export async function redirectToKeycloak(idpHint?: string): Promise<void> {
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);

  sessionStorage.setItem('pkce_code_verifier', codeVerifier);

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    redirect_uri: KEYCLOAK_REDIRECT_URI,
    response_type: 'code',
    scope: 'openid profile email',
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  });

  if (idpHint) {
    params.set('kc_idp_hint', idpHint);
  }

  window.location.href = `${KEYCLOAK_AUTH_URL}?${params.toString()}`;
}

// ─── Token Exchange ───

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
}

/**
 * fetch with timeout — щоб запити до Keycloak не висіли нескінченно
 * (інакше при обриві мережі / simbe Keycloak UI застряє у вічному loading).
 */
async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs = 10_000): Promise<Response> {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(id);
  }
}

export async function exchangeCodeForTokens(code: string): Promise<TokenResponse> {
  const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
  if (!codeVerifier) throw new Error('PKCE code_verifier not found');

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: KEYCLOAK_CLIENT_ID,
    code,
    redirect_uri: KEYCLOAK_REDIRECT_URI,
    code_verifier: codeVerifier,
  });

  const response = await fetchWithTimeout(KEYCLOAK_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Token exchange failed: ${error}`);
  }

  sessionStorage.removeItem('pkce_code_verifier');
  return response.json();
}

// ─── Token Refresh ───

export async function refreshAccessToken(refreshToken: string): Promise<TokenResponse> {
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: KEYCLOAK_CLIENT_ID,
    refresh_token: refreshToken,
  });

  const response = await fetchWithTimeout(KEYCLOAK_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  }, 10_000);

  if (!response.ok) throw new Error('Token refresh failed');
  return response.json();
}

// ─── Logout ───

export async function keycloakLogout(refreshToken: string): Promise<void> {
  try {
    await fetchWithTimeout(KEYCLOAK_LOGOUT_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: KEYCLOAK_CLIENT_ID,
        refresh_token: refreshToken,
      }).toString(),
    }, 5_000);
  } catch {
    // Ignore logout errors
  }
}
