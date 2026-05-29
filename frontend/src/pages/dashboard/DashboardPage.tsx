import React, { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Table, Typography, Tag, message, Result } from 'antd';
import {
  TeamOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import { Navigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { getComplianceAll } from '@/api/achievementApi';
import { ComplianceReport } from '@/types/achievement';
import { useAuth } from '@/auth/AuthContext';

const { Title } = Typography;

const statusConfig: Record<string, { color: string; label: string }> = {
  COMPLIANT: { color: 'green', label: 'Відповідає' },
  WARNING: { color: 'orange', label: 'Попередження' },
  NON_COMPLIANT: { color: 'red', label: 'Не відповідає' },
  EXEMPT: { color: 'blue', label: 'Стаж НПП менше 3 років' },
};

const DashboardPage: React.FC = () => {
  const [data, setData] = useState<ComplianceReport[]>([]);
  const [loading, setLoading] = useState(false);
  const { user, isHead, isTeacher, isAdmin } = useAuth();

  // TEACHER: редіректимо на власний профіль (якщо teacherId є).
  // Якщо teacherId немає (адмін ще не привʼязав) — показуємо повідомлення замість 403.
  useEffect(() => {
    if (isTeacher) return;       // не вантажимо compliance для teacher
    setLoading(true);
    // HEAD sees only own department
    const deptId = isHead ? (user?.departmentId ?? undefined) : undefined;
    getComplianceAll(deptId)
      .then(setData)
      .catch(() => message.error('Помилка завантаження даних'))
      .finally(() => setLoading(false));
  }, [isHead, isTeacher, user?.departmentId]);

  if (isTeacher && user?.teacherId) {
    return <Navigate to={`/teachers/${user.teacherId}`} replace />;
  }
  if (isTeacher && !user?.teacherId) {
    return (
      <Result
        status="warning"
        title="Ваш обліковий запис не привʼязаний до картки викладача"
        subTitle="Зверніться до адміністратора для звʼязування акаунта з вашим профілем у системі."
      />
    );
  }
  if (isHead && !user?.departmentId) {
    return (
      <Result
        status="warning"
        title="Ваш обліковий запис не привʼязаний до кафедри"
        subTitle="Зверніться до адміністратора."
      />
    );
  }
  // Admin без явного departmentId — все ОК (бачить всіх).
  void isAdmin;

  const total = data.length;
  const compliant = data.filter(d => d.status === 'COMPLIANT').length;
  const warning = data.filter(d => d.status === 'WARNING').length;
  const nonCompliant = data.filter(d => d.status === 'NON_COMPLIANT').length;
  const exempt = data.filter(d => d.status === 'EXEMPT').length;

  const columns: ColumnsType<ComplianceReport> = [
    { title: 'ПІБ', dataIndex: 'teacherName', key: 'teacherName', sorter: (a, b) => a.teacherName.localeCompare(b.teacherName) },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      filters: [
        { text: 'Відповідає', value: 'COMPLIANT' },
        { text: 'Попередження', value: 'WARNING' },
        { text: 'Не відповідає', value: 'NON_COMPLIANT' },
        { text: 'Стаж НПП менше 3 років', value: 'EXEMPT' },
      ],
      onFilter: (value, record) => record.status === value,
      render: (status: string) => {
        const cfg = statusConfig[status] || { color: 'default', label: status };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: 'Досягнень', dataIndex: 'achievementCount', key: 'achievementCount', sorter: (a, b) => a.achievementCount - b.achievementCount },
    { title: 'Унікальних типів', dataIndex: 'uniqueTypeCount', key: 'uniqueTypeCount', sorter: (a, b) => a.uniqueTypeCount - b.uniqueTypeCount },
    {
      title: 'Причина звільнення',
      dataIndex: 'exemptionReason',
      key: 'exemptionReason',
      render: (reason: string) => reason || '-',
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        Dashboard
      </Title>

      <Row gutter={[16, 16]} style={{ marginBottom: 32 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Всього викладачів"
              value={total}
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Відповідають п.38"
              value={compliant}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Потребують уваги"
              value={warning}
              valueStyle={{ color: '#faad14' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Не відповідають"
              value={nonCompliant}
              valueStyle={{ color: '#cf1322' }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {exempt > 0 && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="Звільнені від вимог"
                value={exempt}
                valueStyle={{ color: '#1890ff' }}
                prefix={<SafetyOutlined />}
              />
            </Card>
          </Col>
        </Row>
      )}

      <Card>
        <Title level={5} style={{ marginBottom: 16 }}>
          Зведена таблиця відповідності
        </Title>
        <Table
          dataSource={data}
          columns={columns}
          rowKey="teacherId"
          loading={loading}
          locale={{ emptyText: 'Немає даних' }}
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </div>
  );
};

export default DashboardPage;
