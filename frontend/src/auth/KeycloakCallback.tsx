import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin, message } from 'antd';
import { useAuth } from './AuthContext';

const KeycloakCallback: React.FC = () => {
  const navigate = useNavigate();
  const { handleKeycloakCallback } = useAuth();
  const processingRef = useRef(false);

  useEffect(() => {
    if (processingRef.current) return;
    processingRef.current = true;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const error = params.get('error');

    if (error) {
      message.error(`Помилка автентифікації: ${error}`);
      navigate('/login', { replace: true });
      return;
    }

    if (!code) {
      message.error('Код авторизації не знайдено');
      navigate('/login', { replace: true });
      return;
    }

    handleKeycloakCallback(code)
      .then(() => {
        navigate('/dashboard', { replace: true });
      })
      .catch((err) => {
        console.error('Keycloak callback error:', err);
        message.error('Помилка входу через Keycloak');
        navigate('/login', { replace: true });
      });
  }, [handleKeycloakCallback, navigate]);

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <Spin size="large" tip="Виконується вхід..." />
    </div>
  );
};

export default KeycloakCallback;
