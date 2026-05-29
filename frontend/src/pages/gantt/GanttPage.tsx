import React, { useEffect, useState } from 'react';
import { Card, Button, Modal, Form, Input, Select, DatePicker, ColorPicker, Space, message, Table, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { parseDate, formatDate } from '@/utils/dateUtils';
import { GanttEvent } from '@/types/gantt';
import { getGanttEvents, createGanttEvent, updateGanttEvent, deleteGanttEvent } from '@/api/ganttApi';

const EVENT_TYPES = [
  { value: 'CONFERENCE', label: 'Конференція' },
  { value: 'PUBLICATION', label: 'Публікація' },
  { value: 'QUALIFICATION', label: 'Підвищення кваліфікації' },
  { value: 'DOCUMENT_DEADLINE', label: 'Дедлайн документа' },
  { value: 'OTHER', label: 'Інше' },
];

const GanttPage: React.FC = () => {
  const [events, setEvents] = useState<GanttEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await getGanttEvents();
      setEvents(data);
    } catch {
      message.error('Помилка завантаження подій');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        startDate: values.startDate?.format('DD.MM.YYYY'),
        endDate: values.endDate?.format('DD.MM.YYYY'),
        color: typeof values.color === 'string' ? values.color : values.color?.toHexString?.() || '#1890ff',
      };
      if (editingId) {
        await updateGanttEvent(editingId, payload);
        message.success('Подію оновлено');
      } else {
        await createGanttEvent(payload);
        message.success('Подію додано');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingId(null);
      loadData();
    } catch {
      // validation
    }
  };

  const handleEdit = (record: GanttEvent) => {
    setEditingId(record.id);
    form.setFieldsValue({
      ...record,
      startDate: record.startDate ? parseDate(record.startDate) : null,
      endDate: record.endDate ? parseDate(record.endDate) : null,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    await deleteGanttEvent(id);
    message.success('Подію видалено');
    loadData();
  };

  const typeLabels: Record<string, string> = Object.fromEntries(EVENT_TYPES.map(t => [t.value, t.label]));

  const columns: ColumnsType<GanttEvent> = [
    { title: 'Назва', dataIndex: 'title', key: 'title' },
    {
      title: 'Тип', dataIndex: 'eventType', key: 'eventType', width: 200,
      render: (type: string) => <Tag>{typeLabels[type] || type}</Tag>,
    },
    { title: 'Початок', dataIndex: 'startDate', key: 'startDate', width: 120, render: (v: string) => formatDate(v) },
    { title: 'Кінець', dataIndex: 'endDate', key: 'endDate', width: 120, render: (v: string) => formatDate(v) },
    { title: 'Рік', dataIndex: 'academicYear', key: 'academicYear', width: 120 },
    { title: 'Семестр', dataIndex: 'semester', key: 'semester', width: 90 },
    {
      title: 'Колір', dataIndex: 'color', key: 'color', width: 60,
      render: (color: string) => <div style={{ width: 20, height: 20, borderRadius: 4, background: color || '#1890ff' }} />,
    },
    {
      title: 'Дії', key: 'actions', width: 100,
      render: (_, record) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => handleEdit(record)} />
          <Button icon={<DeleteOutlined />} size="small" danger onClick={() => handleDelete(record.id)} />
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Діаграма Ганта — Заходи"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); form.resetFields(); setModalOpen(true); }}>
          Додати подію
        </Button>
      }
    >
      <Table columns={columns} dataSource={events} rowKey="id" loading={loading} />

      <Modal
        title={editingId ? 'Редагувати подію' : 'Нова подія'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditingId(null); }}
        width={600}
        okText="Зберегти"
        cancelText="Скасувати"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="Назва" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="eventType" label="Тип" rules={[{ required: true }]}>
            <Select options={EVENT_TYPES} />
          </Form.Item>
          <Space>
            <Form.Item name="startDate" label="Початок" rules={[{ required: true }]}>
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="endDate" label="Кінець" rules={[{ required: true }]}>
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="academicYear" label="Навчальний рік">
              <Input placeholder="2025-2026" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="semester" label="Семестр">
              <Select style={{ width: 100 }} options={[{ value: 1, label: '1' }, { value: 2, label: '2' }]} allowClear />
            </Form.Item>
            <Form.Item name="color" label="Колір">
              <ColorPicker />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Примітки">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default GanttPage;
