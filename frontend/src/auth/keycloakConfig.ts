export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'https://auth.viti.edu.ua';
export const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'grade-book';
export const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'grade-book-client-web';
export const KEYCLOAK_ENABLED = import.meta.env.VITE_KEYCLOAK_ENABLED === 'true';

export const KEYCLOAK_AUTH_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`;
export const KEYCLOAK_TOKEN_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
export const KEYCLOAK_LOGOUT_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`;
export const KEYCLOAK_REDIRECT_URI = `${window.location.origin}/login/callback`;
