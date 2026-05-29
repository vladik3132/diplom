import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useParams } from 'react-router-dom';
import {
  Tabs, Descriptions, Spin, Typography, Tag, Table, message, Card,
  Button, Modal, Form, Select, InputNumber, Input, Space, Popconfirm,
  DatePicker, Switch, AutoComplete, Drawer, Tooltip,
} from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, ExclamationCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { parseDate, formatDate, formatDatesInText } from '../../utils/dateUtils';
import { Teacher, Education, MilitaryEducation, CareerRecord, LanguageSkill, AcademicDegree, AcademicTitle } from '../../types/teacher';
import { Discipline, TeacherDiscipline } from '../../types/discipline';
import { ComplianceReport } from '../../types/achievement';
import {
  getTeacher, updateTeacher,
  getEducations, createEducation, updateEducation, deleteEducation,
  getAcademicDegrees, createAcademicDegree, updateAcademicDegree, deleteAcademicDegree,
  getAcademicTitles, createAcademicTitle, updateAcademicTitle, deleteAcademicTitle,
  getMilitaryEducations, createMilitaryEducation, updateMilitaryEducation, deleteMilitaryEducation,
  getCareerRecords, createCareerRecord, updateCareerRecord, deleteCareerRecord,
  getLanguageSkills, createLanguageSkill, updateLanguageSkill, deleteLanguageSkill,
} from '../../api/teacherApi';
import {
  getDisciplines,
  getTeacherDisciplines,
  assignTeacherDiscipline,
  removeTeacherDiscipline,
} from '../../api/disciplineApi';
import { getComplianceByTeacher } from '../../api/achievementApi';
import { getDepartments } from '../../api/departmentApi';
import { getQualifications, createQualification, updateQualification, deleteQualification } from '../../api/qualificationApi';
import { MILITARY_COURSE_LEVEL_LABELS } from '@/types/qualification';
import type { MilitaryCourseLevel } from '@/types/qualification';
import type { Department } from '../../types/teacher';
import PublicationsPage from '../publications/PublicationsPage';
import AchievementsPage from '../achievements/AchievementsPage';
import QualificationPage from '../qualification/QualificationPage';
import PpDataTabs from '../ppdata/PpDataTabs';
import OtherActivityTab from './OtherActivityTab';
import FileAttachmentList from '@/components/FileAttachmentList';
import type { FileAttachmentListRef } from '@/components/FileAttachmentList';
import { useAuth } from '@/auth/AuthContext';

const { Title, Text } = Typography;
const { TextArea } = Input;

// POSITION_OPTIONS — більше не використовується: посада редагується тільки у розділі «Штат».
// Зберігаємо тут як коментар на випадок повернення до інлайн-редагування у профілі.
// const POSITION_OPTIONS = ['Начальник кафедри', 'Заступник начальника кафедри', 'Професор',
//                           'Доцент', 'Старший викладач', 'Викладач', 'Асистент'];

const MILITARY_RANK_OPTIONS = [
  'генерал-лейтенант',
  'генерал-майор',
  'полковник',
  'підполковник',
  'майор',
  'капітан',
  'старший лейтенант',
  'лейтенант',
  'старший прапорщик',
  'прапорщик',
  'працівник ЗСУ',
];

