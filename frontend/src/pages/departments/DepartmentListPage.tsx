import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Card, Row, Col, Statistic, Select, Typography, Tag, Progress, Space, message } from 'antd';
import {
  BankOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { getDepartmentComplianceSummaries, getFaculties } from '@/api/departmentApi';
import { DepartmentComplianceSummary, Faculty } from '@/types/teacher';

const { Title, Text } = Typography;

const overallStatusConfig: Record<string, { color: string; label: string }> = {
  GOOD: { color: 'green', label: 'Відповідає' },
  WARNING: { color: 'orange', label: 'Потребує уваги' },
  CRITICAL: { color: 'red', label: 'Критичний' },
};

const DepartmentListPage: React.FC = () => {
  const [summaries, setSummaries] = useState<DepartmentComplianceSummary[]>([]);
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [selectedFacultyId, setSelectedFacultyId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getDepartmentComplianceSummaries(),
      getFaculties(),
    ])
      .then(([sums, facs]) => {
        setSummaries(sums);
        setFaculties(facs);
      })
      .catch(() => message.error('Помилка завантаження даних'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    if (!selectedFacultyId) return summaries;
    return summaries.filter(s =>
      faculties.find(f => f.id === selectedFacultyId)?.name === s.facultyName
    );
  }, [summaries, selectedFacultyId, faculties]);

  const goodCount = filtered.filter(s => s.overallStatus === 'GOOD').length;
  const warningCount = filtered.filter(s => s.overallStatus === 'WARNING').length;
  const criticalCount = filtered.filter(s => s.overallStatus === 'CRITICAL').length;

  // Сортуємо номер як натуральне число (щоб "11" йшло після "9", а не перед).
  const numberSort = (a?: string | null, b?: string | null): number => {
    const na = a != null && a !== '' ? Number(a) : Number.POSITIVE_INFINITY;
    const nb = b != null && b !== '' ? Number(b) : Number.POSITIVE_INFINITY;
    if (Number.isNaN(na) || Number.isNaN(nb)) {
      return (a || '').localeCompare(b || '');
    }
    return na - nb;
  };

  const columns: ColumnsType<DepartmentComplianceSummary> = [
    {
      title: '№',
      dataIndex: 'departmentNumber',
      key: 'departmentNumber',
      width: 80,
      defaultSortOrder: 'ascend',
      sorter: (a, b) => numberSort(a.departmentNumber, b.departmentNumber),
      render: (n: string | null | undefined) => n || '—',
    },
    {
      title: 'Назва кафедри',
      dataIndex: 'departmentName',
      key: 'departmentName',
      sorter: (a, b) => a.departmentName.localeCompare(b.departmentName),
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: 'Факультет',
      dataIndex: 'facultyName',
      key: 'facultyName',
      render: (name: string | null) => name || '—',
      filters: faculties.map(f => ({ text: f.name, value: f.name })),
      onFilter: (value, record) => record.facultyName === value,
    },
    {
      title: 'Викладачів',
      dataIndex: 'totalTeachers',
      key: 'totalTeachers',
      width: 110,
      sorter: (a, b) => a.totalTeachers - b.totalTeachers,
      render: (total: number, record) => (
        <Space size={4}>
          <TeamOutlined />
          <span>{total}</span>
          {record.partTimeTeachers > 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              (сум: {record.partTimeTeachers})
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'п.35: Зі ступенем (осн. м.р.)',
      key: 'point35',
      width: 220,
      sorter: (a, b) => a.withDegreeAndMainPercent - b.withDegreeAndMainPercent,
      render: (_, record) => {
        const percent = record.withDegreeAndMainPercent;
        const color = percent >= 50 ? '#52c41a' : percent >= 40 ? '#faad14' : '#f5222d';
        return (
          <Space direction="vertical" size={0} style={{ width: '100%' }}>
            <Text style={{ fontSize: 12 }}>
              {record.withDegreeAndMainCount}/{record.totalTeachers} ({percent}%)
            </Text>
            <Progress
              percent={percent}
              size="small"
              strokeColor={color}
              showInfo={false}
              style={{ width: 120 }}
            />
          </Space>
        );
      },
    },
    {
      title: 'п.38: Відповідність',
      key: 'point38',
      width: 200,
      render: (_, record) => (
        <Space size={4} wrap>
          {record.point38Compliant > 0 && (
            <Tag color="green">{record.point38Compliant}</Tag>
          )}
          {record.point38Warning > 0 && (
            <Tag color="orange">{record.point38Warning}</Tag>
          )}
          {record.point38NonCompliant > 0 && (
            <Tag color="red">{record.point38NonCompliant}</Tag>
          )}
          {record.point38Exempt > 0 && (
            <Tag color="blue">{record.point38Exempt}</Tag>
          )}
          {record.totalTeachers === 0 && (
            <Text type="secondary">—</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'overallStatus',
      key: 'overallStatus',
      width: 150,
      filters: [
        { text: 'Відповідає', value: 'GOOD' },
        { text: 'Потребує уваги', value: 'WARNING' },
        { text: 'Критичний', value: 'CRITICAL' },
      ],
      onFilter: (value, record) => record.overallStatus === value,
      render: (status: string) => {
        const cfg = overallStatusConfig[status] || { color: 'default', label: status };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        <BankOutlined /> Кафедри
      </Title>

      <div style={{ marginBottom: 16 }}>
        <Space>
          <Text>Факультет:</Text>
          <Select
            placeholder="Всі факультети"
            value={selectedFacultyId}
            onChange={setSelectedFacultyId}
            allowClear
            style={{ width: 400 }}
            options={faculties.map(f => ({ value: f.id, label: f.name }))}
          />
        </Space>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Всього кафедр"
              value={filtered.length}
              prefix={<BankOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Відповідають"
              value={goodCount}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Потребують уваги"
              value={warningCount}
              valueStyle={{ color: '#faad14' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Критичні"
              value={criticalCount}
              valueStyle={{ color: '#cf1322' }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Table
          dataSource={filtered}
          columns={columns}
          rowKey="departmentId"
          loading={loading}
          locale={{ emptyText: 'Немає даних' }}
          pagination={false}
          onRow={(record) => ({
            onClick: () => navigate(`/departments/${record.departmentId}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
};

export default DepartmentListPage;
