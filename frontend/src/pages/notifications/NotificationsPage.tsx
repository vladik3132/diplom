import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { List, Card, Button, Tag, Space, message, Badge, Typography, Tooltip } from 'antd';
import {
  BellOutlined,
  CheckOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  EditOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons';
import api from '@/api/axiosInstance';

const { Text } = Typography;

interface Notification {
  id: number;
  title: string;
  message: string;
  type: string;
  isRead: boolean;
  linkUrl?: string;
  createdAt: string;
}

const typeColor: Record<string, string> = {
  DEADLINE: 'red',
  COMPLIANCE_WARNING: 'orange',
  DOCUMENT_STATUS: 'blue',
  EDITORIAL: 'purple',
  DATA_CHANGE: 'cyan',
  SYSTEM: 'default',
};

const typeLabel: Record<string, string> = {
  DEADLINE: 'Дедлайн',
  COMPLIANCE_WARNING: 'Відповідність',
  DOCUMENT_STATUS: 'Документ',
  EDITORIAL: 'Редакційний план',
  DATA_CHANGE: 'Зміна даних',
  SYSTEM: 'Система',
};

const typeIcon: Record<string, React.ReactNode> = {
  DEADLINE: <ClockCircleOutlined style={{ color: '#f5222d', fontSize: 22 }} />,
  COMPLIANCE_WARNING: <WarningOutlined style={{ color: '#fa8c16', fontSize: 22 }} />,
  DOCUMENT_STATUS: <FileTextOutlined style={{ color: '#1890ff', fontSize: 22 }} />,
  EDITORIAL: <EditOutlined style={{ color: '#722ed1', fontSize: 22 }} />,
  DATA_CHANGE: <InfoCircleOutlined style={{ color: '#13c2c2', fontSize: 22 }} />,
  SYSTEM: <BellOutlined style={{ color: '#8c8c8c', fontSize: 22 }} />,
};

const formatDateTime = (dateStr: string) => {
  const date = new Date(dateStr);
  return date.toLocaleString('uk-UA', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const NotificationsPage: React.FC = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const loadData = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<Notification[]>('/notifications');
      setNotifications(data);
    } catch {
      message.error('Помилка завантаження нотифікацій');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  const markAsRead = async (id: number, e?: React.MouseEvent) => {
    e?.stopPropagation();
    await api.patch(`/notifications/${id}/read`);
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
  };

  const markAllAsRead = async () => {
    await api.patch('/notifications/read-all');
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    message.success('Всі нотифікації прочитані');
  };

  const handleClick = async (item: Notification) => {
    if (!item.isRead) {
      await api.patch(`/notifications/${item.id}/read`);
      setNotifications(prev => prev.map(n => n.id === item.id ? { ...n, isRead: true } : n));
    }
    if (item.linkUrl) {
      navigate(item.linkUrl);
    }
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;

  return (
    <Card
      title={
        <Space>
          <BellOutlined />
          Нотифікації
          {unreadCount > 0 && <Tag color="blue">{unreadCount} нових</Tag>}
        </Space>
      }
      extra={
        <Button onClick={markAllAsRead} icon={<CheckOutlined />} disabled={unreadCount === 0}>
          Прочитати всі
        </Button>
      }
    >
      <List
        loading={loading}
        dataSource={notifications}
        locale={{ emptyText: 'Немає нотифікацій' }}
        renderItem={(item) => (
          <List.Item
            style={{
              background: item.isRead ? 'transparent' : '#f0f5ff',
              padding: '12px 16px',
              borderRadius: 8,
              marginBottom: 8,
              cursor: item.linkUrl ? 'pointer' : 'default',
              transition: 'background 0.2s',
            }}
            onClick={() => handleClick(item)}
            onMouseEnter={(e) => {
              if (item.linkUrl) (e.currentTarget as HTMLDivElement).style.background = item.isRead ? '#fafafa' : '#e6f4ff';
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLDivElement).style.background = item.isRead ? 'transparent' : '#f0f5ff';
            }}
            actions={[
              !item.isRead && (
                <Button size="small" onClick={(e) => markAsRead(item.id, e)}>
                  Прочитати
                </Button>
              ),
              item.linkUrl && (
                <Tooltip title="Перейти до викладача">
                  <Button
                    size="small"
                    type="link"
                    icon={<ArrowRightOutlined />}
                    onClick={(e) => { e.stopPropagation(); navigate(item.linkUrl!); }}
                  />
                </Tooltip>
              ),
            ].filter(Boolean)}
          >
            <List.Item.Meta
              avatar={
                <Badge dot={!item.isRead}>
                  {typeIcon[item.type] || <BellOutlined style={{ fontSize: 22 }} />}
                </Badge>
              }
              title={
                <Space>
                  <Text strong={!item.isRead}>{item.title}</Text>
                  <Tag color={typeColor[item.type]}>{typeLabel[item.type] || item.type}</Tag>
                </Space>
              }
              description={
                <Space direction="vertical" size={2}>
                  <Text style={{ color: '#595959' }}>{item.message}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>{formatDateTime(item.createdAt)}</Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  );
};

export default NotificationsPage;
