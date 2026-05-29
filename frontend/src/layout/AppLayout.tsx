import React, { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Badge, Popover, List, Empty, notification as antNotification, Dropdown, Tag } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  FileTextOutlined,
  TrophyOutlined,
  CheckCircleOutlined,
  BookOutlined,
  RiseOutlined,
  EditOutlined,
  FileWordOutlined,
  BellOutlined,
  LogoutOutlined,
  LeftOutlined,
  RightOutlined,
  RobotOutlined,
  UploadOutlined,
  BankOutlined,
  ApartmentOutlined,
  UserOutlined,
  ReadOutlined,
  SolutionOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useAuth } from '../auth/AuthContext';
import api from '@/api/axiosInstance';
import './AppLayout.css';

type RoleKey = 'ADMIN' | 'HEAD_OF_DEPARTMENT' | 'TEACHER';

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  roles: RoleKey[];
}

interface NotificationItem {
  id: number;
  title: string;
  message: string;
  type: string;
  isRead: boolean;
  linkUrl?: string;
  createdAt: string;
}

const allMenuItems: MenuItem[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/teachers', icon: <TeamOutlined />, label: 'Викладачі', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/my-profile', icon: <UserOutlined />, label: 'Мій профіль', roles: ['HEAD_OF_DEPARTMENT', 'TEACHER'] },
  { key: '/departments', icon: <BankOutlined />, label: 'Кафедри', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/org-structure', icon: <ApartmentOutlined />, label: 'Штат', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/educational-programs', icon: <SolutionOutlined />, label: 'ОПП', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/rating', icon: <TrophyOutlined />, label: 'Рейтинг НПП', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT', 'TEACHER'] },
  { key: '/publications', icon: <FileTextOutlined />, label: 'Публікації', roles: ['TEACHER'] },
  { key: '/achievements', icon: <TrophyOutlined />, label: 'Досягнення п.38', roles: ['TEACHER'] },
  { key: '/compliance', icon: <CheckCircleOutlined />, label: 'Звіт відповідності', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/disciplines', icon: <BookOutlined />, label: 'Дисципліни', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/qualification', icon: <RiseOutlined />, label: 'Підвищення кваліфікації', roles: ['TEACHER'] },
  { key: '/docx', icon: <FileWordOutlined />, label: 'Конструктор DOCX', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/import', icon: <UploadOutlined />, label: 'Імпорт даних', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/fakhovi-journals', icon: <ReadOutlined />, label: 'Фахові видання', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT', 'TEACHER'] },
  { key: '/ai', icon: <RobotOutlined />, label: 'AI-асистент', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT'] },
  { key: '/notifications', icon: <BellOutlined />, label: 'Нотифікації', roles: ['ADMIN', 'HEAD_OF_DEPARTMENT', 'TEACHER'] },
];

const PAGE_TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/teachers': 'Викладачі',
  '/my-profile': 'Мій профіль',
  '/departments': 'Кафедри',
  '/org-structure': 'Штат',
  '/educational-programs': 'ОПП',
  '/rating': 'Рейтинг НПП',
  '/publications': 'Публікації',
  '/achievements': 'Досягнення п.38',
  '/compliance': 'Звіт відповідності',
  '/disciplines': 'Дисципліни',
  '/qualification': 'Підвищення кваліфікації',
  '/docx': 'Конструктор DOCX',
  '/import': 'Імпорт даних',
  '/fakhovi-journals': 'Фахові видання',
  '/ai': 'AI-асистент',
  '/notifications': 'Нотифікації',
  '/gantt': 'Діаграма Ганта',
  '/editorial': 'Видавничий план',
};

const typeIcon: Record<string, React.ReactNode> = {
  DEADLINE: <ClockCircleOutlined style={{ color: '#f5222d' }} />,
  COMPLIANCE_WARNING: <WarningOutlined style={{ color: '#fa8c16' }} />,
  DOCUMENT_STATUS: <FileTextOutlined style={{ color: '#1890ff' }} />,
  EDITORIAL: <EditOutlined style={{ color: '#722ed1' }} />,
  DATA_CHANGE: <InfoCircleOutlined style={{ color: '#13c2c2' }} />,
  SYSTEM: <BellOutlined style={{ color: '#8c8c8c' }} />,
};

const formatTime = (dateStr: string) => {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'щойно';
  if (diffMin < 60) return `${diffMin} хв тому`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours} год тому`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays} дн тому`;
  return date.toLocaleDateString('uk-UA');
};