/* ── Disciplines tab (teacher-specific) ─────────────────────── */
const TeacherDisciplinesTab: React.FC<{ teacherId: number }> = ({ teacherId }) => {
  // Дисципліни сетапляться у меню ОПП начальником кафедри/адміністратором.
  // TEACHER бачить вкладку лише для перегляду — без кнопки додавання та колонки «Дії».
  const { isTeacher } = useAuth();
  const readOnly = isTeacher;
  const [disciplines, setDisciplines] = useState<TeacherDiscipline[]>([]);
  const [allDisciplines, setAllDisciplines] = useState<Discipline[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchData = () => {
    setLoading(true);
    getTeacherDisciplines(teacherId)
      .then(setDisciplines)
      .catch(() => message.error('Помилка завантаження дисциплін'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchData();
    getDisciplines().then(setAllDisciplines).catch(() => {});
  }, [teacherId]);

  const handleAdd = () => {
    form.resetFields();
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await assignTeacherDiscipline({
        teacher: { id: teacherId } as any,
        discipline: { id: values.disciplineId } as any,
        academicYear: values.academicYear || undefined,
        semester: values.semester || undefined,
      });
      message.success('Дисципліну призначено');
      setModalOpen(false);
      fetchData();
    } catch (err: any) {
      if (err?.errorFields) return; // validation
      message.error('Помилка збереження');
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (id: number) => {
    try {
      await removeTeacherDiscipline(id);
      message.success('Призначення видалено');
      fetchData();
    } catch {
      message.error('Помилка видалення');
    }
  };

  // Фільтруємо вже призначені
  const assignedIds = new Set(disciplines.map(d => d.discipline?.id));
  const availableDisciplines = allDisciplines.filter(d => !assignedIds.has(d.id));

  const columns: ColumnsType<TeacherDiscipline> = [
    {
      title: 'Дисципліна', key: 'name',
      render: (_, r) => r.discipline?.name || '—',
      sorter: (a, b) => (a.discipline?.name || '').localeCompare(b.discipline?.name || ''),
    },
    {
      title: 'Код', key: 'code', width: 100,
      render: (_, r) => r.discipline?.code || '—',
    },
    { title: 'Навчальний рік', dataIndex: 'academicYear', key: 'academicYear', width: 150 },
    {
      title: 'Семестр', dataIndex: 'semester', key: 'semester', width: 100,
      render: (v: number) => v ? `${v}-й` : '—',
    },
    {
      title: 'Кредити', key: 'credits', width: 100,
      render: (_, r) => r.discipline?.credits ?? '—',
    },
    {
      title: 'Години (Л/П/Лаб)', key: 'hours', width: 140,
      render: (_, r) => {
        const d = r.discipline;
        if (!d) return '—';
        return `${d.hoursLecture ?? 0}/${d.hoursPractical ?? 0}/${d.hoursLab ?? 0}`;
      },
    },
    ...(readOnly ? [] : [{
      title: 'Дії', key: 'actions', width: 70, align: 'center' as const,
      render: (_: unknown, r: TeacherDiscipline) => (
        <Popconfirm title="Видалити призначення?" onConfirm={() => handleRemove(r.id)} okText="Так" cancelText="Ні">
          <Button type="text" danger icon={<DeleteOutlined />} size="small" />
        </Popconfirm>
      ),
    }]),
  ];

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        {readOnly ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            <InfoCircleOutlined style={{ marginRight: 6 }} />
            Дисципліни призначаються начальником кафедри через меню «ОПП». Тут — лише перегляд.
          </Text>
        ) : (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            Призначити дисципліну
          </Button>
        )}
      </div>
      <Table
        dataSource={disciplines}
        columns={columns}
        rowKey="id"
        loading={loading}
        locale={{ emptyText: 'Немає призначених дисциплін' }}
        pagination={false}
        size="middle"
      />

      <Modal
        title="Призначити дисципліну"
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        okText="Зберегти"
        cancelText="Скасувати"
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="disciplineId"
            label="Дисципліна"
            rules={[{ required: true, message: 'Оберіть дисципліну' }]}
          >
            <Select
              placeholder="Оберіть дисципліну"
              showSearch
              optionFilterProp="label"
              options={availableDisciplines.map(d => ({
                value: d.id,
                label: `${d.name}${d.code ? ` (${d.code})` : ''}`,
              }))}
            />
          </Form.Item>
          <Space size="large">
            <Form.Item name="academicYear" label="Навчальний рік">
              <Input placeholder="2025-2026" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="semester" label="Семестр">
              <InputNumber min={1} max={2} placeholder="1" style={{ width: 80 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  );
};

/* ── Service record tab (career + languages + compliance) ──── */
const ServiceRecordTab: React.FC<{ teacherId: number }> = ({ teacherId }) => {
  const [career, setCareer] = useState<CareerRecord[]>([]);
  const [compliance, setCompliance] = useState<ComplianceReport | null>(null);
  const [loading, setLoading] = useState(false);

  // Career modal
  const [careerModalOpen, setCareerModalOpen] = useState(false);
  const [editingCareer, setEditingCareer] = useState<CareerRecord | null>(null);
  const [careerForm] = Form.useForm();

  const fetchData = useCallback(() => {
    setLoading(true);
    Promise.all([
      getCareerRecords(teacherId),
      getComplianceByTeacher(teacherId).catch(() => null),
    ])
      .then(([c, comp]) => {
        setCareer(c);
        setCompliance(comp);
      })
      .catch(() => message.error('Помилка завантаження'))
      .finally(() => setLoading(false));
  }, [teacherId]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // ── Career CRUD ────────────────────────────────────────────

  const openCareerModal = (record?: CareerRecord) => {
    setEditingCareer(record ?? null);
    if (record) {
      careerForm.setFieldsValue({
        position: record.position,
        organization: record.organization,
        startDate: record.startDate ? parseDate(record.startDate) : undefined,
        endDate: record.endDate ? parseDate(record.endDate) : undefined,
        notes: record.notes,
      });
    } else {
      careerForm.resetFields();
    }
    setCareerModalOpen(true);
  };

  const handleCareerSave = async () => {
    try {
      const values = await careerForm.validateFields();
      const payload = {
        position: values.position,
        organization: values.organization,
        startDate: values.startDate?.format('DD.MM.YYYY') || null,
        endDate: values.endDate?.format('DD.MM.YYYY') || null,
        notes: values.notes || null,
      };
      if (editingCareer) {
        await updateCareerRecord(teacherId, editingCareer.id, payload);
        message.success('Запис оновлено');
      } else {
        await createCareerRecord(teacherId, payload);
        message.success('Запис додано');
      }
      setCareerModalOpen(false);
      careerForm.resetFields();
      fetchData();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error('Помилка збереження');
    }
  };

  const handleCareerDelete = async (recId: number) => {
    try {
      await deleteCareerRecord(teacherId, recId);
      message.success('Запис видалено');
      fetchData();
    } catch {
      message.error('Помилка видалення');
    }
  };

  // ── Columns ────────────────────────────────────────────────

  const careerColumns: ColumnsType<CareerRecord> = [
    { title: 'Посада', dataIndex: 'position', key: 'position' },
    { title: 'Організація', dataIndex: 'organization', key: 'organization' },
    {
      title: 'Початок', dataIndex: 'startDate', key: 'startDate', width: 120,
      render: (v: string) => formatDate(v),
      defaultSortOrder: 'ascend',
      sorter: (a, b) => (a.startDate || '').localeCompare(b.startDate || ''),
    },
    {
      title: 'Кінець', dataIndex: 'endDate', key: 'endDate', width: 120,
      render: (v: string) => v ? formatDate(v) : 'по теп. час',
      sorter: (a, b) => (a.endDate || '￿').localeCompare(b.endDate || '￿'),
    },
    { title: 'Примітки', dataIndex: 'notes', key: 'notes', ellipsis: true },
    {
      title: '', key: 'actions', width: 80,
      render: (_: unknown, record: CareerRecord) => (
        <Space>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openCareerModal(record)} />
          <Popconfirm title="Видалити запис?" onConfirm={() => handleCareerDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const statusConfig: Record<string, { color: string; label: string }> = {
    COMPLIANT: { color: 'green', label: 'Відповідає' },
    WARNING: { color: 'orange', label: 'Попередження' },
    NON_COMPLIANT: { color: 'red', label: 'Не відповідає' },
    EXEMPT: { color: 'blue', label: 'Стаж НПП менше 3 років' },
  };

  return (
    <Spin spinning={loading}>
      {compliance && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Title level={5}>Відповідність п.38</Title>
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Статус">
              <Tag color={statusConfig[compliance.status]?.color || 'default'}>
                {statusConfig[compliance.status]?.label || compliance.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Кількість досягнень">{compliance.achievementCount}</Descriptions.Item>
            <Descriptions.Item label="Унікальних типів">{compliance.uniqueTypeCount}</Descriptions.Item>
            {compliance.exemptionReason && (
              <Descriptions.Item label="Причина звільнення">{compliance.exemptionReason}</Descriptions.Item>
            )}
          </Descriptions>
        </Card>
      )}

      {/* Career records */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Title level={5} style={{ margin: 0 }}>Послужний список</Title>
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => openCareerModal()}>
            Додати запис
          </Button>
        </div>
        <Table
          dataSource={career}
          columns={careerColumns}
          rowKey="id"
          pagination={false}
          locale={{ emptyText: 'Немає записів' }}
          size="small"
        />
      </Card>

      {/* Career record modal */}
      <Modal
        title={editingCareer ? 'Редагувати запис' : 'Новий запис'}
        open={careerModalOpen}
        onOk={handleCareerSave}
        onCancel={() => { setCareerModalOpen(false); careerForm.resetFields(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={520}
      >
        <Form form={careerForm} layout="vertical">
          <Form.Item name="position" label="Посада" rules={[{ required: true, message: 'Введіть посаду' }]}>
            <Input placeholder="Начальник кафедри, Доцент, Викладач..." />
          </Form.Item>
          <Form.Item name="organization" label="Організація">
            <Input placeholder="ВІТІ, НУОУ, ..." />
          </Form.Item>
          <Space size="large">
            <Form.Item name="startDate" label="Дата початку">
              <DatePicker format="DD.MM.YYYY" placeholder="Оберіть дату" />
            </Form.Item>
            <Form.Item name="endDate" label="Дата закінчення">
              <DatePicker format="DD.MM.YYYY" placeholder="Пусто = по теп. час" />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Примітки">
            <TextArea rows={2} placeholder="Додаткова інформація" />
          </Form.Item>
        </Form>
      </Modal>

    </Spin>
  );
};

/* ── Language skills card (used inside "Основні дані" tab) ─── */
const LanguageSkillsCard: React.FC<{ teacherId: number }> = ({ teacherId }) => {
  const [languages, setLanguages] = useState<LanguageSkill[]>([]);
  const [loading, setLoading] = useState(false);

  const [langModalOpen, setLangModalOpen] = useState(false);
  const [editingLang, setEditingLang] = useState<LanguageSkill | null>(null);
  const [langSaving, setLangSaving] = useState(false);
  const [langDrawerOpen, setLangDrawerOpen] = useState(false);
  const [selectedLang, setSelectedLang] = useState<LanguageSkill | null>(null);
  const [langForm] = Form.useForm();
  const langFileRef = useRef<FileAttachmentListRef>(null);

  const fetchData = useCallback(() => {
    setLoading(true);
    getLanguageSkills(teacherId)
      .then(setLanguages)
      .catch(() => message.error('Помилка завантаження мов'))
      .finally(() => setLoading(false));
  }, [teacherId]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openLangModal = (record?: LanguageSkill) => {
    setEditingLang(record ?? null);
    if (record) {
      langForm.setFieldsValue({
        language: record.language,
        level: record.level,
        certificateDetails: record.certificateDetails,
        certificateNumber: record.certificateNumber,
        certificateDate: record.certificateDate ? parseDate(record.certificateDate) : null,
        certificateOrganization: record.certificateOrganization,
        certificateUrl: record.certificateUrl,
        smr1: record.smr1,
        smr2: record.smr2,
        smr3: record.smr3,
        smr4: record.smr4,
      });
    } else {
      langForm.resetFields();
    }
    setLangModalOpen(true);
  };

  const handleLangSave = async () => {
    if (langSaving) return;
    setLangSaving(true);
    try {
      const values = await langForm.validateFields();
      const payload = {
        language: values.language,
        level: values.level || null,
        certificateDetails: values.certificateDetails || null,
        certificateNumber: values.certificateNumber || null,
        certificateDate: values.certificateDate ? values.certificateDate.format('DD.MM.YYYY') : null,
        certificateOrganization: values.certificateOrganization || null,
        certificateUrl: values.certificateUrl || null,
        smr1: values.smr1 ?? null,
        smr2: values.smr2 ?? null,
        smr3: values.smr3 ?? null,
        smr4: values.smr4 ?? null,
      };
      if (editingLang) {
        await updateLanguageSkill(teacherId, editingLang.id, payload);
        message.success('Запис оновлено');
      } else {
        const created = await createLanguageSkill(teacherId, payload);
        const pending = langFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0) {
          await langFileRef.current?.uploadPendingFiles('LANGUAGE_SKILL', created.id, teacherId);
        }
        message.success('Мову додано');
      }
      setLangModalOpen(false);
      langForm.resetFields();
      setEditingLang(null);
      fetchData();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error('Помилка збереження');
    } finally {
      setLangSaving(false);
    }
  };

  const handleLangDelete = async (recId: number) => {
    try {
      await deleteLanguageSkill(teacherId, recId);
      message.success('Запис видалено');
      fetchData();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const langColumns: ColumnsType<LanguageSkill> = [
    { title: 'Мова', dataIndex: 'language', key: 'language', width: 120 },
    { title: 'Рівень', dataIndex: 'level', key: 'level', width: 100 },
    { title: '№ сертифіката', dataIndex: 'certificateNumber', key: 'certificateNumber', width: 160 },
    { title: 'Дата серт.', dataIndex: 'certificateDate', key: 'certificateDate', width: 110, render: (v: string) => formatDate(v) },
    { title: 'Організація', dataIndex: 'certificateOrganization', key: 'certificateOrganization', ellipsis: true },
    {
      title: 'URL', dataIndex: 'certificateUrl', key: 'certificateUrl', width: 60,
      render: (v: string) => v ? <a href={v} target="_blank" rel="noopener noreferrer">посилання</a> : '—',
    },
    { title: 'Деталі', dataIndex: 'certificateDetails', key: 'certificateDetails', ellipsis: true },
    {
      title: '', key: 'actions', width: 80,
      render: (_: unknown, record: LanguageSkill) => (
        <Space>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openLangModal(record)} />
          <Popconfirm title="Видалити мову?" onConfirm={() => handleLangDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <Card size="small" title="Іноземні мови" style={{ marginTop: 16, marginBottom: 16 }}
        extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openLangModal()}>Додати мову</Button>}
      >
        <Table
          dataSource={languages}
          columns={langColumns}
          rowKey="id"
          pagination={false}
          locale={{ emptyText: 'Немає даних' }}
          size="small"
          onRow={(record) => ({
            onClick: (e) => {
              if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
              setSelectedLang(record);
              setLangDrawerOpen(true);
            },
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      {/* Language skill modal */}
      <Modal
        title={editingLang ? 'Редагувати мову' : 'Нова мова'}
        open={langModalOpen}
        onOk={handleLangSave}
        confirmLoading={langSaving}
        onCancel={() => { setLangModalOpen(false); langForm.resetFields(); setEditingLang(null); langFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={520}
      >
        <div style={{ marginBottom: 12, borderBottom: '1px solid #f0f0f0', paddingBottom: 12 }}>
          <FileAttachmentList
            ref={langFileRef}
            entityType="LANGUAGE_SKILL"
            entityId={editingLang?.id}
            teacherId={teacherId}
          />
        </div>
        <Form form={langForm} layout="vertical">
          <Form.Item name="language" label="Мова" rules={[{ required: true, message: 'Введіть мову' }]}>
            <Select placeholder="Оберіть мову" showSearch options={[
              { value: 'Англійська', label: 'Англійська' },
              { value: 'Німецька', label: 'Німецька' },
              { value: 'Французька', label: 'Французька' },
              { value: 'Іспанська', label: 'Іспанська' },
              { value: 'Польська', label: 'Польська' },
              { value: 'Китайська', label: 'Китайська' },
            ]} />
          </Form.Item>
          <Form.Item name="level" label="Рівень" tooltip="CEFR (A1-C2) або STANAG 6001 (напр. 2+221, 3332)">
            <AutoComplete
              placeholder="A1, B2, C1, 1+221, 2111, ..."
              allowClear
              options={[
                { label: 'CEFR', options: [
                  { value: 'A1', label: 'A1 — Початковий' },
                  { value: 'A2', label: 'A2 — Нижче середнього' },
                  { value: 'B1', label: 'B1 — Середній' },
                  { value: 'B2', label: 'B2 — Вище середнього' },
                  { value: 'C1', label: 'C1 — Просунутий' },
                  { value: 'C2', label: 'C2 — Досконалий' },
                ]},
                { label: 'STANAG 6001', options: [
                  { value: '1111', label: '1111 — Рівень 1' },
                  { value: '2222', label: '2222 — Рівень 2' },
                  { value: '3333', label: '3333 — Рівень 3' },
                  { value: '2+221', label: '2+221' },
                  { value: '3332', label: '3332' },
                ]},
              ]}
              filterOption={(input, option) => {
                const val = (option as { value?: string })?.value?.toString() || '';
                const lbl = (option as { label?: string })?.label?.toString() || '';
                return val.toLowerCase().includes(input.toLowerCase()) ||
                  lbl.toLowerCase().includes(input.toLowerCase());
              }}
            />
          </Form.Item>
          <Form.Item name="certificateNumber" label="Номер сертифіката">
            <Input placeholder="MIL-ENG-2024-001, IELTS-12345, ..." />
          </Form.Item>
          <Space size="large">
            <Form.Item name="certificateDate" label="Дата видачі сертифіката">
              <DatePicker format="DD.MM.YYYY" placeholder="Оберіть дату" />
            </Form.Item>
          </Space>
          <Form.Item name="certificateOrganization" label="Організація, яка видала">
            <Input placeholder="Національний університет оборони, British Council, ..." />
          </Form.Item>
          <Form.Item name="certificateUrl" label="Зовнішнє посилання" tooltip="Посилання на зовнішній ресурс (сайт організації, реєстр НАЗЯВО тощо)">
            <Input placeholder="https://nazvo.gov.ua/..., https://britishcouncil.org/..." />
          </Form.Item>
          <Form.Item name="certificateDetails" label="Додаткові деталі">
            <Input placeholder="СМР STANAG 6001, IELTS 7.0, ..." />
          </Form.Item>

          {/* СМР */}
          <Text strong style={{ display: 'block', marginBottom: 8 }}>СМР (компоненти STANAG)</Text>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="smr1" label="Аудіювання" style={{ flex: 1 }}>
              <InputNumber min={0} max={4} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="smr2" label="Говоріння" style={{ flex: 1 }}>
              <InputNumber min={0} max={4} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="smr3" label="Читання" style={{ flex: 1 }}>
              <InputNumber min={0} max={4} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="smr4" label="Письмо" style={{ flex: 1 }}>
              <InputNumber min={0} max={4} style={{ width: '100%' }} />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Drawer
        title={selectedLang ? `${selectedLang.language} — ${selectedLang.level || ''}` : 'Деталі'}
        placement="right"
        width={480}
        open={langDrawerOpen}
        onClose={() => { setLangDrawerOpen(false); setSelectedLang(null); }}
      >
        {selectedLang && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Мова">{selectedLang.language}</Descriptions.Item>
              {selectedLang.level && <Descriptions.Item label="Рівень">{selectedLang.level}</Descriptions.Item>}
              {selectedLang.certificateDetails && <Descriptions.Item label="Деталі">{selectedLang.certificateDetails}</Descriptions.Item>}
              {selectedLang.certificateNumber && <Descriptions.Item label="Номер сертифіката">{selectedLang.certificateNumber}</Descriptions.Item>}
              {selectedLang.certificateDate && <Descriptions.Item label="Дата видачі">{formatDate(selectedLang.certificateDate)}</Descriptions.Item>}
              {selectedLang.certificateOrganization && <Descriptions.Item label="Організація">{selectedLang.certificateOrganization}</Descriptions.Item>}
              {selectedLang.certificateUrl && (
                <Descriptions.Item label="Зовнішнє посилання">
                  <a href={selectedLang.certificateUrl} target="_blank" rel="noopener noreferrer">
                    {selectedLang.certificateUrl.length > 50 ? selectedLang.certificateUrl.substring(0, 50) + '...' : selectedLang.certificateUrl}
                  </a>
                </Descriptions.Item>
              )}
            </Descriptions>

            <div style={{ marginTop: 16 }}>
              <Typography.Text strong>Прикріплені файли</Typography.Text>
              <FileAttachmentList
                entityType="LANGUAGE_SKILL"
                entityId={selectedLang.id}
                teacherId={teacherId}
                editable={false}
              />
            </div>
          </>
        )}
      </Drawer>
    </Spin>
  );
};

/* ── Main profile page ─────────────────────────────────────── */
const TeacherProfilePage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [teacher, setTeacher] = useState<Teacher | null>(null);
  const [loading, setLoading] = useState(true);

  // Edit modal
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editForm] = Form.useForm();
  const [departments, setDepartments] = useState<Department[]>([]);

  // Educations
  const [educations, setEducations] = useState<Education[]>([]);
  const [eduModalOpen, setEduModalOpen] = useState(false);
  const [editingEdu, setEditingEdu] = useState<Education | null>(null);
  const [eduForm] = Form.useForm();
  const [eduDrawerOpen, setEduDrawerOpen] = useState(false);
  const [selectedEdu, setSelectedEdu] = useState<Education | null>(null);
  const eduFileRef = useRef<FileAttachmentListRef>(null);

  // Academic Degrees
  const [degrees, setDegrees] = useState<AcademicDegree[]>([]);
  const [degModalOpen, setDegModalOpen] = useState(false);
  const [editingDeg, setEditingDeg] = useState<AcademicDegree | null>(null);
  const [degForm] = Form.useForm();
  const [degDrawerOpen, setDegDrawerOpen] = useState(false);
  const [selectedDeg, setSelectedDeg] = useState<AcademicDegree | null>(null);
  const degFileRef = useRef<FileAttachmentListRef>(null);

  // Academic Titles
  const [titles, setTitles] = useState<AcademicTitle[]>([]);
  const [titleModalOpen, setTitleModalOpen] = useState(false);
  const [editingTitle, setEditingTitle] = useState<AcademicTitle | null>(null);
  const [titleForm] = Form.useForm();
  const [titleDrawerOpen, setTitleDrawerOpen] = useState(false);
  const [selectedTitle, setSelectedTitle] = useState<AcademicTitle | null>(null);
  const titleFileRef = useRef<FileAttachmentListRef>(null);

  // Military Education CRUD
  const [milEdus, setMilEdus] = useState<MilitaryEducation[]>([]);
  const [milEduModalOpen, setMilEduModalOpen] = useState(false);
  const [editingMilEdu, setEditingMilEdu] = useState<MilitaryEducation | null>(null);
  const [milEduForm] = Form.useForm();
  const [milEduDrawerOpen, setMilEduDrawerOpen] = useState(false);
  const [selectedMilEdu, setSelectedMilEdu] = useState<MilitaryEducation | null>(null);
  const milEduFileRef = useRef<FileAttachmentListRef>(null);

  // Military courses (L2/L3/L4) on "Основні дані" tab
  const [militaryCourses, setMilitaryCourses] = useState<any[]>([]);
  const [mcModalOpen, setMcModalOpen] = useState(false);
  const [editingMc, setEditingMc] = useState<any | null>(null);
  const [mcForm] = Form.useForm();
  const [mcDrawerOpen, setMcDrawerOpen] = useState(false);
  const [selectedMc, setSelectedMc] = useState<any | null>(null);
  const mcFileRef = useRef<FileAttachmentListRef>(null);

  const teacherId = id ? Number(id) : undefined;

  const fetchTeacher = useCallback(async () => {
    if (!teacherId) return;
    setLoading(true);
    try {
      const data = await getTeacher(teacherId);
      setTeacher(data);
    } catch {
      message.error('Не вдалося завантажити дані викладача');
    } finally {
      setLoading(false);
    }
  }, [teacherId]);

  const fetchEducations = useCallback(async () => {
    if (!teacherId) return;
    try {
      const data = await getEducations(teacherId);
      setEducations(data);
    } catch { /* ignore */ }
  }, [teacherId]);

  const fetchDegrees = useCallback(async () => {
    if (!teacherId) return;
    try {
      const data = await getAcademicDegrees(teacherId);
      setDegrees(data);
    } catch { /* ignore */ }
  }, [teacherId]);

  const fetchTitles = useCallback(async () => {
    if (!teacherId) return;
    try {
      const data = await getAcademicTitles(teacherId);
      setTitles(data);
    } catch { /* ignore */ }
  }, [teacherId]);

  const fetchMilitaryEducations = useCallback(async () => {
    if (!teacherId) return;
    try {
      const data = await getMilitaryEducations(teacherId);
      setMilEdus(data);
    } catch { /* ignore */ }
  }, [teacherId]);

  const fetchMilitaryCourses = useCallback(async () => {
    if (!teacherId) return;
    try {
      const all = await getQualifications(teacherId);
      setMilitaryCourses(all.filter((q: any) => q.category === 'MILITARY_COURSE'));
    } catch { /* ignore */ }
  }, [teacherId]);

  useEffect(() => {
    fetchTeacher(); fetchEducations(); fetchDegrees(); fetchTitles();
    fetchMilitaryEducations(); fetchMilitaryCourses();
    getDepartments().then(setDepartments).catch(() => {});
  }, [fetchTeacher, fetchEducations, fetchDegrees, fetchTitles, fetchMilitaryEducations, fetchMilitaryCourses]);

  // ── Edit teacher basic data ───────────────────────────────

  // ── Education CRUD ───────────────────────────────
  const openEduModal = (edu?: Education) => {
    setEditingEdu(edu || null);
    eduForm.resetFields();
    if (edu) {
      eduForm.setFieldsValue({
        ...edu,
        diplomaDate: edu.diplomaDate ? parseDate(edu.diplomaDate) : undefined,
      });
    }
    setEduModalOpen(true);
  };

  const handleEduSave = async () => {
    if (!teacherId) return;
    const values = eduForm.getFieldsValue();
    const payload = {
      ...values,
      diplomaDate: values.diplomaDate ? values.diplomaDate.format('DD.MM.YYYY') : null,
    };
    try {
      if (editingEdu?.id) {
        await updateEducation(teacherId, editingEdu.id, payload);
        message.success('Освіту оновлено');
      } else {
        const created = await createEducation(teacherId, payload);
        // Upload pending files for new education record
        const pending = eduFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && created?.id) {
          await eduFileRef.current?.uploadPendingFiles('EDUCATION', created.id, teacherId);
        }
        message.success('Освіту додано');
      }
      setEduModalOpen(false);
      fetchEducations();
      fetchTeacher(); // sync flat fields
    } catch {
      message.error('Помилка збереження освіти');
    }
  };

  const handleEduDelete = async (eduId: number) => {
    if (!teacherId) return;
    try {
      await deleteEducation(teacherId, eduId);
      message.success('Освіту видалено');
      fetchEducations();
      fetchTeacher();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const educationColumns: ColumnsType<Education> = [
    { title: 'ВНЗ', dataIndex: 'institution', key: 'institution', ellipsis: true },
    { title: 'Рівень', dataIndex: 'degree', key: 'degree', width: 140 },
    { title: 'Спеціальність', dataIndex: 'speciality', key: 'speciality', ellipsis: true },
    { title: 'Кваліфікація', dataIndex: 'qualification', key: 'qualification', ellipsis: true },
    { title: 'Рік', dataIndex: 'graduationYear', key: 'graduationYear', width: 70 },
    { title: 'Диплом', dataIndex: 'diploma', key: 'diploma', ellipsis: true, width: 200 },
    {
      title: '', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEduModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => record.id && handleEduDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ── Academic Degree CRUD ────────────────────────────────
  const openDegModal = (d?: AcademicDegree) => {
    setEditingDeg(d || null);
    degForm.resetFields();
    if (d) {
      degForm.setFieldsValue({
        ...d,
        diplomaDate: d.diplomaDate ? parseDate(d.diplomaDate) : undefined,
      });
    }
    setDegModalOpen(true);
  };

  const handleDegSave = async () => {
    if (!teacherId) return;
    const values = degForm.getFieldsValue();
    const payload = {
      ...values,
      diplomaDate: values.diplomaDate ? values.diplomaDate.format('DD.MM.YYYY') : null,
    };
    try {
      if (editingDeg?.id) {
        await updateAcademicDegree(teacherId, editingDeg.id, payload);
        message.success('Ступінь оновлено');
      } else {
        const created = await createAcademicDegree(teacherId, payload);
        // Upload pending files прив'язавши їх до новоствореного ступеня
        const pending = degFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && created?.id) {
          await degFileRef.current?.uploadPendingFiles('ACADEMIC_DEGREE', created.id, teacherId);
        }
        message.success('Ступінь додано');
      }
      setDegModalOpen(false);
      fetchDegrees();
      fetchTeacher(); // sync primary degree у flat-полях
    } catch {
      message.error('Помилка збереження ступеня');
    }
  };

  const handleDegDelete = async (degreeId: number) => {
    if (!teacherId) return;
    try {
      await deleteAcademicDegree(teacherId, degreeId);
      message.success('Ступінь видалено');
      fetchDegrees();
      fetchTeacher();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const degreeColumns: ColumnsType<AcademicDegree> = [
    { title: 'Ступінь', dataIndex: 'degree', key: 'degree', width: 220, ellipsis: true },
    { title: 'Спеціальність', dataIndex: 'speciality', key: 'speciality', ellipsis: true },
    { title: 'Тема дисертації', dataIndex: 'dissertationTopic', key: 'dissertationTopic', ellipsis: true },
    { title: 'Диплом', dataIndex: 'diploma', key: 'diploma', width: 160, ellipsis: true },
    {
      title: 'Дата диплома', dataIndex: 'diplomaDate', key: 'diplomaDate', width: 130,
      render: (v: string) => v ? formatDate(v) : '—',
      defaultSortOrder: 'ascend',
      sorter: (a, b) => (a.diplomaDate || '').localeCompare(b.diplomaDate || ''),
    },
    {
      title: '', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openDegModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => record.id && handleDegDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ── Academic Title CRUD ─────────────────────────────────
  const openTitleModal = (t?: AcademicTitle) => {
    setEditingTitle(t || null);
    titleForm.resetFields();
    if (t) {
      titleForm.setFieldsValue({
        ...t,
        attestatDate: t.attestatDate ? parseDate(t.attestatDate) : undefined,
      });
    }
    setTitleModalOpen(true);
  };

  const handleTitleSave = async () => {
    if (!teacherId) return;
    const values = titleForm.getFieldsValue();
    const payload = {
      ...values,
      attestatDate: values.attestatDate ? values.attestatDate.format('DD.MM.YYYY') : null,
    };
    try {
      if (editingTitle?.id) {
        await updateAcademicTitle(teacherId, editingTitle.id, payload);
        message.success('Звання оновлено');
      } else {
        const created = await createAcademicTitle(teacherId, payload);
        const pending = titleFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && created?.id) {
          await titleFileRef.current?.uploadPendingFiles('ACADEMIC_TITLE', created.id, teacherId);
        }
        message.success('Звання додано');
      }
      setTitleModalOpen(false);
      fetchTitles();
      fetchTeacher();
    } catch {
      message.error('Помилка збереження звання');
    }
  };

  const handleTitleDelete = async (titleId: number) => {
    if (!teacherId) return;
    try {
      await deleteAcademicTitle(teacherId, titleId);
      message.success('Звання видалено');
      fetchTitles();
      fetchTeacher();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const titleColumns: ColumnsType<AcademicTitle> = [
    { title: 'Вчене звання', dataIndex: 'titleName', key: 'titleName', ellipsis: true },
    { title: 'Атестат', dataIndex: 'attestat', key: 'attestat', width: 200, ellipsis: true },
    {
      title: 'Дата атестата', dataIndex: 'attestatDate', key: 'attestatDate', width: 130,
      render: (v: string) => v ? formatDate(v) : '—',
      defaultSortOrder: 'ascend',
      sorter: (a, b) => (a.attestatDate || '').localeCompare(b.attestatDate || ''),
    },
    { title: 'Ким видано', dataIndex: 'issuedBy', key: 'issuedBy', ellipsis: true },
    {
      title: '', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openTitleModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => record.id && handleTitleDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ── Military Education CRUD ───────────────────────────────
  const openMilEduModal = (me?: MilitaryEducation) => {
    setEditingMilEdu(me || null);
    milEduForm.resetFields();
    if (me) {
      milEduForm.setFieldsValue({
        ...me,
        diplomaDate: me.diplomaDate ? parseDate(me.diplomaDate) : undefined,
      });
    }
    setMilEduModalOpen(true);
  };

  const handleMilEduSave = async () => {
    if (!teacherId) return;
    const values = milEduForm.getFieldsValue();
    const payload = {
      ...values,
      diplomaDate: values.diplomaDate ? values.diplomaDate.format('DD.MM.YYYY') : null,
    };
    try {
      if (editingMilEdu?.id) {
        await updateMilitaryEducation(teacherId, editingMilEdu.id, payload);
        message.success('Військову освіту оновлено');
      } else {
        const created = await createMilitaryEducation(teacherId, payload);
        const pending = milEduFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && created?.id) {
          await milEduFileRef.current?.uploadPendingFiles('MILITARY_EDUCATION', created.id, teacherId);
        }
        message.success('Військову освіту додано');
      }
      setMilEduModalOpen(false);
      fetchMilitaryEducations();
      fetchTeacher();
    } catch {
      message.error('Помилка збереження');
    }
  };

  const handleMilEduDelete = async (meId: number) => {
    if (!teacherId) return;
    try {
      await deleteMilitaryEducation(teacherId, meId);
      message.success('Військову освіту видалено');
      fetchMilitaryEducations();
      fetchTeacher();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const milEduColumns: ColumnsType<MilitaryEducation> = [
    { title: 'Рівень', dataIndex: 'level', key: 'level', width: 140,
      render: (v: string) => v === 'STRATEGIC' ? 'Стратегічний' : v === 'OPERATIONAL' ? 'Оперативний' : '—',
    },
    { title: 'Заклад освіти', dataIndex: 'institution', key: 'institution', ellipsis: true },
    { title: 'Спеціальність', dataIndex: 'speciality', key: 'speciality', ellipsis: true },
    { title: 'Рік', dataIndex: 'graduationYear', key: 'graduationYear', width: 70 },
    { title: 'Диплом', dataIndex: 'diploma', key: 'diploma', ellipsis: true, width: 200 },
    {
      title: '', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openMilEduModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => record.id && handleMilEduDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ── Military Courses (L2/L3/L4) CRUD ───────────────────────────────
  const openMcModal = (mc?: any) => {
    setEditingMc(mc || null);
    mcForm.resetFields();
    if (mc) {
      mcForm.setFieldsValue({
        ...mc,
        startDate: mc.startDate ? parseDate(mc.startDate) : undefined,
        endDate: mc.endDate ? parseDate(mc.endDate) : undefined,
        certificateDate: mc.certificateDate ? parseDate(mc.certificateDate) : undefined,
      });
    }
    setMcModalOpen(true);
  };

  const handleMcSave = async () => {
    if (!teacherId) return;
    try {
      const values = await mcForm.validateFields();
      const payload = {
        ...values,
        category: 'MILITARY_COURSE',
        startDate: values.startDate?.format('DD.MM.YYYY'),
        endDate: values.endDate?.format('DD.MM.YYYY'),
        certificateDate: values.certificateDate?.format('DD.MM.YYYY'),
        teacher: { id: teacherId },
      };
      if (editingMc?.id) {
        await updateQualification(editingMc.id, payload);
        message.success('Курс оновлено');
      } else {
        const created = await createQualification(payload);
        const pending = mcFileRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && created?.id) {
          await mcFileRef.current?.uploadPendingFiles('QUALIFICATION_IMPROVEMENT', created.id, teacherId);
        }
        message.success('Курс додано');
      }
      setMcModalOpen(false);
      fetchMilitaryCourses();
    } catch {
      // validation
    }
  };

  const handleMcDelete = async (id: number) => {
    try {
      await deleteQualification(id);
      message.success('Курс видалено');
      fetchMilitaryCourses();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const mcColumns: ColumnsType<any> = [
    { title: 'Рівень', dataIndex: 'militaryCourseLevel', key: 'level', width: 120,
      render: (v: string) => <Tag color="volcano">{MILITARY_COURSE_LEVEL_LABELS[v as MilitaryCourseLevel] || v}</Tag>,
    },
    { title: 'Назва', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: 'Організація', dataIndex: 'organization', key: 'org', ellipsis: true },
    { title: 'Початок', dataIndex: 'startDate', key: 'start', width: 110, render: (v: string) => formatDate(v) },
    { title: 'Кінець', dataIndex: 'endDate', key: 'end', width: 110, render: (v: string) => formatDate(v) },
    { title: 'Сертифікат', dataIndex: 'certificateNumber', key: 'cert', width: 150 },
    {
      title: '', key: 'actions', width: 80,
      render: (_, record: any) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openMcModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => record.id && handleMcDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const openEditModal = () => {
    if (!teacher) return;
    editForm.setFieldsValue({
      lastName: teacher.lastName,
      firstName: teacher.firstName,
      patronymic: teacher.patronymic,
      dateOfBirth: teacher.dateOfBirth ? parseDate(teacher.dateOfBirth) : undefined,
      // position видалено — редагується через «Штат»
      employmentType: teacher.employmentType,
      militaryRank: teacher.militaryRank,
      experienceStartDate: teacher.experienceStartDate ? parseDate(teacher.experienceStartDate) : undefined,
      university: teacher.university,
      universitySpeciality: teacher.universitySpeciality,
      universityDiploma: teacher.universityDiploma,
      universityGraduationYear: teacher.universityGraduationYear,
      universityDiplomaDate: teacher.universityDiplomaDate ? parseDate(teacher.universityDiplomaDate) : undefined,
      combatVeteranStatus: teacher.combatVeteranStatus,
      combatVeteranDoc: teacher.combatVeteranDoc,
      combatVeteranDocDate: teacher.combatVeteranDocDate ? parseDate(teacher.combatVeteranDocDate) : undefined,
      combatVeteranDocIssuedBy: teacher.combatVeteranDocIssuedBy,
      combatExperienceDates: teacher.combatExperienceDates,
      militaryEducationLevel: teacher.militaryEducationLevel,
      militaryEducationDiploma: teacher.militaryEducationDiploma,
      militaryEducationDiplomaDate: teacher.militaryEducationDiplomaDate ? parseDate(teacher.militaryEducationDiplomaDate) : undefined,
      militaryEducationIssuedBy: teacher.militaryEducationIssuedBy,
      orcidId: teacher.orcidId,
      googleScholarUrl: teacher.googleScholarUrl,
      scopusId: teacher.scopusId,
      wosId: teacher.wosId,
      email: teacher.email,
      phone: teacher.phone,
      departmentId: teacher.departmentId,
    });
    setEditModalOpen(true);
  };

  const handleEditSave = async () => {
    if (!teacherId || !teacher) return;
    try {
      const values = await editForm.validateFields();
      const payload = {
        ...values,
        dateOfBirth: values.dateOfBirth?.format('DD.MM.YYYY') || null,
        experienceStartDate: values.experienceStartDate?.format('DD.MM.YYYY') || null,
        universityDiplomaDate: values.universityDiplomaDate?.format('DD.MM.YYYY') || null,
        combatVeteranDocDate: values.combatVeteranDocDate?.format('DD.MM.YYYY') || null,
        departmentId: values.departmentId ?? teacher.departmentId, // from form or preserve
      };
      await updateTeacher(teacherId, payload);
      message.success('Дані оновлено');
      setEditModalOpen(false);
      fetchTeacher();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error('Помилка збереження');
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!teacher || !teacherId) {
    return <Text type="danger">Викладача не знайдено</Text>;
  }

  const fullName = `${teacher.lastName} ${teacher.firstName} ${teacher.patronymic || ''}`.trim();

  // Format experience display
  const experienceDisplay = (() => {
    if (!teacher.experienceStartDate) return '—';
    const start = formatDate(teacher.experienceStartDate);
    const years = teacher.experienceYears ?? 0;
    return `з ${start} (${years} р.)`;
  })();

  const tabItems = [
    {
      key: 'general',
      label: 'Основні дані',
      children: (
        <>
          <div style={{ marginBottom: 16, textAlign: 'right' }}>
            <Button icon={<EditOutlined />} onClick={openEditModal}>Редагувати</Button>
          </div>
          <Descriptions bordered column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label="Прізвище">{teacher.lastName}</Descriptions.Item>
            <Descriptions.Item label="Ім'я">{teacher.firstName}</Descriptions.Item>
            <Descriptions.Item label="По батькові">{teacher.patronymic || '—'}</Descriptions.Item>
            <Descriptions.Item label="Дата народження">{teacher.dateOfBirth ? formatDate(teacher.dateOfBirth) : '—'}</Descriptions.Item>
            <Descriptions.Item label="Посада">
              <Space size={6}>
                <span>{teacher.effectivePosition || '—'}</span>
                {teacher.bootstrappedPosition && (
                  <Tooltip title="Запис створено автоматично з картки. Перевірте у розділі «Штат».">
                    <ExclamationCircleOutlined style={{ color: '#faad14' }} />
                  </Tooltip>
                )}
                <Tooltip title="Посаду редагуйте у розділі «Штат». Тут вона лише для перегляду.">
                  <Tag color="default" style={{ fontSize: 11 }}>лише перегляд</Tag>
                </Tooltip>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="Тип зайнятості">
              <Tag color={teacher.employmentType === 'MAIN' ? 'blue' : 'orange'}>
                {teacher.employmentType === 'MAIN' ? 'Основне місце' : 'Сумісник'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Військове звання">{teacher.militaryRank || '—'}</Descriptions.Item>
            <Descriptions.Item label="Стаж НПП">{experienceDisplay}</Descriptions.Item>
            <Descriptions.Item label="Учасник бойових дій">
              <Tag color={teacher.combatVeteranStatus ? 'green' : 'default'}>
                {teacher.combatVeteranStatus ? 'Так' : 'Ні'}
              </Tag>
            </Descriptions.Item>
            {teacher.combatExperienceDates && (
              <Descriptions.Item label="Дати бойового досвіду">{formatDatesInText(teacher.combatExperienceDates)}</Descriptions.Item>
            )}
            {teacher.combatVeteranDoc && (
              <Descriptions.Item label="Документ УБД">{teacher.combatVeteranDoc}</Descriptions.Item>
            )}
            {teacher.combatVeteranDocDate && (
              <Descriptions.Item label="Дата посвідчення УБД">{formatDate(teacher.combatVeteranDocDate)}</Descriptions.Item>
            )}
            {teacher.combatVeteranDocIssuedBy && (
              <Descriptions.Item label="Ким видано посвідчення">{teacher.combatVeteranDocIssuedBy}</Descriptions.Item>
            )}
            <Descriptions.Item label="Скан посвідчення УБД" span={2}>
              <FileAttachmentList entityType="TEACHER_COMBAT_VETERAN_DOC" entityId={teacher.id} teacherId={teacher.id} editable={false} />
            </Descriptions.Item>
          </Descriptions>

          {/* Освіта — окрема таблиця з Drawer */}
          <Card size="small" title="Освіта" style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openEduModal()}>Додати</Button>}
          >
            <Table
              dataSource={educations}
              columns={educationColumns}
              rowKey="id"
              size="small"
              pagination={false}
              locale={{ emptyText: 'Немає записів про освіту' }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
                  setSelectedEdu(record);
                  setEduDrawerOpen(true);
                },
                style: { cursor: 'pointer' },
              })}
            />
          </Card>

          {/* Наукові ступені — окрема таблиця з Drawer (як Освіта) */}
          <Card size="small" title="Наукові ступені" style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openDegModal()}>Додати</Button>}
          >
            <Table
              dataSource={degrees}
              columns={degreeColumns}
              rowKey="id"
              size="small"
              pagination={false}
              locale={{ emptyText: 'Немає записів про наукові ступені' }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
                  setSelectedDeg(record);
                  setDegDrawerOpen(true);
                },
                style: { cursor: 'pointer' },
              })}
            />
          </Card>

          {/* Вчені звання — окрема таблиця з Drawer */}
          <Card size="small" title="Вчені звання" style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openTitleModal()}>Додати</Button>}
          >
            <Table
              dataSource={titles}
              columns={titleColumns}
              rowKey="id"
              size="small"
              pagination={false}
              locale={{ emptyText: 'Немає записів про вчені звання' }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
                  setSelectedTitle(record);
                  setTitleDrawerOpen(true);
                },
                style: { cursor: 'pointer' },
              })}
            />
          </Card>

          {/* Військова освіта — таблиця з Drawer */}
          <Card size="small" title="Військова освіта (оперативний/стратегічний рівень)" style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openMilEduModal()}>Додати</Button>}
          >
            <Table
              dataSource={milEdus}
              columns={milEduColumns}
              rowKey="id"
              size="small"
              pagination={false}
              locale={{ emptyText: 'Немає записів про військову освіту' }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
                  setSelectedMilEdu(record);
                  setMilEduDrawerOpen(true);
                },
                style: { cursor: 'pointer' },
              })}
            />
          </Card>

          {/* Курси Професійної військової освіти (L2, L3, L4) */}
          <Card size="small" title="Курси Професійної військової освіти (L2, L3, L4)" style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openMcModal()}>Додати</Button>}
          >
            <Table
              dataSource={militaryCourses}
              columns={mcColumns}
              rowKey="id"
              size="small"
              pagination={false}
              locale={{ emptyText: 'Немає записів про курси ПВО' }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
                  setSelectedMc(record);
                  setMcDrawerOpen(true);
                },
                style: { cursor: 'pointer' },
              })}
            />
          </Card>

          {/* Іноземні мови (з власним state, modals і drawer всередині компонента) */}
          {teacherId !== undefined && <LanguageSkillsCard teacherId={teacherId} />}

          <Descriptions bordered column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label="Email">{teacher.email || '—'}</Descriptions.Item>
            <Descriptions.Item label="Телефон">{teacher.phone || '—'}</Descriptions.Item>
            <Descriptions.Item label="ORCID">
              {teacher.orcidId ? (
                <a href={`https://orcid.org/${teacher.orcidId}`} target="_blank" rel="noopener noreferrer">
                  {teacher.orcidId}
                </a>
              ) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Google Scholar">
              {teacher.googleScholarUrl ? (
                <a href={teacher.googleScholarUrl} target="_blank" rel="noopener noreferrer">
                  Профіль
                </a>
              ) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Scopus ID">
              {teacher.scopusId ? (
                <a href={`https://www.scopus.com/authid/detail.uri?authorId=${teacher.scopusId}`} target="_blank" rel="noopener noreferrer">
                  {teacher.scopusId}
                </a>
              ) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="WoS ID">
              {teacher.wosId ? (
                <a href={`https://www.webofscience.com/wos/author/record/${teacher.wosId}`} target="_blank" rel="noopener noreferrer">
                  {teacher.wosId}
                </a>
              ) : '—'}
            </Descriptions.Item>
          </Descriptions>
        </>
      ),
    },
    {
      key: 'publications',
      label: 'Публікації',
      children: <PublicationsPage teacherId={teacherId} />,
    },
    {
      key: 'achievements',
      label: 'Досягнення п.38',
      children: <AchievementsPage teacherId={teacherId} />,
    },
    {
      key: 'disciplines',
      label: 'Дисципліни',
      children: <TeacherDisciplinesTab teacherId={teacherId} />,
    },
    {
      key: 'qualification',
      label: 'Підвищення кваліфікації',
      children: <QualificationPage teacherId={teacherId} />,
    },
    {
      key: 'ppData',
      label: 'Дані п.38',
      children: <PpDataTabs teacherId={teacherId} />,
    },
    {
      key: 'otherActivity',
      label: 'Інша діяльність',
      children: <OtherActivityTab teacherId={teacherId} />,
    },
    {
      key: 'serviceRecord',
      label: 'Послужний список',
      children: <ServiceRecordTab teacherId={teacherId} />,
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        {fullName}
      </Title>
      <Tabs defaultActiveKey="general" items={tabItems} />

      {/* ── Edit teacher modal ──────────────────────────────── */}
      <Modal
        title="Редагувати дані викладача"
        open={editModalOpen}
        onOk={handleEditSave}
        onCancel={() => { setEditModalOpen(false); editForm.resetFields(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={900}
      >
        <Form form={editForm} layout="vertical" style={{ maxHeight: '60vh', overflowY: 'auto', paddingRight: 8 }}>
          {/* ПІБ */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="lastName" label="Прізвище" style={{ flex: 1 }}
              rules={[{ required: true, message: 'Введіть прізвище' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="firstName" label="Ім'я" style={{ flex: 1 }}
              rules={[{ required: true, message: "Введіть ім'я" }]}>
              <Input />
            </Form.Item>
            <Form.Item name="patronymic" label="По батькові" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </div>

          {/* Основне */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="dateOfBirth" label="Дата народження" style={{ width: 170 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="дд.мм.рррр" style={{ width: '100%' }} />
            </Form.Item>
            {/* Посада повністю редагується через «Штат» — у формі профілю її немає. */}
            <Form.Item name="employmentType" label="Тип зайнятості" style={{ width: 220 }}
              rules={[{ required: true, message: 'Оберіть тип' }]}>
              <Select options={[
                { value: 'MAIN', label: 'Основне місце' },
                { value: 'PART_TIME', label: 'Сумісник' },
              ]} />
            </Form.Item>
          </div>

          {/* Кафедра */}
          <Form.Item name="departmentId" label="Кафедра">
            <Select allowClear showSearch placeholder="Оберіть кафедру"
              optionFilterProp="label"
              options={departments.map(d => ({
                value: d.id, label: d.number ? `${d.number} — ${d.name}` : d.name,
              }))} />
          </Form.Item>

          {/* Військове + стаж */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="militaryRank" label="Військове звання" style={{ flex: 1 }}>
              <Select allowClear showSearch placeholder="Оберіть звання"
                options={MILITARY_RANK_OPTIONS.map(r => ({ value: r, label: r }))} />
            </Form.Item>
            <Form.Item name="experienceStartDate" label="Початок стажу НПП" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата початку" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          {/* Наукові ступені та вчені звання редагуються через окремі таблиці у "Основні дані". */}
          {/* Освіта теж редагується окремою таблицею. */}

          {/* УБД */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'end' }}>
            <Form.Item name="combatVeteranStatus" label="Учасник бойових дій" valuePropName="checked"
              style={{ width: 200 }}>
              <Switch checkedChildren="Так" unCheckedChildren="Ні" />
            </Form.Item>
            <Form.Item name="combatVeteranDoc" label="Документ УБД" style={{ flex: 1 }}>
              <Input placeholder="Посвідчення УБД №..." />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="combatVeteranDocDate" label="Дата посвідчення УБД" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="combatVeteranDocIssuedBy" label="Ким видано посвідчення" style={{ flex: 1 }}>
              <Input placeholder="МО України, РТЦК та СП..." />
            </Form.Item>
          </div>
          {teacher?.id && (
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>📎 Скан посвідчення УБД</Text>
              <FileAttachmentList entityType="TEACHER_COMBAT_VETERAN_DOC" entityId={teacher.id} teacherId={teacher.id} maxCount={3} />
            </div>
          )}
          <Form.Item
            name="combatExperienceDates"
            label={
              <span>
                Дати бойового досвіду&nbsp;
                <Tooltip
                  title={
                    <div style={{ fontSize: 12, lineHeight: 1.5 }}>
                      <div><b>Формат:</b> один або кілька діапазонів через кому.</div>
                      <div>Кожен діапазон: <code>дд.мм.рррр – дд.мм.рррр</code></div>
                      <div style={{ marginTop: 6 }}><b>Приклади:</b></div>
                      <div>• <code>02.08.2015 – 02.10.2015</code></div>
                      <div>• <code>02.08.2015 – 02.10.2015, 20.02.2025 – 28.02.2025</code></div>
                      <div style={{ marginTop: 6 }}>
                        Допустимі роздільники між датами: <code>–</code>, <code>—</code>, <code>-</code>, <code>/</code>, «по».
                        Бажано — тире з пробілами.
                      </div>
                      <div style={{ marginTop: 6, opacity: 0.85 }}>
                        Рейтинг враховує запис, лише якщо діапазон перетинається з періодом рейтингу.
                        Тому точні дати дають точний підрахунок.
                      </div>
                    </div>
                  }
                >
                  <InfoCircleOutlined style={{ color: '#1677ff', cursor: 'help' }} />
                </Tooltip>
              </span>
            }
            extra={<span style={{ fontSize: 11, color: '#888' }}>Формат: <code>дд.мм.рррр – дд.мм.рррр</code>, кілька діапазонів через кому</span>}
          >
            <Input placeholder="02.08.2015 – 02.10.2015, 20.02.2025 – 28.02.2025" />
          </Form.Item>

          {/* Контакти */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="email" label="Email" style={{ flex: 1 }}>
              <Input type="email" />
            </Form.Item>
            <Form.Item name="phone" label="Телефон" style={{ flex: 1 }}>
              <Input placeholder="+380..." />
            </Form.Item>
          </div>

          {/* Науковометрія */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="orcidId" label="ORCID" style={{ flex: 1 }}>
              <Input placeholder="0000-0001-2345-6789" />
            </Form.Item>
            <Form.Item name="googleScholarUrl" label="Google Scholar URL" style={{ flex: 1 }}>
              <Input placeholder="https://scholar.google.com/..." />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="scopusId" label="Scopus ID" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="wosId" label="WoS ID" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* ── Education modal ──────────────────────────────── */}
      <Modal
        title={editingEdu ? 'Редагувати освіту' : 'Додати освіту'}
        open={eduModalOpen}
        onOk={handleEduSave}
        onCancel={() => { setEduModalOpen(false); eduForm.resetFields(); eduFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={eduForm} layout="vertical">
          <Form.Item label="Прикріплені файли">
            <FileAttachmentList
              ref={eduFileRef}
              entityType="EDUCATION"
              entityId={editingEdu?.id}
              teacherId={teacherId}
              maxCount={3}
            />
          </Form.Item>
          <Form.Item name="institution" label="Заклад освіти" rules={[{ required: true, message: 'Вкажіть ВНЗ' }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="city" label="Місто" style={{ width: 180 }}>
              <Input />
            </Form.Item>
            <Form.Item name="degree" label="Рівень освіти" style={{ width: 220 }}>
              <Select allowClear placeholder="Оберіть">
                <Select.Option value="Фаховий молодший бакалавр">Фаховий молодший бакалавр</Select.Option>
                <Select.Option value="Бакалавр">Бакалавр</Select.Option>
                <Select.Option value="Спеціаліст">Спеціаліст</Select.Option>
                <Select.Option value="Магістр">Магістр</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="graduationYear" label="Рік закінчення" style={{ width: 130 }}>
              <InputNumber min={1950} max={2035} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="speciality" label="Спеціальність">
            <Input />
          </Form.Item>
          <Form.Item name="qualification" label="Кваліфікація">
            <Input />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="diploma" label="Диплом (серія, номер)" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="diplomaDate" label="Дата диплому" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: '100%' }} />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* ── Education drawer ──────────────────────────────── */}
      <Drawer
        title={selectedEdu?.institution || 'Деталі освіти'}
        placement="right"
        width={480}
        open={eduDrawerOpen}
        onClose={() => { setEduDrawerOpen(false); setSelectedEdu(null); }}
        extra={
          <Space>
            <Button icon={<EditOutlined />} size="small" onClick={() => { setEduDrawerOpen(false); selectedEdu && openEduModal(selectedEdu); }}>
              Редагувати
            </Button>
          </Space>
        }
      >
        {selectedEdu && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Заклад освіти">{selectedEdu.institution || '—'}</Descriptions.Item>
              {selectedEdu.city && <Descriptions.Item label="Місто">{selectedEdu.city}</Descriptions.Item>}
              {selectedEdu.degree && <Descriptions.Item label="Рівень освіти">{selectedEdu.degree}</Descriptions.Item>}
              {selectedEdu.speciality && <Descriptions.Item label="Спеціальність">{selectedEdu.speciality}</Descriptions.Item>}
              {selectedEdu.qualification && <Descriptions.Item label="Кваліфікація">{selectedEdu.qualification}</Descriptions.Item>}
              {selectedEdu.graduationYear && <Descriptions.Item label="Рік закінчення">{selectedEdu.graduationYear}</Descriptions.Item>}
              {selectedEdu.diploma && <Descriptions.Item label="Диплом">{selectedEdu.diploma}</Descriptions.Item>}
              {selectedEdu.diplomaDate && <Descriptions.Item label="Дата диплому">{formatDate(selectedEdu.diplomaDate)}</Descriptions.Item>}
            </Descriptions>
            {selectedEdu.id && (
              <div style={{ marginTop: 16 }}>
                <Text strong>Прикріплені файли</Text>
                <FileAttachmentList
                  entityType="EDUCATION"
                  entityId={selectedEdu.id}
                  teacherId={teacherId}
                  editable={false}
                  maxCount={3}
                />
              </div>
            )}
          </>
        )}
      </Drawer>

      {/* ── Academic Degree modal ──────────────────────────────── */}
      <Modal
        title={editingDeg ? 'Редагувати науковий ступінь' : 'Додати науковий ступінь'}
        open={degModalOpen}
        onOk={handleDegSave}
        onCancel={() => { setDegModalOpen(false); degForm.resetFields(); degFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={degForm} layout="vertical">
          <Form.Item label="Прикріплені файли (диплом, скан)">
            <FileAttachmentList
              ref={degFileRef}
              entityType="ACADEMIC_DEGREE"
              entityId={editingDeg?.id}
              teacherId={teacherId}
              maxCount={3}
            />
          </Form.Item>
          <Form.Item name="degree" label="Науковий ступінь" rules={[{ required: true, message: 'Вкажіть ступінь' }]}>
            <AutoComplete
              placeholder="Напр. Доктор технічних наук"
              options={[
                'Доктор філософії',
                'Доктор технічних наук',
                'Доктор військових наук',
                'Доктор економічних наук',
                'Доктор юридичних наук',
                'Доктор фізико-математичних наук',
                'Кандидат технічних наук',
                'Кандидат військових наук',
                'Кандидат економічних наук',
                'Кандидат юридичних наук',
                'PhD',
              ].map(v => ({ value: v }))}
              filterOption={(inputValue, option) =>
                String(option?.value || '').toLowerCase().includes(inputValue.toLowerCase())
              }
            />
          </Form.Item>
          <Form.Item name="speciality" label="Спеціальність">
            <Input placeholder="Напр. 122 Комп'ютерні науки" />
          </Form.Item>
          <Form.Item name="dissertationTopic" label="Тема дисертації">
            <Input.TextArea rows={2} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="diploma" label="Диплом (серія/номер)" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="diplomaDate" label="Дата диплома" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="issuedBy" label="Ким видано (рада з захисту)">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Academic Degree drawer ──────────────────────────────── */}
      <Drawer
        title={selectedDeg?.degree || 'Деталі ступеня'}
        placement="right"
        width={480}
        open={degDrawerOpen}
        onClose={() => { setDegDrawerOpen(false); setSelectedDeg(null); }}
        extra={
          <Space>
            <Button icon={<EditOutlined />} size="small" onClick={() => { setDegDrawerOpen(false); selectedDeg && openDegModal(selectedDeg); }}>
              Редагувати
            </Button>
          </Space>
        }
      >
        {selectedDeg && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Ступінь">{selectedDeg.degree || '—'}</Descriptions.Item>
              {selectedDeg.speciality && <Descriptions.Item label="Спеціальність">{selectedDeg.speciality}</Descriptions.Item>}
              {selectedDeg.dissertationTopic && <Descriptions.Item label="Тема дисертації">{selectedDeg.dissertationTopic}</Descriptions.Item>}
              {selectedDeg.diploma && <Descriptions.Item label="Диплом">{selectedDeg.diploma}</Descriptions.Item>}
              {selectedDeg.diplomaDate && <Descriptions.Item label="Дата диплома">{formatDate(selectedDeg.diplomaDate)}</Descriptions.Item>}
              {selectedDeg.issuedBy && <Descriptions.Item label="Ким видано">{selectedDeg.issuedBy}</Descriptions.Item>}
            </Descriptions>
            {selectedDeg.id && (
              <div style={{ marginTop: 16 }}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>📎 Прикріплені файли</Text>
                <FileAttachmentList
                  entityType="ACADEMIC_DEGREE"
                  entityId={selectedDeg.id}
                  teacherId={teacherId}
                  editable={false}
                  maxCount={3}
                />
              </div>
            )}
          </>
        )}
      </Drawer>

      {/* ── Academic Title modal ──────────────────────────────── */}
      <Modal
        title={editingTitle ? 'Редагувати вчене звання' : 'Додати вчене звання'}
        open={titleModalOpen}
        onOk={handleTitleSave}
        onCancel={() => { setTitleModalOpen(false); titleForm.resetFields(); titleFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={titleForm} layout="vertical">
          <Form.Item label="Прикріплені файли (атестат, скан)">
            <FileAttachmentList
              ref={titleFileRef}
              entityType="ACADEMIC_TITLE"
              entityId={editingTitle?.id}
              teacherId={teacherId}
              maxCount={3}
            />
          </Form.Item>
          <Form.Item
            name="titleName"
            label="Вчене звання"
            tooltip="Зазвичай повний запис, напр. «Доцент кафедри комп'ютерних наук»"
            rules={[{ required: true, message: 'Вкажіть звання' }]}
          >
            <AutoComplete
              placeholder="Напр. Доцент кафедри ..."
              options={[
                'Професор',
                'Доцент',
                'Старший науковий співробітник',
                'Старший дослідник',
              ].map(v => ({ value: v }))}
              filterOption={(inputValue, option) =>
                String(option?.value || '').toLowerCase().includes(inputValue.toLowerCase())
              }
            />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="attestat" label="Атестат (серія, номер)" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="attestatDate" label="Дата атестата" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="issuedBy" label="Ким видано">
            <Input placeholder="Атестаційна колегія МОН України..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Academic Title drawer ──────────────────────────────── */}
      <Drawer
        title={selectedTitle?.titleName || 'Деталі звання'}
        placement="right"
        width={480}
        open={titleDrawerOpen}
        onClose={() => { setTitleDrawerOpen(false); setSelectedTitle(null); }}
        extra={
          <Space>
            <Button icon={<EditOutlined />} size="small" onClick={() => { setTitleDrawerOpen(false); selectedTitle && openTitleModal(selectedTitle); }}>
              Редагувати
            </Button>
          </Space>
        }
      >
        {selectedTitle && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Звання">{selectedTitle.titleName || '—'}</Descriptions.Item>
              {selectedTitle.attestat && <Descriptions.Item label="Атестат">{selectedTitle.attestat}</Descriptions.Item>}
              {selectedTitle.attestatDate && <Descriptions.Item label="Дата атестата">{formatDate(selectedTitle.attestatDate)}</Descriptions.Item>}
              {selectedTitle.issuedBy && <Descriptions.Item label="Ким видано">{selectedTitle.issuedBy}</Descriptions.Item>}
            </Descriptions>
            {selectedTitle.id && (
              <div style={{ marginTop: 16 }}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>📎 Прикріплені файли</Text>
                <FileAttachmentList
                  entityType="ACADEMIC_TITLE"
                  entityId={selectedTitle.id}
                  teacherId={teacherId}
                  editable={false}
                  maxCount={3}
                />
              </div>
            )}
          </>
        )}
      </Drawer>

      {/* ── Military Education modal ──────────────────────────────── */}
      <Modal
        title={editingMilEdu ? 'Редагувати військову освіту' : 'Додати військову освіту'}
        open={milEduModalOpen}
        onOk={handleMilEduSave}
        onCancel={() => { setMilEduModalOpen(false); milEduForm.resetFields(); milEduFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={milEduForm} layout="vertical">
          <Form.Item label="Прикріплені файли">
            <FileAttachmentList
              ref={milEduFileRef}
              entityType="MILITARY_EDUCATION"
              entityId={editingMilEdu?.id}
              teacherId={teacherId}
              maxCount={3}
            />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="level" label="Рівень ВО" style={{ width: 200 }} rules={[{ required: true, message: 'Оберіть рівень' }]}>
              <Select placeholder="Оберіть рівень">
                <Select.Option value="OPERATIONAL">Оперативний</Select.Option>
                <Select.Option value="STRATEGIC">Стратегічний</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="graduationYear" label="Рік закінчення" style={{ width: 130 }}>
              <InputNumber min={1950} max={2035} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="institution" label="Заклад освіти" rules={[{ required: true, message: 'Вкажіть ВНЗ' }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="speciality" label="Спеціальність">
            <Input />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="diploma" label="Диплом (серія, номер)" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="diplomaDate" label="Дата диплому" style={{ width: 180 }}>
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="issuedBy" label="Ким видано">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Military Education drawer ──────────────────────────────── */}
      <Drawer
        title={selectedMilEdu?.institution || 'Деталі військової освіти'}
        placement="right"
        width={480}
        open={milEduDrawerOpen}
        onClose={() => { setMilEduDrawerOpen(false); setSelectedMilEdu(null); }}
        extra={
          <Space>
            <Button icon={<EditOutlined />} size="small" onClick={() => { setMilEduDrawerOpen(false); selectedMilEdu && openMilEduModal(selectedMilEdu); }}>
              Редагувати
            </Button>
          </Space>
        }
      >
        {selectedMilEdu && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Рівень">
                {selectedMilEdu.level === 'STRATEGIC' ? 'Стратегічний' : selectedMilEdu.level === 'OPERATIONAL' ? 'Оперативний' : '—'}
              </Descriptions.Item>
              {selectedMilEdu.institution && <Descriptions.Item label="Заклад освіти">{selectedMilEdu.institution}</Descriptions.Item>}
              {selectedMilEdu.speciality && <Descriptions.Item label="Спеціальність">{selectedMilEdu.speciality}</Descriptions.Item>}
              {selectedMilEdu.graduationYear && <Descriptions.Item label="Рік закінчення">{selectedMilEdu.graduationYear}</Descriptions.Item>}
              {selectedMilEdu.diploma && <Descriptions.Item label="Диплом">{selectedMilEdu.diploma}</Descriptions.Item>}
              {selectedMilEdu.diplomaDate && <Descriptions.Item label="Дата диплому">{formatDate(selectedMilEdu.diplomaDate)}</Descriptions.Item>}
              {selectedMilEdu.issuedBy && <Descriptions.Item label="Ким видано">{selectedMilEdu.issuedBy}</Descriptions.Item>}
            </Descriptions>
            {selectedMilEdu.id && (
              <div style={{ marginTop: 16 }}>
                <Text strong>Прикріплені файли</Text>
                <FileAttachmentList
                  entityType="MILITARY_EDUCATION"
                  entityId={selectedMilEdu.id}
                  teacherId={teacherId}
                  editable={false}
                  maxCount={3}
                />
              </div>
            )}
          </>
        )}
      </Drawer>

      {/* ── Military courses (L2/L3/L4) modal ──────────────────────────────── */}
      <Modal
        title={editingMc ? 'Редагувати курс ПВО' : 'Додати курс ПВО'}
        open={mcModalOpen}
        onOk={handleMcSave}
        onCancel={() => { setMcModalOpen(false); mcForm.resetFields(); mcFileRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={mcForm} layout="vertical">
          <Form.Item label="Прикріплені файли">
            <FileAttachmentList
              ref={mcFileRef}
              entityType="QUALIFICATION_IMPROVEMENT"
              entityId={editingMc?.id}
              teacherId={teacherId}
              maxCount={3}
            />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="militaryCourseLevel" label="Рівень" style={{ width: 180 }} rules={[{ required: true, message: 'Оберіть рівень' }]}>
              <Select placeholder="Рівень">
                <Select.Option value="L2">L2 (5 балів)</Select.Option>
                <Select.Option value="L3">L3 (10 балів)</Select.Option>
                <Select.Option value="L4">L4 (15 балів)</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="title" label="Назва" style={{ flex: 1 }} rules={[{ required: true, message: 'Введіть назву' }]}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="organization" label="Організація">
            <Input />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="startDate" label="Дата початку">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="endDate" label="Дата закінчення">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="hours" label="Години">
              <InputNumber min={0} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="credits" label="Кредити">
              <InputNumber min={0} step={0.5} style={{ width: 100 }} />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="certificateNumber" label="Номер сертифікату" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="certificateDate" label="Дата серт.">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* ── Military courses (L2/L3/L4) drawer ──────────────────────────────── */}
      <Drawer
        title={selectedMc?.title || 'Курси ПВО'}
        placement="right"
        width={480}
        open={mcDrawerOpen}
        onClose={() => { setMcDrawerOpen(false); setSelectedMc(null); }}
        extra={
          <Space>
            <Button icon={<EditOutlined />} size="small" onClick={() => { setMcDrawerOpen(false); selectedMc && openMcModal(selectedMc); }}>
              Редагувати
            </Button>
          </Space>
        }
      >
        {selectedMc && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Рівень">
                <Tag color="volcano">{MILITARY_COURSE_LEVEL_LABELS[selectedMc.militaryCourseLevel as MilitaryCourseLevel] || selectedMc.militaryCourseLevel}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Назва">{selectedMc.title}</Descriptions.Item>
              {selectedMc.organization && <Descriptions.Item label="Організація">{selectedMc.organization}</Descriptions.Item>}
              {selectedMc.startDate && <Descriptions.Item label="Початок">{formatDate(selectedMc.startDate)}</Descriptions.Item>}
              {selectedMc.endDate && <Descriptions.Item label="Кінець">{formatDate(selectedMc.endDate)}</Descriptions.Item>}
              {selectedMc.hours != null && <Descriptions.Item label="Години">{selectedMc.hours}</Descriptions.Item>}
              {selectedMc.credits != null && <Descriptions.Item label="Кредити">{selectedMc.credits}</Descriptions.Item>}
              {selectedMc.certificateNumber && <Descriptions.Item label="Сертифікат">{selectedMc.certificateNumber}</Descriptions.Item>}
              {selectedMc.certificateDate && <Descriptions.Item label="Дата серт.">{formatDate(selectedMc.certificateDate)}</Descriptions.Item>}
              {selectedMc.certificateUrl && (
                <Descriptions.Item label="Посилання">
                  <a href={selectedMc.certificateUrl} target="_blank" rel="noopener noreferrer">
                    {selectedMc.certificateUrl.length > 50 ? selectedMc.certificateUrl.substring(0, 50) + '...' : selectedMc.certificateUrl}
                  </a>
                </Descriptions.Item>
              )}
            </Descriptions>
            {selectedMc.id && (
              <div style={{ marginTop: 16 }}>
                <Text strong>Прикріплені файли</Text>
                <FileAttachmentList
                  entityType="QUALIFICATION_IMPROVEMENT"
                  entityId={selectedMc.id}
                  teacherId={teacherId}
                  editable={false}
                />
              </div>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default TeacherProfilePage;
