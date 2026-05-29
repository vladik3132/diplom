import React, { createContext, useContext, useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { message } from 'antd';
import { login as apiLogin, fetchMe, LoginResponse } from '../api/authApi';
import { KEYCLOAK_ENABLED } from './keycloakConfig';
import { redirectToKeycloak, exchangeCodeForTokens, keycloakLogout } from './keycloakAuth';
import { notifyTokenUpdated, getTokenExpiry, AUTH_EXPIRED_EVENT, AUTH_REFRESHED_EVENT } from '../api/axiosInstance';

export interface User {
  email: string;
  role: string;
  token: string;
  teacherId: number | null;
  departmentId: number | null;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isHead: boolean;
  isTeacher: boolean;
  keycloakEnabled: boolean;
  login: (email: string, password: string) => Promise<void>;
  loginWithKeycloak: () => void;
  loginWithGoogle: () => void;
  handleKeycloakCallback: (code: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const getStoredUser = (): User | null => {
  const keycloakToken = localStorage.getItem('keycloak_token');
  const localToken = localStorage.getItem('token');
  const token = keycloakToken || localToken;
  const email = localStorage.getItem('userEmail');
  const role = localStorage.getItem('userRole');
  const teacherIdStr = localStorage.getItem('userTeacherId');
  const departmentIdStr = localStorage.getItem('userDepartmentId');
  if (token && email && role) {
    // Check if token is already expired
    const expiry = getTokenExpiry(token);
    if (expiry && expiry < Date.now()) {
      // Local token: dead, clear.
      if (!keycloakToken) {
        clearAllTokens();
        return null;
      }
      // Keycloak: access-token прострочений — refresh може ще спрацювати,
      // але тільки якщо є refresh-токен. Якщо його немає — сесія точно мертва.
      const refreshToken = localStorage.getItem('keycloak_refresh_token');
      if (!refreshToken) {
        clearAllTokens();
        return null;
      }
    }
    return {
      token,
      email,
      role,
      teacherId: teacherIdStr ? Number(teacherIdStr) : null,
      departmentId: departmentIdStr ? Number(departmentIdStr) : null,
    };
  }
  return null;
};

const storeUserData = (token: string, data: LoginResponse, isKeycloak: boolean) => {
  if (isKeycloak) {
    localStorage.setItem('keycloak_token', token);
  } else {
    localStorage.setItem('token', token);
  }
  localStorage.setItem('userEmail', data.email);
  localStorage.setItem('userRole', data.role);
  if (data.teacherId != null) localStorage.setItem('userTeacherId', String(data.teacherId));
  else localStorage.removeItem('userTeacherId');
  if (data.departmentId != null) localStorage.setItem('userDepartmentId', String(data.departmentId));
  else localStorage.removeItem('userDepartmentId');
};

const clearAllTokens = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('keycloak_token');
  localStorage.removeItem('keycloak_refresh_token');
  localStorage.removeItem('userEmail');
  localStorage.removeItem('userRole');
  localStorage.removeItem('userTeacherId');
  localStorage.removeItem('userDepartmentId');
  sessionStorage.removeItem('pkce_code_verifier');
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(getStoredUser);
  const sessionExpiredHandled = useRef(false);

  // Listen for auth:session-expired events from axios interceptor
  useEffect(() => {
    const handleSessionExpired = (e: Event) => {
      // Prevent multiple simultaneous notifications
      if (sessionExpiredHandled.current) return;
      sessionExpiredHandled.current = true;

      const reason = (e as CustomEvent).detail?.reason || 'Сесія закінчилася';
      clearAllTokens();
      setUser(null);
      message.warning(reason, 2);

      // Hard-redirect на /login — гарантовано скидає весь React state,
      // щоб завислі `setLoading(true)` в компонентах не тримали UI у
      // вічному спінері. Не потрібно чекати на React re-render через
      // ProtectedRoute, бо якийсь useEffect може вже виконувати запит,
      // що повис у refresh-катуванні.
      if (window.location.pathname !== '/login') {
        // даємо 300мс на показ message, потім перезавантажуємо
        setTimeout(() => {
          window.location.href = '/login';
        }, 300);
      } else {
        sessionExpiredHandled.current = false;
      }
    };

    // When local token is refreshed, update user state with new token
    const handleTokenRefreshed = (e: Event) => {
      const newToken = (e as CustomEvent).detail?.token;
      if (newToken) {
        setUser((prev) => prev ? { ...prev, token: newToken } : null);
      }
    };

    window.addEventListener(AUTH_EXPIRED_EVENT, handleSessionExpired);
    window.addEventListener(AUTH_REFRESHED_EVENT, handleTokenRefreshed);
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleSessionExpired);
      window.removeEventListener(AUTH_REFRESHED_EVENT, handleTokenRefreshed);
    };
  }, []);

  // Local login (email/password)
  const login = useCallback(async (email: string, password: string) => {
    const response: LoginResponse = await apiLogin(email, password);
    storeUserData(response.token, response, false);
    setUser({
      email: response.email,
      role: response.role,
      token: response.token,
      teacherId: response.teacherId ?? null,
      departmentId: response.departmentId ?? null,
    });
  }, []);

  // Keycloak redirect
  const loginWithKeycloak = useCallback(() => {
    redirectToKeycloak();
  }, []);

  // Google via Keycloak IdP hint
  const loginWithGoogle = useCallback(() => {
    redirectToKeycloak('google');
  }, []);

  // Handle callback from Keycloak redirect
  const handleKeycloakCallback = useCallback(async (code: string) => {
    const tokens = await exchangeCodeForTokens(code);

    // Store tokens and schedule proactive refresh
    localStorage.setItem('keycloak_token', tokens.access_token);
    localStorage.setItem('keycloak_refresh_token', tokens.refresh_token);
    notifyTokenUpdated();

    // Fetch user info from our backend (which validates the Keycloak JWT)
    const meData = await fetchMe(tokens.access_token);

    storeUserData(tokens.access_token, meData, true);
    setUser({
      email: meData.email,
      role: meData.role,
      token: tokens.access_token,
      teacherId: meData.teacherId ?? null,
      departmentId: meData.departmentId ?? null,
    });
  }, []);

  // Logout
  const logout = useCallback(() => {
    const refreshToken = localStorage.getItem('keycloak_refresh_token');
    if (refreshToken) {
      keycloakLogout(refreshToken);
    }
    clearAllTokens();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: !!user,
      isAdmin: user?.role === 'ADMIN',
      isHead: user?.role === 'HEAD_OF_DEPARTMENT',
      isTeacher: user?.role === 'TEACHER',
      keycloakEnabled: KEYCLOAK_ENABLED,
      login,
      loginWithKeycloak,
      loginWithGoogle,
      handleKeycloakCallback,
      logout,
    }),
    [user, login, loginWithKeycloak, loginWithGoogle, handleKeycloakCallback, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