const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [recentNotifications, setRecentNotifications] = useState<NotificationItem[]>([]);
  const [popoverOpen, setPopoverOpen] = useState(false);
  const prevUnreadRef = useRef(0);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();

  const role = (user?.role || 'TEACHER') as RoleKey;

  // ─── Notifications ───
  const fetchUnreadCount = useCallback(async () => {
    try {
      const { data } = await api.get<{ count: number }>('/notifications/unread/count');
      const newCount = data.count;
      if (newCount > prevUnreadRef.current && prevUnreadRef.current >= 0) {
        const { data: unread } = await api.get<NotificationItem[]>('/notifications/unread');
        if (unread.length > 0) {
          const latest = unread[0];
          antNotification.info({
            message: latest.title,
            description: latest.message,
            icon: typeIcon[latest.type] || <BellOutlined />,
            placement: 'topRight',
            duration: 5,
            onClick: () => {
              if (latest.linkUrl) navigate(latest.linkUrl);
              antNotification.destroy();
            },
            style: { cursor: 'pointer' },
          });
        }
      }
      prevUnreadRef.current = newCount;
      setUnreadCount(newCount);
    } catch { /* ignore */ }
  }, [navigate]);

  useEffect(() => {
    if (!user) return;
    const loadInitial = async () => {
      try {
        const { data } = await api.get<{ count: number }>('/notifications/unread/count');
        prevUnreadRef.current = data.count;
        setUnreadCount(data.count);
      } catch { /* ignore */ }
    };
    loadInitial();
    const interval = setInterval(fetchUnreadCount, 15000);
    return () => clearInterval(interval);
  }, [user, fetchUnreadCount]);

  const fetchRecent = useCallback(async () => {
    try {
      const { data } = await api.get<NotificationItem[]>('/notifications');
      setRecentNotifications(data.slice(0, 8));
    } catch { /* ignore */ }
  }, []);

  const handlePopoverOpenChange = (open: boolean) => {
    setPopoverOpen(open);
    if (open) fetchRecent();
  };

  const handleNotificationClick = async (item: NotificationItem) => {
    if (!item.isRead) {
      try {
        await api.patch(`/notifications/${item.id}/read`);
        setUnreadCount(prev => Math.max(0, prev - 1));
        setRecentNotifications(prev =>
          prev.map(n => n.id === item.id ? { ...n, isRead: true } : n)
        );
      } catch { /* ignore */ }
    }
    setPopoverOpen(false);
    if (item.linkUrl) navigate(item.linkUrl);
  };

  const handleMarkAllRead = async () => {
    try {
      await api.patch('/notifications/read-all');
      setUnreadCount(0);
      setRecentNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    } catch { /* ignore */ }
  };

  // ─── Menu & navigation ───
  const menuItems = useMemo(() => {
    return allMenuItems.filter((item) => item.roles.includes(role));
  }, [role]);

  const selectedKey = useMemo(() => {
    if (user?.teacherId && location.pathname === `/teachers/${user.teacherId}` && (role === 'TEACHER' || role === 'HEAD_OF_DEPARTMENT')) {
      return '/my-profile';
    }
    return menuItems.find((item) => location.pathname.startsWith(item.key))?.key || '/dashboard';
  }, [location.pathname, menuItems, user?.teacherId, role]);

  const handleNavClick = (key: string) => {
    if (key === '/my-profile' && user?.teacherId) {
      navigate(`/teachers/${user.teacherId}`);
    } else {
      navigate(key);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // ─── Page title ───
  const pageTitle = useMemo(() => {
    if (PAGE_TITLES[location.pathname]) return PAGE_TITLES[location.pathname];
    const prefix = Object.keys(PAGE_TITLES).find(k => k !== '/' && location.pathname.startsWith(k));
    return prefix ? PAGE_TITLES[prefix] : '';
  }, [location.pathname]);

  // ─── Notification popover content ───
  const notificationPopoverContent = (
    <div style={{ width: 380, maxHeight: 450, overflow: 'auto' }}>
      {recentNotifications.length === 0 ? (
        <Empty description="Немає нотифікацій" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <List
          size="small"
          dataSource={recentNotifications}
          renderItem={(item) => (
            <List.Item
              style={{
                cursor: 'pointer',
                padding: '8px 12px',
                background: item.isRead ? 'transparent' : '#fff7ed',
                borderRadius: 6,
                marginBottom: 2,
              }}
              onClick={() => handleNotificationClick(item)}
            >
              <List.Item.Meta
                avatar={typeIcon[item.type] || <BellOutlined />}
                title={
                  <span style={{ fontWeight: item.isRead ? 'normal' : 600, fontSize: 13 }}>
                    {item.title}
                  </span>
                }
                description={
                  <div>
                    <div style={{ fontSize: 12, color: '#595959', marginBottom: 2 }}>
                      {item.message}
                    </div>
                    <div style={{ fontSize: 11, color: '#bfbfbf' }}>
                      {formatTime(item.createdAt)}
                    </div>
                  </div>
                }
              />
            </List.Item>
          )}
        />
      )}
      <div style={{ borderTop: '1px solid #f0f0f0', padding: '8px 0 4px', display: 'flex', justifyContent: 'space-between' }}>
        <button
          onClick={handleMarkAllRead}
          disabled={unreadCount === 0}
          style={{ background: 'none', border: 'none', color: '#ea580c', cursor: 'pointer', fontSize: 13, padding: '4px 8px', opacity: unreadCount === 0 ? 0.4 : 1 }}
        >
          Прочитати всі
        </button>
        <button
          onClick={() => { setPopoverOpen(false); navigate('/notifications'); }}
          style={{ background: 'none', border: 'none', color: '#ea580c', cursor: 'pointer', fontSize: 13, padding: '4px 8px' }}
        >
          Всі нотифікації
        </button>
      </div>
    </div>
  );

  const userName = user?.email?.split('@')[0] || user?.email || '';

  const ROLE_LABELS: Record<RoleKey, string> = {
    ADMIN: 'Адміністратор',
    HEAD_OF_DEPARTMENT: 'Начальник кафедри',
    TEACHER: 'Викладач',
  };

  const ROLE_COLORS: Record<RoleKey, string> = {
    ADMIN: 'red',
    HEAD_OF_DEPARTMENT: 'orange',
    TEACHER: 'blue',
  };

  const userMenuItems = [
    {
      key: 'role',
      label: <Tag color={ROLE_COLORS[role]}>{ROLE_LABELS[role]}</Tag>,
      disabled: true,
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Вийти',
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <div className="app-layout">
      {/* ─── Sidebar ─── */}
      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-logo">
          <img src="/logo.png" alt="logo" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
          {!collapsed && <span className="sidebar-logo-text">TeacherLicence</span>}
        </div>

        <nav className="sidebar-nav">
          {menuItems.map((item) => (
            <button
              key={item.key}
              className={`nav-item ${selectedKey === item.key ? 'active' : ''}`}
              onClick={() => handleNavClick(item.key)}
              title={collapsed ? item.label : undefined}
            >
              <span className="nav-icon">{item.icon}</span>
              {!collapsed && <span className="nav-label">{item.label}</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <button className="nav-item" onClick={handleLogout} title={collapsed ? 'Вийти' : undefined}>
            <span className="nav-icon"><LogoutOutlined /></span>
            {!collapsed && <span className="nav-label">Вийти</span>}
          </button>
        </div>

        <button className="sidebar-toggle" onClick={() => setCollapsed(!collapsed)}>
          {collapsed ? <RightOutlined /> : <LeftOutlined />}
        </button>
      </aside>

      {/* ─── Main area ─── */}
      <div className={`main-wrapper ${collapsed ? 'sidebar-collapsed' : ''}`}>
        <header className="app-header">
          <div className="page-title">{pageTitle}</div>
          <div className="header-actions">
            <Popover
              content={notificationPopoverContent}
              title={<span style={{ fontWeight: 600 }}>Нотифікації</span>}
              trigger="click"
              open={popoverOpen}
              onOpenChange={handlePopoverOpenChange}
              placement="bottomRight"
            >
              <Badge count={unreadCount} size="small" offset={[-2, 4]}>
                <button className="notification-btn">
                  <BellOutlined />
                </button>
              </Badge>
            </Popover>
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
              <button className="header-user-btn">
                <UserOutlined />
                <span>{userName}</span>
                <Tag color={ROLE_COLORS[role]} style={{ marginLeft: 6, marginRight: 0 }}>{ROLE_LABELS[role]}</Tag>
              </button>
            </Dropdown>
          </div>
        </header>

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AppLayout;
