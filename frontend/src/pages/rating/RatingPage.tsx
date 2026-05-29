import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Select, Switch, Typography, Tag, message,
  Modal, Tabs, InputNumber, Drawer, Descriptions, Tooltip, Spin,
} from 'antd';
import {
  CalculatorOutlined, TrophyOutlined, TeamOutlined, BankOutlined,
  ReloadOutlined, PlusOutlined, EyeOutlined, FileWordOutlined, SettingOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { formatDate } from '../../utils/dateUtils';
import {
  getRatingPeriods, createRatingPeriodForYear, calculateAllRatings,
  getTeacherRankings, getTeacherRatingDetails, getDepartmentRankings, getFacultyRankings,
  getCriterionRecords,
  getRatingDepartmentSettings, updateRatingDepartmentSettings,
} from '../../api/ratingCalcApi';
import type {
  RatingPeriodDto, TeacherRatingSummary, TeacherRatingDetail, DepartmentRatingSummary, FacultyRatingSummary,
  CriterionRecord, RatingDepartmentSetting,
} from '../../api/ratingCalcApi';
import { getDepartments } from '../../api/departmentApi';
import type { Department } from '../../types/teacher';
import { useAuth } from '../../auth/AuthContext';

const { Title, Text } = Typography;

const RatingPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAdmin, isHead } = useAuth();
  const canCalculate = isAdmin || isHead;
  const [periods, setPeriods] = useState<RatingPeriodDto[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedDeptId, setSelectedDeptId] = useState<number | undefined>(undefined);
  const [activeTab, setActiveTab] = useState('teachers');

  // Data
  const [teacherRankings, setTeacherRankings] = useState<TeacherRatingSummary[]>([]);
  const [deptRankings, setDeptRankings] = useState<DepartmentRatingSummary[]>([]);
  const [facultyRankings, setFacultyRankings] = useState<FacultyRatingSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [calculating, setCalculating] = useState(false);

  // Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [selectedTeacher, setSelectedTeacher] = useState<TeacherRatingSummary | null>(null);

  // Criterion records (expandable rows in detail drawer)
  const [criterionRecords, setCriterionRecords] = useState<Record<string, CriterionRecord[]>>({});
  const [criterionLoading, setCriterionLoading] = useState<Record<string, boolean>>({});

  // New period modal
  const [periodModalOpen, setPeriodModalOpen] = useState(false);
  const [newYear, setNewYear] = useState<number>(new Date().getFullYear());

  // Department settings modal (admin only)
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [departmentSettings, setDepartmentSettings] = useState<RatingDepartmentSetting[]>([]);

  useEffect(() => {
    Promise.all([getRatingPeriods(), getDepartments()]).then(([p, d]) => {
      setPeriods(p);
      setDepartments(d);
      const active = p.find(pp => pp.active);
      if (active) setSelectedPeriodId(active.id);
      else if (p.length > 0) setSelectedPeriodId(p[0].id);
    });
  }, []);

  useEffect(() => {
    if (selectedPeriodId) {
      loadData();
    }
  }, [selectedPeriodId, selectedDeptId, activeTab]);

  const loadData = async () => {
    if (!selectedPeriodId) return;
    setLoading(true);
    try {
      if (activeTab === 'teachers') {
        const data = await getTeacherRankings(selectedPeriodId, selectedDeptId);
        setTeacherRankings(data);
      } else if (activeTab === 'departments') {
        const data = await getDepartmentRankings(selectedPeriodId);
        setDeptRankings(data);
      } else if (activeTab === 'faculties') {
        const data = await getFacultyRankings(selectedPeriodId);
        setFacultyRankings(data);
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const handleCalculateAll = async () => {
    if (!selectedPeriodId) return;
    setCalculating(true);
    try {
      const result = await calculateAllRatings(selectedPeriodId);
      message.success(`Рейтинг обраховано для ${result.teachersCalculated} викладачів`);
      loadData();
    } catch {
      message.error('Помилка обрахунку');
    } finally {
      setCalculating(false);
    }
  };

  const openDepartmentSettings = async () => {
    setSettingsOpen(true);
    setSettingsLoading(true);
    try {
      const data = await getRatingDepartmentSettings();
      setDepartmentSettings(data);
    } catch {
      message.error('Не вдалося завантажити налаштування');
    } finally {
      setSettingsLoading(false);
    }
  };

  const toggleDepartmentExcluded = (deptId: number, excluded: boolean) => {
    setDepartmentSettings(prev =>
      prev.map(d => (d.departmentId === deptId ? { ...d, ratingExcluded: excluded } : d)),
    );
  };

  const saveDepartmentSettings = async () => {
    setSettingsSaving(true);
    try {
      const excludedIds = departmentSettings
        .filter(d => d.ratingExcluded)
        .map(d => d.departmentId);
      const updated = await updateRatingDepartmentSettings(excludedIds);
      setDepartmentSettings(updated);
      message.success('Налаштування збережено');
      setSettingsOpen(false);
      // Перезавантажуємо рейтинги — щоб одразу врахувати виключення
      loadData();
    } catch {
      message.error('Помилка збереження');
    } finally {
      setSettingsSaving(false);
    }
  };

  const handleCreatePeriod = async () => {
    try {
      const created = await createRatingPeriodForYear(newYear);
      message.success(`Створено період ${created.name}`);
      const updated = await getRatingPeriods();
      setPeriods(updated);
      setSelectedPeriodId(created.id);
      setPeriodModalOpen(false);
    } catch {
      message.error('Помилка створення періоду');
    }
  };

  const handleViewDetails = async (teacherId: number) => {
    if (!selectedPeriodId) return;
    setDrawerLoading(true);
    setDrawerOpen(true);
    setCriterionRecords({});
    setCriterionLoading({});
    try {
      const data = await getTeacherRatingDetails(selectedPeriodId, teacherId);
      setSelectedTeacher(data);
    } catch {
      message.error('Помилка завантаження');
    } finally {
      setDrawerLoading(false);
    }
  };

  const handleExpandCriterion = async (expanded: boolean, record: TeacherRatingDetail) => {
    if (!expanded || !selectedPeriodId || !selectedTeacher) return;
    if (criterionRecords[record.criterion]) return; // already loaded
    setCriterionLoading(prev => ({ ...prev, [record.criterion]: true }));
    try {
      const records = await getCriterionRecords(selectedPeriodId, selectedTeacher.teacherId, record.criterion);
      setCriterionRecords(prev => ({ ...prev, [record.criterion]: records }));
    } catch {
      message.error('Помилка завантаження записів');
    } finally {
      setCriterionLoading(prev => ({ ...prev, [record.criterion]: false }));
    }
  };

  // ── Teacher columns ──

  const teacherColumns: ColumnsType<TeacherRatingSummary> = [
    {
      title: '#', dataIndex: 'rank', key: 'rank', width: 60,
      render: (rank: number) => {
        if (rank === 1) return <Tag color="gold"><TrophyOutlined /> 1</Tag>;
        if (rank === 2) return <Tag color="default" style={{ color: '#888' }}>2</Tag>;
        if (rank === 3) return <Tag color="default" style={{ color: '#cd7f32' }}>3</Tag>;
        return rank;
      },
    },
    { title: 'Викладач', dataIndex: 'teacherName', key: 'teacherName',
      render: (name: string, record) => (
        <a onClick={() => handleViewDetails(record.teacherId)}>{name}</a>
      ),
    },
    { title: 'Кафедра', dataIndex: 'departmentName', key: 'departmentName', width: 200 },
    {
      title: 'Бали', dataIndex: 'totalScore', key: 'totalScore', width: 100,
      sorter: (a, b) => a.totalScore - b.totalScore,
      defaultSortOrder: 'descend',
      render: (score: number) => <Text strong style={{ fontSize: 16 }}>{score}</Text>,
    },
    {
      title: '', key: 'actions', width: 50,
      render: (_, record) => (
        <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => handleViewDetails(record.teacherId)} />
      ),
    },
  ];

  // ── Department columns ──

  const deptColumns: ColumnsType<DepartmentRatingSummary> = [
    {
      title: '#', dataIndex: 'rank', key: 'rank', width: 60,
      render: (rank: number) => {
        if (rank === 1) return <Tag color="gold"><TrophyOutlined /> 1</Tag>;
        if (rank === 2) return <Tag color="default" style={{ color: '#888' }}>2</Tag>;
        if (rank === 3) return <Tag color="default" style={{ color: '#cd7f32' }}>3</Tag>;
        return rank;
      },
    },
    { title: 'Кафедра', dataIndex: 'departmentName', key: 'departmentName' },
    { title: 'Факультет', dataIndex: 'facultyName', key: 'facultyName', width: 200 },
    { title: 'Викладачів', dataIndex: 'teacherCount', key: 'teacherCount', width: 100 },
    {
      title: 'Сума балів', dataIndex: 'totalScore', key: 'totalScore', width: 120,
      render: (v: number) => <Text strong>{v}</Text>,
    },
    {
      title: 'Середній бал', dataIndex: 'averageScore', key: 'averageScore', width: 120,
      sorter: (a, b) => a.averageScore - b.averageScore,
      defaultSortOrder: 'descend',
      render: (v: number) => <Text strong style={{ fontSize: 16 }}>{v.toFixed(1)}</Text>,
    },
  ];

  // ── Faculty columns ──

  const facultyColumns: ColumnsType<FacultyRatingSummary> = [
    {
      title: '#', dataIndex: 'rank', key: 'rank', width: 60,
      render: (rank: number) => {
        if (rank === 1) return <Tag color="gold"><TrophyOutlined /> 1</Tag>;
        if (rank === 2) return <Tag color="default" style={{ color: '#888' }}>2</Tag>;
        if (rank === 3) return <Tag color="default" style={{ color: '#cd7f32' }}>3</Tag>;
        return rank;
      },
    },
    { title: 'Факультет', dataIndex: 'facultyName', key: 'facultyName' },
    { title: 'Кафедр', dataIndex: 'departmentCount', key: 'departmentCount', width: 80 },
    { title: 'Викладачів', dataIndex: 'teacherCount', key: 'teacherCount', width: 100 },
    {
      title: 'Сума балів', dataIndex: 'totalScore', key: 'totalScore', width: 120,
      render: (v: number) => <Text strong>{v}</Text>,
    },
    {
      title: 'Середній бал', dataIndex: 'averageScore', key: 'averageScore', width: 120,
      sorter: (a, b) => a.averageScore - b.averageScore,
      defaultSortOrder: 'descend',
      render: (v: number) => <Text strong style={{ fontSize: 16 }}>{v.toFixed(1)}</Text>,
    },
  ];

  // ── Detail columns ──

  const detailColumns: ColumnsType<TeacherRatingDetail> = [
    { title: 'Критерій', dataIndex: 'criterionLabel', key: 'criterionLabel' },
    { title: 'Кількість', dataIndex: 'count', key: 'count', width: 90, align: 'center' },
    { title: 'Бал/од.', dataIndex: 'pointsPerUnit', key: 'pointsPerUnit', width: 80, align: 'center' },
    {
      title: 'Бали', dataIndex: 'score', key: 'score', width: 80, align: 'center',
      render: (v: number) => <Text strong>{v}</Text>,
    },
  ];

  const selectedPeriod = periods.find(p => p.id === selectedPeriodId);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>Рейтинг НПП</Title>
        <Space>
          <Select
            value={selectedPeriodId ?? undefined}
            onChange={setSelectedPeriodId}
            style={{ width: 200 }}
            placeholder="Оберіть період"
            options={periods.map(p => ({
              value: p.id,
              label: `${p.name}${p.active ? ' (активний)' : ''}`,
            }))}
          />
          {canCalculate && (
            <Tooltip title="Створити новий рейтинговий період">
              <Button icon={<PlusOutlined />} onClick={() => setPeriodModalOpen(true)} />
            </Tooltip>
          )}
          {isAdmin && (
            <Tooltip title="Налаштування кафедр у рейтингу">
              <Button
                icon={<SettingOutlined />}
                onClick={openDepartmentSettings}
              />
            </Tooltip>
          )}
          {isAdmin && (
            <Button
              icon={<FileWordOutlined />}
              onClick={() => navigate('/rating/protocol')}
              disabled={!selectedPeriodId}
            >
              Протокол
            </Button>
          )}
          {canCalculate ? (
            <Button
              type="primary"
              icon={<CalculatorOutlined />}
              loading={calculating}
              onClick={handleCalculateAll}
              disabled={!selectedPeriodId}
            >
              Обрахувати рейтинг
            </Button>
          ) : (
            <Tooltip title="Перерахувати рейтинг може адміністратор або начальник кафедри">
              <Tag color="blue" style={{ fontSize: 13, padding: '4px 12px' }}>Тільки перегляд</Tag>
            </Tooltip>
          )}
        </Space>
      </div>

      {selectedPeriod && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          Період: {formatDate(selectedPeriod.startDate)} – {formatDate(selectedPeriod.endDate)}
        </Text>
      )}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'teachers',
          label: <span><TeamOutlined /> Викладачі</span>,
          children: (
            <>
              <div style={{ marginBottom: 12 }}>
                <Select
                  allowClear
                  placeholder="Фільтр по кафедрі"
                  value={selectedDeptId}
                  onChange={setSelectedDeptId}
                  style={{ width: 300 }}
                  showSearch
                  filterOption={(input, option) =>
                    (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                  }
                  options={departments.map(d => ({ value: d.id, label: d.name }))}
                />
                <Button icon={<ReloadOutlined />} onClick={loadData} style={{ marginLeft: 8 }}>
                  Оновити
                </Button>
              </div>
              <Table
                dataSource={teacherRankings}
                columns={teacherColumns}
                rowKey="teacherId"
                loading={loading}
                pagination={{ pageSize: 50 }}
                size="middle"
                locale={{ emptyText: 'Рейтинг ще не обраховано. Натисніть "Обрахувати рейтинг".' }}
              />
            </>
          ),
        },
        {
          key: 'departments',
          label: <span><TeamOutlined /> Кафедри</span>,
          children: (
            <Table
              dataSource={deptRankings}
              columns={deptColumns}
              rowKey="departmentId"
              loading={loading}
              pagination={false}
              size="middle"
              expandable={{
                expandedRowRender: (record) => (
                  <Table
                    dataSource={record.teachers}
                    columns={[
                      { title: '#', dataIndex: 'rank', key: 'rank', width: 50 },
                      {
                        title: 'Викладач', dataIndex: 'teacherName', key: 'teacherName',
                        render: (name: string, rec) => <a onClick={() => handleViewDetails(rec.teacherId)}>{name}</a>,
                      },
                      { title: 'Бали', dataIndex: 'totalScore', key: 'totalScore', width: 80 },
                    ]}
                    rowKey="teacherId"
                    pagination={false}
                    size="small"
                  />
                ),
              }}
              locale={{ emptyText: 'Рейтинг ще не обраховано' }}
            />
          ),
        },
        {
          key: 'faculties',
          label: <span><BankOutlined /> Факультети</span>,
          children: (
            <Table
              dataSource={facultyRankings}
              columns={facultyColumns}
              rowKey="facultyId"
              loading={loading}
              pagination={false}
              size="middle"
              expandable={{
                expandedRowRender: (record) => (
                  <Table
                    dataSource={record.departments}
                    columns={[
                      { title: '#', dataIndex: 'rank', key: 'rank', width: 50 },
                      { title: 'Кафедра', dataIndex: 'departmentName', key: 'departmentName' },
                      { title: 'Викладачів', dataIndex: 'teacherCount', key: 'teacherCount', width: 100 },
                      { title: 'Сума', dataIndex: 'totalScore', key: 'totalScore', width: 100 },
                      {
                        title: 'Середній', dataIndex: 'averageScore', key: 'averageScore', width: 100,
                        render: (v: number) => v?.toFixed(1),
                      },
                    ]}
                    rowKey="departmentId"
                    pagination={false}
                    size="small"
                  />
                ),
              }}
              locale={{ emptyText: 'Рейтинг ще не обраховано' }}
            />
          ),
        },
      ]} />

      {/* Drawer: деталі викладача */}
      <Drawer
        title={selectedTeacher ? `Рейтинг: ${selectedTeacher.teacherName}` : 'Деталі'}
        placement="right"
        width={560}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setSelectedTeacher(null); }}
      >
        {drawerLoading ? (
          <Spin style={{ display: 'block', margin: '40px auto' }} />
        ) : selectedTeacher ? (
          <>
            <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Кафедра">{selectedTeacher.departmentName}</Descriptions.Item>
              <Descriptions.Item label="Загальний бал">
                <Text strong style={{ fontSize: 18 }}>{selectedTeacher.totalScore}</Text>
              </Descriptions.Item>
            </Descriptions>
            <Table
              dataSource={selectedTeacher.details}
              columns={detailColumns}
              rowKey="criterion"
              pagination={false}
              size="small"
              expandable={{
                expandedRowRender: (record) => {
                  const records = criterionRecords[record.criterion];
                  const isLoading = criterionLoading[record.criterion];
                  if (isLoading) return <Spin size="small" style={{ display: 'block', margin: '8px auto' }} />;
                  if (!records || records.length === 0) return <Text type="secondary">Немає записів</Text>;
                  return (
                    <div style={{ padding: '4px 0' }}>
                      {records.map((r, i) => (
                        <div key={r.id + '-' + i} style={{ padding: '4px 0', borderBottom: i < records.length - 1 ? '1px solid #f0f0f0' : undefined }}>
                          <Text>{r.title}</Text>
                          {r.subtitle && <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>({r.subtitle})</Text>}
                        </div>
                      ))}
                    </div>
                  );
                },
                onExpand: handleExpandCriterion,
              }}
              summary={(data) => {
                const total = data.reduce((sum, d) => sum + d.score, 0);
                return (
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0} />{/* expand column */}
                    <Table.Summary.Cell index={1} colSpan={3}><Text strong>Разом</Text></Table.Summary.Cell>
                    <Table.Summary.Cell index={4} align="center"><Text strong>{total}</Text></Table.Summary.Cell>
                  </Table.Summary.Row>
                );
              }}
            />
          </>
        ) : null}
      </Drawer>

      {/* Modal: новий період */}
      <Modal
        title="Створити рейтинговий період"
        open={periodModalOpen}
        onOk={handleCreatePeriod}
        onCancel={() => setPeriodModalOpen(false)}
        okText="Створити"
        cancelText="Скасувати"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>Оберіть рік завершення періоду:</Text>
          <InputNumber
            value={newYear}
            onChange={(v) => setNewYear(v ?? new Date().getFullYear())}
            min={2020}
            max={2035}
            style={{ width: '100%' }}
          />
          <Text type="secondary">
            Буде створено період: 21.06.{newYear - 1} – 20.06.{newYear}
          </Text>
        </Space>
      </Modal>

      {/* Modal: налаштування кафедр (admin only) */}
      <Modal
        title="Кафедри у рейтингу"
        open={settingsOpen}
        onOk={saveDepartmentSettings}
        onCancel={() => setSettingsOpen(false)}
        okText="Зберегти"
        cancelText="Скасувати"
        confirmLoading={settingsSaving}
        width={720}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          Викладачі виключених кафедр (наприклад, віртуальних кафедр управління 0/888)
          не беруть участь у рейтингу. Зміни застосуються до наступного перегляду / обрахунку.
        </Text>
        <Table
          dataSource={departmentSettings}
          rowKey="departmentId"
          loading={settingsLoading}
          pagination={false}
          size="small"
          columns={[
            { title: '№', dataIndex: 'number', key: 'number', width: 70 },
            { title: 'Кафедра', dataIndex: 'name', key: 'name' },
            { title: 'Факультет', dataIndex: 'facultyName', key: 'facultyName', width: 200 },
            {
              title: 'У рейтингу',
              key: 'inRating',
              width: 110,
              align: 'center',
              render: (_, record) => (
                <Switch
                  checked={!record.ratingExcluded}
                  onChange={(checked) => toggleDepartmentExcluded(record.departmentId, !checked)}
                />
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
};

export default RatingPage;
