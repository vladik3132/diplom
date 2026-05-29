import axios from 'axios';
import { refreshAccessToken } from '@/auth/keycloakAuth';

const axiosInstance = axios.create({
  baseURL: '/api',
  // 5 хвилин — обрахунок ОПП з AI-перевірками (десятки/сотні викликів Mistral)
  // на першому проході може зайняти 1-2 хв, при cold-cache до 3+ хв.
  // Без timeout axios міг скидати запит за замовчуванням раніше ніж backend дописав відповідь.
  timeout: 5 * 60 * 1000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ─── Custom events for auth state (listened by AuthContext) ───
export const AUTH_EXPIRED_EVENT = 'auth:session-expired';
export const AUTH_WARNING_EVENT = 'auth:session-warning';
export const AUTH_REFRESHED_EVENT = 'auth:token-refreshed';

function fireAuthExpired(reason: string) {
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { reason } }));
}

// ─── Parse JWT exp claim without library ───
export function getTokenExpiry(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return decoded.exp ? decoded.exp * 1000 : null; // ms
  } catch {
    return null;
  }
}

// ─── Determine auth type ───
function isKeycloakAuth(): boolean {
  return !!localStorage.getItem('keycloak_token');
}

function isLocalAuth(): boolean {
  return !localStorage.getItem('keycloak_token') && !!localStorage.getItem('token');
}

function getCurrentToken(): string | null {
  return localStorage.getItem('keycloak_token') || localStorage.getItem('token');
}

// ─── Local token refresh via backend /api/auth/refresh ───
async function doLocalRefresh(): Promise<string> {
  const currentToken = localStorage.getItem('token');
  if (!currentToken) throw new Error('No token');

  // Use raw axios to avoid interceptor loops
  const response = await axios.post('/api/auth/refresh', null, {
    headers: { Authorization: `Bearer ${currentToken}` },
  });

  const newToken = response.data.token;
  localStorage.setItem('token', newToken);

  // Notify AuthContext to update user state
  window.dispatchEvent(new CustomEvent(AUTH_REFRESHED_EVENT, {
    detail: { token: newToken },
  }));

  return newToken;
}

// ─── Proactive token refresh (both Keycloak and local) ───
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleTokenRefresh() {
  if (refreshTimer) clearTimeout(refreshTimer);

  const token = getCurrentToken();
  if (!token) return;

  const expiry = getTokenExpiry(token);
  if (!expiry) return;

  // Refresh 60 seconds before expiry
  const refreshIn = expiry - Date.now() - 60_000;
  if (refreshIn <= 0) {
    // Already near expiry — refresh now
    doProactiveRefresh();
    return;
  }

  refreshTimer = setTimeout(() => doProactiveRefresh(), refreshIn);
}

async function doProactiveRefresh() {
  try {
    if (isKeycloakAuth()) {
      const refreshToken = localStorage.getItem('keycloak_refresh_token');
      if (!refreshToken) return;
      const tokens = await refreshAccessToken(refreshToken);
      localStorage.setItem('keycloak_token', tokens.access_token);
      localStorage.setItem('keycloak_refresh_token', tokens.refresh_token);
    } else if (isLocalAuth()) {
      await doLocalRefresh();
    }
    // Schedule next refresh
    scheduleTokenRefresh();
  } catch {
    // Refresh failed — will get 401 on next request, handled by response interceptor
  }
}

// Start scheduling on load
scheduleTokenRefresh();

// Re-schedule after any token update (called from AuthContext)
export function notifyTokenUpdated() {
  scheduleTokenRefresh();
}

// ─── Visibility / focus handling ───
// Коли вкладка була у фоні, setTimeout часто не спрацьовує (throttling),
// тому при поверненні перевіряємо токен синхронно і тригеримо refresh,
// або відразу викидаємо на /login якщо все пропало.
async function checkAuthOnResume() {
  const token = getCurrentToken();
  if (!token) return; // не залогінений — хай ProtectedRoute вирішує

  const expiry = getTokenExpiry(token);
  if (!expiry) return;

  const now = Date.now();
  if (expiry - now > 60_000) {
    // ще живий — просто переплануй таймер
    scheduleTokenRefresh();
    return;
  }

  // Прострочений або ось-ось протухне → пробуємо refresh прямо зараз
  try {
    if (isKeycloakAuth()) {
      const refreshToken = localStorage.getItem('keycloak_refresh_token');
      if (!refreshToken) throw new Error('No refresh token');
      const tokens = await refreshAccessToken(refreshToken);
      localStorage.setItem('keycloak_token', tokens.access_token);
      localStorage.setItem('keycloak_refresh_token', tokens.refresh_token);
      scheduleTokenRefresh();
    } else if (isLocalAuth()) {
      await doLocalRefresh();
      scheduleTokenRefresh();
    }
  } catch {
    // refresh неможливий → hard-redirect на login, щоб не було інфініт loading
    fireAuthExpired('Сесія закінчилася. Будь ласка, увійдіть знову.');
  }
}

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      checkAuthOnResume();
    }
  });
  window.addEventListener('focus', checkAuthOnResume);
}

// ─── Request interceptor: attach token, proactive refresh if near expiry ───
axiosInstance.interceptors.request.use(
  async (config) => {
    // Skip if Authorization already set (e.g. by fetchMe)
    if (config.headers.Authorization) return config;

    // Don't intercept the refresh call itself
    if (config.url === '/auth/refresh') return config;

    let token = getCurrentToken();

    // If token expires in < 30s, try refresh before sending request
    if (token) {
      const expiry = getTokenExpiry(token);
      if (expiry && expiry - Date.now() < 30_000) {
        try {
          if (isKeycloakAuth()) {
            const refreshToken = localStorage.getItem('keycloak_refresh_token');
            if (refreshToken) {
              const tokens = await refreshAccessToken(refreshToken);
              localStorage.setItem('keycloak_token', tokens.access_token);
              localStorage.setItem('keycloak_refresh_token', tokens.refresh_token);
              token = tokens.access_token;
              scheduleTokenRefresh();
            }
          } else if (isLocalAuth()) {
            token = await doLocalRefresh();
            scheduleTokenRefresh();
          }
        } catch {
          // Will fail with 401, handled by response interceptor
        }
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ─── Response interceptor: handle 401 with token refresh ───
let isRefreshing = false;
let failedQueue: { resolve: (token: string) => void; reject: (err: unknown) => void }[] = [];

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach((p) => {
    if (token) p.resolve(token);
    else p.reject(error);
  });
  failedQueue = [];
};

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // Queue concurrent requests while refreshing
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            resolve(axiosInstance(originalRequest));
          },
          reject,
        });
      });
    }

    isRefreshing = true;
    originalRequest._retry = true;

    try {
      let newToken: string;

      if (isKeycloakAuth()) {
        const keycloakRefreshToken = localStorage.getItem('keycloak_refresh_token');
        if (!keycloakRefreshToken) throw new Error('No refresh token');
        const tokens = await refreshAccessToken(keycloakRefreshToken);
        localStorage.setItem('keycloak_token', tokens.access_token);
        localStorage.setItem('keycloak_refresh_token', tokens.refresh_token);
        newToken = tokens.access_token;
      } else if (isLocalAuth()) {
        newToken = await doLocalRefresh();
      } else {
        throw new Error('No auth');
      }

      scheduleTokenRefresh();
      processQueue(null, newToken);
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return axiosInstance(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      fireAuthExpired('Сесія закінчилася. Будь ласка, увійдіть знову.');
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default axiosInstance;
