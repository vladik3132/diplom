import React, { useState } from 'react';
import { Button, Card, Typography, Form, Input, message } from 'antd';
import { GoogleOutlined, LoginOutlined, UserOutlined, LockOutlined } from '@ant-design/icons';
import { useAuth } from './AuthContext';
import { useNavigate } from 'react-router-dom';

const { Title, Text } = Typography;

const LoginPage: React.FC = () => {
  const { login, loginWithKeycloak, loginWithGoogle, keycloakEnabled } = useAuth();
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      await login(values.email, values.password);
      message.success('Успішний вхід');
      navigate('/');
    } catch (error) {
      console.error(error);
      message.error('Помилка входу. Перевірте логін та пароль.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 420, boxShadow: '0 2px 8px rgba(0,0,0,0.15)' }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Система управління даними викладачів
        </Title>
        <Text
          type="secondary"
          style={{ display: 'block', textAlign: 'center', marginBottom: 24 }}
        >
          {keycloakEnabled ? 'Увійдіть за допомогою корпоративного облікового запису' : 'Увійдіть у систему'}
        </Text>

        {!keycloakEnabled ? (
          <Form
            name="normal_login"
            onFinish={onFinish}
            layout="vertical"
          >
            <Form.Item
              name="email"
              rules={[
                { required: true, message: 'Будь ласка, введіть ваш email!' },
                { type: 'email', message: 'Некоректний email' }
              ]}
            >
              <Input prefix={<UserOutlined />} placeholder="Email (напр. admin@viti.edu.ua)" size="large" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: 'Будь ласка, введіть ваш пароль!' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Пароль"
                size="large"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button type="primary" htmlType="submit" block size="large" loading={loading}>
                Увійти
              </Button>
            </Form.Item>
          </Form>
        ) : (
          <>
            <Button
              type="primary"
              icon={<LoginOutlined />}
              onClick={loginWithKeycloak}
              block
              size="large"
              style={{ marginBottom: 12 }}
            >
              Увійти через SSO
            </Button>
            <Button
              icon={<GoogleOutlined />}
              onClick={loginWithGoogle}
              block
              size="large"
            >
              Увійти через Google
            </Button>
          </>
        )}
      </Card>
    </div>
  );
};

export default LoginPage;
