import React, { useEffect, useState, useMemo } from 'react';
import {
  Table, Button, Space, Card, Popconfirm, message, Select, Upload, Tag,
  Typography, Drawer, Descriptions, Divider, InputNumber, Modal, Form, Input,
} from 'antd';
import { DeleteOutlined, UploadOutlined, PlusOutlined, EditOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { Discipline } from '@/types/discipline';
import {
  getDisciplines, deleteDiscipline, importDisciplinesExcel,
  createDiscipline, updateDiscipline,
} from '@/api/disciplineApi';
import { getEducationalPrograms } from '@/api/educationalProgramApi';
import { getDepartments } from '@/api/departmentApi';
import type { EducationalProgram } from '@/types/educationalProgram';
import { useAuth } from '@/auth/AuthContext';

const { Title, Text } = Typography;

interface DepartmentOption {
  id: number;
  name: string;
  number?: string;
}

const DisciplineListPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isHead = user?.role === 'HEAD_OF_DEPARTMENT';
  const canEdit = isAdmin || isHead;

  const [disciplines, setDisciplines] = useState<Discipline[]>([]);
  const [programs, setPrograms] = useState<EducationalProgram[]>([]);
  const [departments, setDepartments] = useState<DepartmentOption[]>([]);
  const [selectedProgramId, setSelectedProgramId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [importYear, setImportYear] = useState<number>(new Date().getFullYear());
  const [importLevel, setImportLevel] = useState<string | undefined>();
  const [importForm, setImportForm] = useState<string | undefined>();

  // Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<Discipline | null>(null);

  // Modal create/edit
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    getEducationalPrograms().then(setPrograms).catch(() => {});
    getDepartments().then(setDepartments).catch(() => {});
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await getDisciplines(
        selectedProgramId ? { programId: selectedProgramId } : undefined
      );
      setDisciplines(data);
    } catch {
      message.error('Помилка завантаження дисциплін');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [selectedProgramId]);

  const handleDelete = async (id: number) => {
    await deleteDiscipline(id);
    message.success('Дисципліну видалено');
    loadData();
  };

  const handleImport: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const result = await importDisciplinesExcel(file as File, importYear, importLevel, importForm);
      message.success(`Імпортовано ${result.imported} дисциплін`);
      onSuccess?.(result);
      loadData();
    } catch (err: any) {
      message.error('Помилка імпорту дисциплін');
      onError?.(err);
    } finally {
      setUploading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: Discipline) => {
    setEditingId(record.id);
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      departmentId: record.department?.id,
      educationalProgramId: record.educationalProgram?.id,
      credits: record.credits,
      totalHours: record.totalHours,
      auditoryHours: record.auditoryHours,
      hoursLecture: record.hoursLecture,
      hoursGroup: record.hoursGroup,
      hoursPractical: record.hoursPractical,
      hoursLab: record.hoursLab,
      hoursSelfStudy: record.hoursSelfStudy,
      examSemesters: record.examSemesters,
      creditSemesters: record.creditSemesters,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      const payload: any = {
        name: values.name,
        code: values.code || null,
        department: values.departmentId ? { id: values.departmentId } : null,
        educationalProgram: values.educationalProgramId ? { id: values.educationalProgramId } : null,
        credits: values.credits ?? null,
        totalHours: values.totalHours ?? null,
        auditoryHours: values.auditoryHours ?? null,
        hoursLecture: values.hoursLecture ?? null,
        hoursGroup: values.hoursGroup ?? null,
        hoursPractical: values.hoursPractical ?? null,
        hoursLab: values.hoursLab ?? null,
        hoursSelfStudy: values.hoursSelfStudy ?? null,
        examSemesters: values.examSemesters || null,
        creditSemesters: values.creditSemesters || null,
      };

      if (editingId) {
        await updateDiscipline(editingId, payload);
        message.success('Дисципліну оновлено');
      } else {
        await createDiscipline(payload);
        message.success('Дисципліну створено');
      }

      setModalOpen(false);
      loadData();
    } catch {
      // validation error
    } finally {
      setSaving(false);
    }
  };

  // Parse JSON safely
  const parseJson = (str?: string) => {
    if (!str) return null;
    try { return JSON.parse(str); } catch { return null; }
  };

  // Program options
  const programOptions = useMemo(() => {
    return programs.map(p => ({
      value: p.id,
      label: `${p.shortCode || ''} — ${p.name}`,
    }));
  }, [programs]);

  // Department options
  const departmentOptions = useMemo(() => {
    return departments.map(d => ({
      value: d.id,
      label: `${d.number || ''} — ${d.name}`,
    }));
  }, [departments]);

  const columns: ColumnsType<Discipline> = [
    { title: 'Код', dataIndex: 'code', key: 'code', width: 90 },
    { title: 'Назва дисципліни', dataIndex: 'name', key: 'name', width: 260, ellipsis: true },
    {
      title: 'Спеціальність', key: 'specialty', ellipsis: true,
      render: (_, r) => r.educationalProgram?.specialty || '—',
    },
    {
      title: 'Кафедра', key: 'department', width: 80,
      render: (_, r) => r.department?.number || '—',
    },
    { title: 'Кредити', dataIndex: 'credits', key: 'credits', width: 80 },
    { title: 'Годин', dataIndex: 'totalHours', key: 'totalHours', width: 70 },
    {
      title: 'Іспити', dataIndex: 'examSemesters', key: 'examSemesters', width: 80,
      render: (v: string) => v || '—',
    },
    {
      title: 'Заліки', dataIndex: 'creditSemesters', key: 'creditSemesters', width: 80,
      render: (v: string) => v || '—',
    },
    ...(canEdit ? [{
      title: 'Дії', key: 'actions', width: 90,
      render: (_: any, record: Discipline) => (
        <Space size={4}>
          <Button
            icon={<EditOutlined />}
            size="small"
            onClick={(e) => { e.stopPropagation(); openEdit(record); }}
          />
          {isAdmin && (
            <Popconfirm title="Видалити дисципліну?" onConfirm={() => handleDelete(record.id)}>
              <Button icon={<DeleteOutlined />} size="small" danger onClick={(e) => e.stopPropagation()} />
            </Popconfirm>
          )}
        </Space>
      ),
    }] : []),
  ];

  // Drawer content
  const renderDrawer = () => {
    if (!selectedRecord) return null;
    const r = selectedRecord;
    const hoursBySem = parseJson(r.hoursBySemester);
    const creditsBySem = parseJson(r.creditsBySemester);
    const controlTypes = parseJson(r.controlTypes);

    return (
      <>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="Код компоненти">{r.code || '—'}</Descriptions.Item>
          <Descriptions.Item label="Назва">{r.name}</Descriptions.Item>
          <Descriptions.Item label="ОПП">
            {r.educationalProgram
              ? `${r.educationalProgram.shortCode || ''} — ${r.educationalProgram.name}`
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Кафедра">
            {r.department
              ? `${r.department.number || ''} — ${r.department.name}`
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Кредити ЄКТС">{r.credits ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Загальний обсяг годин">{r.totalHours ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Аудиторних годин">{r.auditoryHours ?? '—'}</Descriptions.Item>
        </Descriptions>

        <Divider orientation="left" style={{ fontSize: 13 }}>Розподіл годин</Divider>
        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="Лекції">{r.hoursLecture ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Групові">{r.hoursGroup ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Практичні">{r.hoursPractical ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Лабораторні">{r.hoursLab ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Самостійна підготовка" span={2}>{r.hoursSelfStudy ?? '—'}</Descriptions.Item>
        </Descriptions>

        <Divider orientation="left" style={{ fontSize: 13 }}>Контроль</Divider>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="Іспити (семестри)">{r.examSemesters || '—'}</Descriptions.Item>
          <Descriptions.Item label="Заліки (семестри)">{r.creditSemesters || '—'}</Descriptions.Item>
          {controlTypes && (
            <Descriptions.Item label="Типи контролю">
              <Space>
                {(controlTypes as string[]).map((t: string) => (
                  <Tag key={t}>{t}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          )}
        </Descriptions>

        {hoursBySem && Object.keys(hoursBySem).length > 0 && (
          <>
            <Divider orientation="left" style={{ fontSize: 13 }}>Години по семестрах</Divider>
            <Descriptions column={4} size="small" bordered>
              {Object.entries(hoursBySem).map(([sem, hrs]) => (
                <Descriptions.Item key={sem} label={`Сем. ${sem}`}>
                  {hrs as number}
                </Descriptions.Item>
              ))}
            </Descriptions>
          </>
        )}

        {creditsBySem && Object.keys(creditsBySem).length > 0 && (
          <>
            <Divider orientation="left" style={{ fontSize: 13 }}>Кредити по семестрах</Divider>
            <Descriptions column={4} size="small" bordered>
              {Object.entries(creditsBySem).map(([sem, cr]) => (
                <Descriptions.Item key={sem} label={`Сем. ${sem}`}>
                  {cr as number}
                </Descriptions.Item>
              ))}
            </Descriptions>
          </>
        )}

        {canEdit && (
          <>
            <Divider />
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => { setDrawerOpen(false); openEdit(r); }}
            >
              Редагувати
            </Button>
          </>
        )}
      </>
    );
  };

  return (
    <div>
      <Title level={4}>Дисципліни</Title>

      {/* Import section — admin only */}
      {isAdmin && (
        <Card size="small" title="Імпорт НП" style={{ marginBottom: 16 }}>
          <Space>
            <Text strong>Рік набору:</Text>
            <InputNumber
              value={importYear}
              onChange={(val) => setImportYear(val || new Date().getFullYear())}
              min={2020}
              max={2035}
              style={{ width: 90 }}
            />
            <Text strong>Рівень:</Text>
            <Select
              style={{ width: 200 }}
              placeholder="Всі рівні"
              allowClear
              value={importLevel}
              onChange={setImportLevel}
              options={[
                { value: 'бакалавр', label: 'Бакалавр' },
                { value: 'магістр', label: 'Магістр' },
                { value: 'доктор', label: 'Доктор філософії' },
              ]}
            />
            <Text strong>Форма:</Text>
            <Select
              style={{ width: 140 }}
              placeholder="Всі форми"
              allowClear
              value={importForm}
              onChange={setImportForm}
              options={[
                { value: 'денна', label: 'Денна' },
                { value: 'заочна', label: 'Заочна' },
              ]}
            />
            <Upload
              accept=".xlsx,.xls"
              showUploadList={false}
              customRequest={handleImport}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>
                Завантажити Excel (НП)
              </Button>
            </Upload>
            <Text type="secondary">
              Дисципліни прив'яжуться до ОПП обраного року, рівня та форми
            </Text>
          </Space>
        </Card>
      )}

      {/* Filters */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space>
          <Text strong>ОПП:</Text>
          <Select
            style={{ width: 450 }}
            placeholder="Всі програми"
            allowClear
            showSearch
            optionFilterProp="label"
            options={programOptions}
            value={selectedProgramId}
            onChange={(val) => setSelectedProgramId(val)}
          />
          <Text type="secondary">Всього: {disciplines.length}</Text>
          {canEdit && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Додати дисципліну
            </Button>
          )}
        </Space>
      </Card>

      {/* Table */}
      <Card size="small">
        <Table
          columns={columns}
          dataSource={disciplines}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{
            pageSize: 50,
            showSizeChanger: false,
            showTotal: (total) => `Всього: ${total}`,
          }}
          onRow={(record) => ({
            onClick: (e) => {
              if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
              setSelectedRecord(record);
              setDrawerOpen(true);
            },
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      {/* Detail Drawer */}
      <Drawer
        title={selectedRecord?.name || 'Деталі дисципліни'}
        placement="right"
        width={520}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {renderDrawer()}
      </Drawer>

      {/* Create/Edit Modal */}
      <Modal
        title={editingId ? 'Редагувати дисципліну' : 'Нова дисципліна'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={form} layout="vertical" size="small">
          <Form.Item name="name" label="Назва дисципліни" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>
          <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
            <Form.Item name="code" label="Код компоненти" style={{ flex: 1 }}>
              <Input placeholder="ОК 6" />
            </Form.Item>
            <Form.Item name="educationalProgramId" label="ОПП" style={{ flex: 2 }}>
              <Select
                placeholder="Оберіть ОПП"
                allowClear
                showSearch
                optionFilterProp="label"
                options={programOptions}
              />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
            <Form.Item name="departmentId" label="Кафедра" style={{ flex: 1 }}>
              <Select
                placeholder="Оберіть кафедру"
                allowClear
                showSearch
                optionFilterProp="label"
                options={departmentOptions}
              />
            </Form.Item>
          </Space>
          <Divider orientation="left" style={{ fontSize: 13, margin: '8px 0' }}>Обсяги</Divider>
          <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
            <Form.Item name="credits" label="Кредити ЄКТС">
              <InputNumber style={{ width: '100%' }} min={0} step={0.5} />
            </Form.Item>
            <Form.Item name="totalHours" label="Загальний обсяг годин">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
            <Form.Item name="auditoryHours" label="Аудиторних">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
            <Form.Item name="hoursLecture" label="Лекції">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
            <Form.Item name="hoursGroup" label="Групові">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
            <Form.Item name="hoursPractical" label="Практичні">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
            <Form.Item name="hoursLab" label="Лабораторні">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
          </Space>
          <Form.Item name="hoursSelfStudy" label="Самостійна підготовка">
            <InputNumber style={{ width: 150 }} min={0} />
          </Form.Item>
          <Divider orientation="left" style={{ fontSize: 13, margin: '8px 0' }}>Контроль</Divider>
          <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
            <Form.Item name="examSemesters" label="Іспити (семестри)">
              <Input placeholder="1 3" />
            </Form.Item>
            <Form.Item name="creditSemesters" label="Заліки (семестри)">
              <Input placeholder="2" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
};

export default DisciplineListPage;
