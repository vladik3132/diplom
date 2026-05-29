import React, { useEffect, useState } from 'react';
import { Table, Card, Space, Statistic, Row, Col, message, Button, Select, Tooltip, Popconfirm } from 'antd';
import { ReloadOutlined, ExperimentOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { ComplianceReport } from '@/types/achievement';
import { Department } from '@/types/teacher';
import {
  getComplianceAll,
  refreshComplianceAll,
  refreshComplianceForDepartment,
} from '@/api/achievementApi';
import { reclassifySubtypes, reclassifySubtypesForDepartment } from '@/api/publicationApi';
import { getDepartments } from '@/api/departmentApi';
import ComplianceBadge from '@/components/ComplianceBadge';
import { useAuth } from '@/auth/AuthContext';

const ComplianceReportPage: React.FC = () => {
  const { isAdmin, isHead, user } = useAuth();
  const [reports, setReports] = useState<ComplianceReport[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [reclassifying, setReclassifying] = useState(false);
  const [selectedDeptId, setSelectedDeptId] = useState<number | undefined>(undefined);

  const loadData = async (departmentId?: number) => {
    setLoading(true);
    try {
      const data = await getComplianceAll(departmentId);
      setReports(data);
    } catch {
      message.error('Помилка завантаження звіту');
    } finally {
      setLoading(false);
    }
  };

  const loadDepartments = async () => {
    try {
      const data = await getDepartments();
      setDepartments(data);
    } catch {
      // silent — фільтр не критичний
    }
  };

  useEffect(() => {
    loadData();
    if (isAdmin) loadDepartments();
    // HEAD за замовчуванням бачить тільки свою кафедру — фільтр не потрібен
    if (isHead && user?.departmentId) {
      setSelectedDeptId(user.departmentId);
      loadData(user.departmentId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDeptChange = (deptId: number | undefined) => {
    setSelectedDeptId(deptId);
    loadData(deptId);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      if (selectedDeptId) {
        const { refreshed } = await refreshComplianceForDepartment(selectedDeptId);
        message.success(`Перераховано ${refreshed} викладачів кафедри`);
      } else {
        const { refreshed } = await refreshComplianceAll();
        message.success(`Перераховано ${refreshed} викладачів`);
      }
      await loadData(selectedDeptId);
    } catch {
      message.error('Не вдалося перерахувати дані');
    } finally {
      setRefreshing(false);
    }
  };

  const handleReclassify = async () => {
    setReclassifying(true);
    try {
      if (selectedDeptId) {
        const { reclassified } = await reclassifySubtypesForDepartment(selectedDeptId);
        message.success(`Перекласифіковано ${reclassified} записів кафедри (subtypes + ppType + recompose)`);
      } else {
        const { reclassified } = await reclassifySubtypes();
        message.success(`Перекласифіковано ${reclassified} записів (subtypes + ppType + recompose)`);
      }
      await loadData(selectedDeptId);
    } catch {
      message.error('Не вдалося перекласифікувати');
    } finally {
      setReclassifying(false);
    }
  };

  // Refresh всім — тільки Admin. По кафедрі — Admin або Head.
  const canRefresh = selectedDeptId ? (isAdmin || isHead) : isAdmin;
  const canReclassify = canRefresh; // ті самі правила доступу

  const stats = {
    total: reports.length,
    compliant: reports.filter(r => r.status === 'COMPLIANT').length,
    warning: reports.filter(r => r.status === 'WARNING').length,
    nonCompliant: reports.filter(r => r.status === 'NON_COMPLIANT').length,
    exempt: reports.filter(r => r.status === 'EXEMPT').length,
  };

  const columns: ColumnsType<ComplianceReport> = [
    { title: 'Викладач', dataIndex: 'teacherName', key: 'teacherName', sorter: (a, b) => a.teacherName.localeCompare(b.teacherName) },
    {
      title: 'Статус', dataIndex: 'status', key: 'status', width: 180,
      render: (status: string) => <ComplianceBadge status={status as ComplianceReport['status']} />,
      filters: [
        { text: 'Відповідає', value: 'COMPLIANT' },
        { text: 'Попередження', value: 'WARNING' },
        { text: 'Не відповідає', value: 'NON_COMPLIANT' },
        { text: 'Стаж НПП менше 3 років', value: 'EXEMPT' },
      ],
      onFilter: (value, record) => record.status === value,
    },
    { title: 'К-сть досягнень', dataIndex: 'achievementCount', key: 'achievementCount', width: 150, sorter: (a, b) => a.achievementCount - b.achievementCount },
    { title: 'Унікальних типів', dataIndex: 'uniqueTypeCount', key: 'uniqueTypeCount', width: 150, sorter: (a, b) => a.uniqueTypeCount - b.uniqueTypeCount },
    { title: 'Типи досягнень', dataIndex: 'achievementTypes', key: 'achievementTypes', render: (types: string[]) => types?.join(', ') || '-' },
    { title: 'Причина звільнення', dataIndex: 'exemptionReason', key: 'exemptionReason', ellipsis: true },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Row gutter={16}>
        <Col span={5}><Card><Statistic title="Всього" value={stats.total} /></Card></Col>
        <Col span={5}><Card><Statistic title="Відповідає" value={stats.compliant} valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={5}><Card><Statistic title="Попередження" value={stats.warning} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={5}><Card><Statistic title="Не відповідає" value={stats.nonCompliant} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={4}><Card><Statistic title="Звільнені" value={stats.exempt} valueStyle={{ color: '#1890ff' }} /></Card></Col>
      </Row>

      <Card
        title="Звіт відповідності пункту 38"
        extra={
          <Space>
            {isAdmin && (
              <Select
                placeholder="Усі кафедри"
                allowClear
                showSearch
                optionFilterProp="label"
                style={{ width: 280 }}
                value={selectedDeptId}
                onChange={handleDeptChange}
                options={departments.map(d => ({
                  value: d.id,
                  label: d.name,
                }))}
              />
            )}
            {canReclassify && (
              <Tooltip title={selectedDeptId
                ? 'Перекласифікувати підтипи + ppType публікацій кафедри + перебудувати описи досягнень'
                : 'Перекласифікувати підтипи + ppType ВСІХ публікацій + перебудувати описи досягнень. Може зайняти час.'}
              >
                <Popconfirm
                  title={selectedDeptId ? 'Перекласифікувати кафедру?' : 'Перекласифікувати ВСІХ викладачів?'}
                  description={selectedDeptId
                    ? 'Оновлюються підтипи (METHODICAL/APPROBATION/POPULAR_SCIENTIFIC), ppType та описи досягнень для викладачів цієї кафедри.'
                    : 'Оновлюються підтипи, ppType та описи досягнень для ВСІХ викладачів. Може зайняти кілька хвилин при великій кількості публікацій.'}
                  onConfirm={handleReclassify}
                  okText="Так"
                  cancelText="Скасувати"
                >
                  <Button icon={<ExperimentOutlined />} loading={reclassifying}>
                    {selectedDeptId ? 'Перекласифікувати кафедру' : 'Перекласифікувати всі'}
                  </Button>
                </Popconfirm>
              </Tooltip>
            )}
            {canRefresh && (
              <Tooltip title={selectedDeptId
                ? 'Перерахувати compliance для викладачів цієї кафедри'
                : 'Перерахувати compliance для ВСІХ викладачів'}
              >
                <Button
                  icon={<ReloadOutlined />}
                  loading={refreshing}
                  onClick={handleRefresh}
                >
                  {selectedDeptId ? 'Перерахувати кафедру' : 'Перерахувати всім'}
                </Button>
              </Tooltip>
            )}
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={reports}
          rowKey="teacherId"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </Space>
  );
};

export default ComplianceReportPage;
