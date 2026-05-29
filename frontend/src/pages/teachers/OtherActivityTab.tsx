import React, { useEffect, useState, useRef } from 'react';
import { Table, Button, Modal, Form, Input, DatePicker, Select, Space, Card, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { parseDate, formatDate } from '../../utils/dateUtils';
import type { OpenLesson, MethodologicalExperiment, AcademicMobility, ForeignInternship } from '../../types/rating';
import { LESSON_TYPE_OPTIONS } from '../../types/rating';
import {
  getOpenLessons, createOpenLesson, updateOpenLesson, deleteOpenLesson,
  getExperiments, createExperiment, updateExperiment, deleteExperiment,
  getMobilities, createMobility, updateMobility, deleteMobility,
  getForeignInternships, createForeignInternship, updateForeignInternship, deleteForeignInternship,
} from '../../api/ratingApi';
import FileAttachmentList from '../../components/FileAttachmentList';
import { getDepartments } from '../../api/departmentApi';
import type { Department } from '../../types/teacher';

interface Props {
  teacherId: number;
}

const OtherActivityTab: React.FC<Props> = ({ teacherId }) => {
  // ── State ──
  const [lessons, setLessons] = useState<OpenLesson[]>([]);
  const [experiments, setExperiments] = useState<MethodologicalExperiment[]>([]);
  const [mobilities, setMobilities] = useState<AcademicMobility[]>([]);
  const [internships, setInternships] = useState<ForeignInternship[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);

  // Modals
  const [lessonModal, setLessonModal] = useState(false);
  const [experimentModal, setExperimentModal] = useState(false);
  const [mobilityModal, setMobilityModal] = useState(false);
  const [internshipModal, setInternshipModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  const [lessonForm] = Form.useForm();
  const [experimentForm] = Form.useForm();
  const [mobilityForm] = Form.useForm();
  const [internshipForm] = Form.useForm();

  const lessonFileRef = useRef<any>(null);
  const experimentFileRef = useRef<any>(null);
  const mobilityFileRef = useRef<any>(null);
  const internshipFileRef = useRef<any>(null);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [l, e, m, fi, depts] = await Promise.all([
        getOpenLessons(teacherId),
        getExperiments(teacherId),
        getMobilities(teacherId),
        getForeignInternships(teacherId),
        getDepartments(),
      ]);
      setDepartments(depts);
      setLessons(l);
      setExperiments(e);
      setMobilities(m);
      setInternships(fi);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAll(); }, [teacherId]);

  // ══════════════════════════════════════════════
  //  OpenLesson
  // ══════════════════════════════════════════════

  const openLessonModal = (record?: OpenLesson) => {
    if (record) {
      setEditingId(record.id!);
      lessonForm.setFieldsValue({
        ...record,
        date: record.date ? parseDate(record.date) : null,
        orderDate: record.orderDate ? parseDate(record.orderDate) : null,
      });
    } else {
      setEditingId(null);
      lessonForm.resetFields();
    }
    setLessonModal(true);
  };

  const handleLessonSave = async () => {
    try {
      const values = await lessonForm.validateFields();
      setSaving(true);
      const payload = {
        ...values,
        date: values.date?.format('DD.MM.YYYY'),
        orderDate: values.orderDate?.format('DD.MM.YYYY'),
      };
      let saved: OpenLesson;
      if (editingId) {
        saved = await updateOpenLesson(teacherId, editingId, payload);
      } else {
        saved = await createOpenLesson(teacherId, payload);
      }
      // Upload pending files
      if (lessonFileRef.current && saved.id) {
        await lessonFileRef.current.uploadPendingFiles('OPEN_LESSON', saved.id, teacherId);
      }
      message.success(editingId ? 'Оновлено' : 'Додано');
      setLessonModal(false);
      fetchAll();
    } catch (err: any) {
      if (err?.response?.data?.message) message.error(err.response.data.message);
    } finally {
      setSaving(false);
    }
  };

  const handleLessonDelete = async (id: number) => {
    await deleteOpenLesson(teacherId, id);
    message.success('Видалено');
    fetchAll();
  };

  const lessonColumns: ColumnsType<OpenLesson> = [
    { title: 'Тема', dataIndex: 'topic', key: 'topic', ellipsis: true },
    { title: 'Дата', dataIndex: 'date', key: 'date', width: 110,
      render: (v) => formatDate(v) },
    { title: 'Кафедра', dataIndex: 'hostDepartment', key: 'hostDepartment', width: 160 },
    { title: 'Тип', dataIndex: 'lessonType', key: 'lessonType', width: 140 },
    { title: 'Наказ', key: 'order', width: 140,
      render: (_, r) => r.orderNumber ? `${r.orderNumber} від ${r.orderDate ? formatDate(r.orderDate) : ''}` : '—' },
    {
      title: 'Дії', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openLessonModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => handleLessonDelete(record.id!)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ══════════════════════════════════════════════
  //  MethodologicalExperiment
  // ══════════════════════════════════════════════

  const openExperimentModal = (record?: MethodologicalExperiment) => {
    if (record) {
      setEditingId(record.id!);
      experimentForm.setFieldsValue({
        ...record,
        date: record.date ? parseDate(record.date) : null,
        orderDate: record.orderDate ? parseDate(record.orderDate) : null,
      });
    } else {
      setEditingId(null);
      experimentForm.resetFields();
    }
    setExperimentModal(true);
  };

  const handleExperimentSave = async () => {
    try {
      const values = await experimentForm.validateFields();
      setSaving(true);
      const payload = {
        ...values,
        date: values.date?.format('DD.MM.YYYY'),
        orderDate: values.orderDate?.format('DD.MM.YYYY'),
      };
      let saved: MethodologicalExperiment;
      if (editingId) {
        saved = await updateExperiment(teacherId, editingId, payload);
      } else {
        saved = await createExperiment(teacherId, payload);
      }
      if (experimentFileRef.current && saved.id) {
        await experimentFileRef.current.uploadPendingFiles('METHODOLOGICAL_EXPERIMENT', saved.id, teacherId);
      }
      message.success(editingId ? 'Оновлено' : 'Додано');
      setExperimentModal(false);
      fetchAll();
    } catch (err: any) {
      if (err?.response?.data?.message) message.error(err.response.data.message);
    } finally {
      setSaving(false);
    }
  };

  const handleExperimentDelete = async (id: number) => {
    await deleteExperiment(teacherId, id);
    message.success('Видалено');
    fetchAll();
  };

  const experimentColumns: ColumnsType<MethodologicalExperiment> = [
    { title: 'Назва', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: 'Дата', dataIndex: 'date', key: 'date', width: 110,
      render: (v) => formatDate(v) },
    { title: 'Наказ', key: 'order', width: 140,
      render: (_, r) => r.orderNumber ? `${r.orderNumber} від ${r.orderDate ? formatDate(r.orderDate) : ''}` : '—' },
    {
      title: 'Дії', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openExperimentModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => handleExperimentDelete(record.id!)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ══════════════════════════════════════════════
  //  AcademicMobility
  // ══════════════════════════════════════════════

  const openMobilityModal = (record?: AcademicMobility) => {
    if (record) {
      setEditingId(record.id!);
      mobilityForm.setFieldsValue({
        ...record,
        dateFrom: record.dateFrom ? parseDate(record.dateFrom) : null,
        dateTo: record.dateTo ? parseDate(record.dateTo) : null,
      });
    } else {
      setEditingId(null);
      mobilityForm.resetFields();
    }
    setMobilityModal(true);
  };

  const handleMobilitySave = async () => {
    try {
      const values = await mobilityForm.validateFields();
      setSaving(true);
      const payload = {
        ...values,
        dateFrom: values.dateFrom?.format('DD.MM.YYYY'),
        dateTo: values.dateTo?.format('DD.MM.YYYY'),
      };
      let saved: AcademicMobility;
      if (editingId) {
        saved = await updateMobility(teacherId, editingId, payload);
      } else {
        saved = await createMobility(teacherId, payload);
      }
      if (mobilityFileRef.current && saved.id) {
        await mobilityFileRef.current.uploadPendingFiles('ACADEMIC_MOBILITY', saved.id, teacherId);
      }
      message.success(editingId ? 'Оновлено' : 'Додано');
      setMobilityModal(false);
      fetchAll();
    } catch (err: any) {
      if (err?.response?.data?.message) message.error(err.response.data.message);
    } finally {
      setSaving(false);
    }
  };

  const handleMobilityDelete = async (id: number) => {
    await deleteMobility(teacherId, id);
    message.success('Видалено');
    fetchAll();
  };

  const mobilityColumns: ColumnsType<AcademicMobility> = [
    { title: 'Програма', dataIndex: 'programName', key: 'programName', ellipsis: true },
    { title: 'Заклад', dataIndex: 'institution', key: 'institution', ellipsis: true },
    { title: 'Країна', dataIndex: 'country', key: 'country', width: 120 },
    { title: 'Період', key: 'period', width: 180,
      render: (_, r) => {
        const from = r.dateFrom ? formatDate(r.dateFrom) : '';
        const to = r.dateTo ? formatDate(r.dateTo) : '';
        return from || to ? `${from} – ${to}` : '—';
      },
    },
    {
      title: 'Дії', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openMobilityModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => handleMobilityDelete(record.id!)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ══════════════════════════════════════════════
  //  ForeignInternship — Міжнародне стажування
  // ══════════════════════════════════════════════

  const openInternshipModal = (record?: ForeignInternship) => {
    if (record) {
      setEditingId(record.id!);
      internshipForm.setFieldsValue({
        ...record,
        dateFrom: record.dateFrom ? parseDate(record.dateFrom) : null,
        dateTo: record.dateTo ? parseDate(record.dateTo) : null,
      });
    } else {
      setEditingId(null);
      internshipForm.resetFields();
    }
    setInternshipModal(true);
  };

  const handleInternshipSave = async () => {
    try {
      const values = await internshipForm.validateFields();
      setSaving(true);
      const payload = {
        ...values,
        dateFrom: values.dateFrom?.format('DD.MM.YYYY'),
        dateTo: values.dateTo?.format('DD.MM.YYYY'),
      };
      let saved: ForeignInternship;
      if (editingId) {
        saved = await updateForeignInternship(teacherId, editingId, payload);
      } else {
        saved = await createForeignInternship(teacherId, payload);
      }
      if (internshipFileRef.current && saved.id) {
        await internshipFileRef.current.uploadPendingFiles('FOREIGN_INTERNSHIP', saved.id, teacherId);
      }
      message.success(editingId ? 'Оновлено' : 'Додано');
      setInternshipModal(false);
      fetchAll();
    } catch (err: any) {
      if (err?.response?.data?.message) message.error(err.response.data.message);
    } finally {
      setSaving(false);
    }
  };

  const handleInternshipDelete = async (id: number) => {
    await deleteForeignInternship(teacherId, id);
    message.success('Видалено');
    fetchAll();
  };

  const internshipColumns: ColumnsType<ForeignInternship> = [
    { title: 'Програма', dataIndex: 'programName', key: 'programName', ellipsis: true },
    { title: 'Заклад', dataIndex: 'institution', key: 'institution', ellipsis: true },
    { title: 'Країна', dataIndex: 'country', key: 'country', width: 120 },
    { title: 'Період', key: 'period', width: 180,
      render: (_, r) => {
        const from = r.dateFrom ? formatDate(r.dateFrom) : '';
        const to = r.dateTo ? formatDate(r.dateTo) : '';
        return from || to ? `${from} – ${to}` : '—';
      },
    },
    {
      title: 'Дії', key: 'actions', width: 80,
      render: (_, record) => (
        <Space size="small">
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openInternshipModal(record)} />
          <Popconfirm title="Видалити?" onConfirm={() => handleInternshipDelete(record.id!)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ══════════════════════════════════════════════
  //  Render
  // ══════════════════════════════════════════════

  return (
    <>
      {/* Відкриті/показові заняття */}
      <Card size="small" title="Відкриті / показові заняття"
        style={{ marginBottom: 16 }}
        extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openLessonModal()}>Додати</Button>}
      >
        <Table dataSource={lessons} columns={lessonColumns} rowKey="id" size="small"
          loading={loading} pagination={false}
          locale={{ emptyText: 'Немає записів' }}
          expandable={{
            expandedRowRender: (record) => record.id ? (
              <FileAttachmentList entityType="OPEN_LESSON" entityId={record.id} teacherId={teacherId} />
            ) : null,
            rowExpandable: (record) => !!record.id,
          }}
        />
      </Card>

      {/* Методичні експерименти */}
      <Card size="small" title="Методичні / педагогічні експерименти"
        style={{ marginBottom: 16 }}
        extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openExperimentModal()}>Додати</Button>}
      >
        <Table dataSource={experiments} columns={experimentColumns} rowKey="id" size="small"
          loading={loading} pagination={false}
          locale={{ emptyText: 'Немає записів' }}
          expandable={{
            expandedRowRender: (record) => record.id ? (
              <FileAttachmentList entityType="METHODOLOGICAL_EXPERIMENT" entityId={record.id} teacherId={teacherId} />
            ) : null,
            rowExpandable: (record) => !!record.id,
          }}
        />
      </Card>

      {/* Академічна мобільність */}
      <Card size="small" title="Академічна мобільність"
        style={{ marginBottom: 16 }}
        extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openMobilityModal()}>Додати</Button>}
      >
        <Table dataSource={mobilities} columns={mobilityColumns} rowKey="id" size="small"
          loading={loading} pagination={false}
          locale={{ emptyText: 'Немає записів' }}
          expandable={{
            expandedRowRender: (record) => record.id ? (
              <FileAttachmentList entityType="ACADEMIC_MOBILITY" entityId={record.id} teacherId={teacherId} />
            ) : null,
            rowExpandable: (record) => !!record.id,
          }}
        />
      </Card>

      {/* Міжнародне стажування */}
      <Card size="small" title="Міжнародне стажування"
        extra={<Button size="small" icon={<PlusOutlined />} onClick={() => openInternshipModal()}>Додати</Button>}
      >
        <Table dataSource={internships} columns={internshipColumns} rowKey="id" size="small"
          loading={loading} pagination={false}
          locale={{ emptyText: 'Немає записів' }}
          expandable={{
            expandedRowRender: (record) => record.id ? (
              <FileAttachmentList entityType="FOREIGN_INTERNSHIP" entityId={record.id} teacherId={teacherId} />
            ) : null,
            rowExpandable: (record) => !!record.id,
          }}
        />
      </Card>

      {/* ── Modal: Open Lesson ── */}
      <Modal title={editingId ? 'Редагувати заняття' : 'Додати заняття'}
        open={lessonModal}
        onOk={handleLessonSave}
        onCancel={() => setLessonModal(false)}
        confirmLoading={saving}
        okText="Зберегти" cancelText="Скасувати" width={560}>
        <Form form={lessonForm} layout="vertical">
          <Form.Item name="topic" label="Тема" rules={[{ required: true, message: "Обов'язково" }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size="middle" style={{ width: '100%' }}>
            <Form.Item name="date" label="Дата">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="lessonType" label="Тип">
              <Select options={LESSON_TYPE_OPTIONS} style={{ width: 180 }} allowClear />
            </Form.Item>
          </Space>
          <Form.Item name="hostDepartment" label="Кафедра проведення">
            <Select
              showSearch
              allowClear
              placeholder="Оберіть кафедру"
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
              options={departments.map(d => ({
                value: d.name,
                label: d.number ? `${d.number} — ${d.name}` : d.name,
              }))}
            />
          </Form.Item>
          <Space size="middle">
            <Form.Item name="orderNumber" label="№ наказу">
              <Input style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="orderDate" label="Дата наказу">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Примітки">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!editingId && (
            <Form.Item label="Файл">
              <FileAttachmentList ref={lessonFileRef} entityType="OPEN_LESSON" teacherId={teacherId} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* ── Modal: Experiment ── */}
      <Modal title={editingId ? 'Редагувати експеримент' : 'Додати експеримент'}
        open={experimentModal}
        onOk={handleExperimentSave}
        onCancel={() => setExperimentModal(false)}
        confirmLoading={saving}
        okText="Зберегти" cancelText="Скасувати" width={560}>
        <Form form={experimentForm} layout="vertical">
          <Form.Item name="title" label="Назва" rules={[{ required: true, message: "Обов'язково" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Опис">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Space size="middle">
            <Form.Item name="date" label="Дата">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="orderNumber" label="№ наказу">
              <Input style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="orderDate" label="Дата наказу">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Примітки">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!editingId && (
            <Form.Item label="Файл">
              <FileAttachmentList ref={experimentFileRef} entityType="METHODOLOGICAL_EXPERIMENT" teacherId={teacherId} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* ── Modal: Mobility ── */}
      <Modal title={editingId ? 'Редагувати мобільність' : 'Додати мобільність'}
        open={mobilityModal}
        onOk={handleMobilitySave}
        onCancel={() => setMobilityModal(false)}
        confirmLoading={saving}
        okText="Зберегти" cancelText="Скасувати" width={560}>
        <Form form={mobilityForm} layout="vertical">
          <Form.Item name="programName" label="Назва програми" rules={[{ required: true, message: "Обов'язково" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="institution" label="Навчальний заклад">
            <Input />
          </Form.Item>
          <Form.Item name="country" label="Країна">
            <Input />
          </Form.Item>
          <Space size="middle">
            <Form.Item name="dateFrom" label="Дата початку">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="dateTo" label="Дата завершення">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="Опис діяльності">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="notes" label="Примітки">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!editingId && (
            <Form.Item label="Файл">
              <FileAttachmentList ref={mobilityFileRef} entityType="ACADEMIC_MOBILITY" teacherId={teacherId} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* ── Modal: Foreign Internship ── */}
      <Modal title={editingId ? 'Редагувати стажування' : 'Додати міжнародне стажування'}
        open={internshipModal}
        onOk={handleInternshipSave}
        onCancel={() => setInternshipModal(false)}
        confirmLoading={saving}
        okText="Зберегти" cancelText="Скасувати" width={560}>
        <Form form={internshipForm} layout="vertical">
          <Form.Item name="programName" label="Назва програми / напряму" rules={[{ required: true, message: "Обов'язково" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="institution" label="Заклад приймаючої сторони">
            <Input />
          </Form.Item>
          <Form.Item name="country" label="Країна" rules={[{ required: true, message: "Обов'язково" }]}>
            <Input />
          </Form.Item>
          <Space size="middle">
            <Form.Item name="dateFrom" label="Дата початку">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="dateTo" label="Дата завершення">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="Опис діяльності">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="notes" label="Примітки">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!editingId && (
            <Form.Item label="Файл">
              <FileAttachmentList ref={internshipFileRef} entityType="FOREIGN_INTERNSHIP" teacherId={teacherId} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
};

export default OtherActivityTab;
