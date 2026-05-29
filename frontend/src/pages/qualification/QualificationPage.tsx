import React, { useEffect, useState, useRef } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, DatePicker, Space, Card, Popconfirm, message, Descriptions, Drawer, Typography, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { parseDate, formatDate } from '@/utils/dateUtils';
import { QualificationImprovement, QUALIFICATION_CATEGORY_LABELS } from '@/types/qualification';
import { getQualifications, createQualification, updateQualification, deleteQualification } from '@/api/qualificationApi';
import { useAuth } from '@/auth/AuthContext';
import FileAttachmentList from '@/components/FileAttachmentList';
import type { FileAttachmentListRef } from '@/components/FileAttachmentList';

interface Props {
  teacherId?: number;
}

const QualificationPage: React.FC<Props> = ({ teacherId: teacherIdProp }) => {
  const { user } = useAuth();
  const teacherId = teacherIdProp ?? user?.teacherId ?? undefined;
  const [qualifications, setQualifications] = useState<QualificationImprovement[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<QualificationImprovement | null>(null);
  const [form] = Form.useForm();
  const fileListRef = useRef<FileAttachmentListRef>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const allData = await getQualifications(teacherId);
      // Курси ВО (L2/L3/L4) тепер на вкладці "Основні дані"
      const data = allData.filter((q: QualificationImprovement) => q.category !== 'MILITARY_COURSE');
      setQualifications(data);
      if (selectedRecord) {
        const updated = data.find((d: QualificationImprovement) => d.id === selectedRecord.id);
        if (updated) setSelectedRecord(updated);
        else { setSelectedRecord(null); setDrawerOpen(false); }
      }
    } catch {
      message.error('Помилка завантаження');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [teacherId]);

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        startDate: values.startDate?.format('DD.MM.YYYY'),
        endDate: values.endDate?.format('DD.MM.YYYY'),
        certificateDate: values.certificateDate?.format('DD.MM.YYYY'),
        teacher: teacherId ? { id: teacherId } : undefined,
      };
      if (editingId) {
        await updateQualification(editingId, payload);
        message.success('Запис оновлено');
      } else {
        const created = await createQualification(payload);
        // Upload pending files
        const pending = fileListRef.current?.getPendingFiles() || [];
        if (pending.length > 0) {
          await fileListRef.current?.uploadPendingFiles('QUALIFICATION_IMPROVEMENT', created.id, teacherId);
        }
        message.success('Запис додано');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingId(null);
      loadData();
    } catch {
      // validation
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (record: QualificationImprovement) => {
    setEditingId(record.id);
    form.setFieldsValue({
      ...record,
      startDate: record.startDate ? parseDate(record.startDate) : null,
      endDate: record.endDate ? parseDate(record.endDate) : null,
      certificateDate: record.certificateDate ? parseDate(record.certificateDate) : null,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    await deleteQualification(id);
    message.success('Запис видалено');
    loadData();
  };

  const columns: ColumnsType<QualificationImprovement> = [
    { title: 'Назва', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: 'Організація', dataIndex: 'organization', key: 'organization', ellipsis: true },
    { title: 'Початок', dataIndex: 'startDate', key: 'startDate', width: 120, render: (v: string) => formatDate(v) },
    { title: 'Кінець', dataIndex: 'endDate', key: 'endDate', width: 120, render: (v: string) => formatDate(v) },
    { title: 'Години', dataIndex: 'hours', key: 'hours', width: 80 },
    { title: 'Кредити', dataIndex: 'credits', key: 'credits', width: 80 },
    { title: 'Країна', dataIndex: 'country', key: 'country', width: 100,
      render: (v: string) => v || '—',
    },
    { title: 'Категорія', dataIndex: 'category', key: 'category', width: 140,
      render: (v: string) => v ? (
        <Tag color="blue">
          {QUALIFICATION_CATEGORY_LABELS[v as keyof typeof QUALIFICATION_CATEGORY_LABELS] || v}
        </Tag>
      ) : '—',
    },
    { title: 'Сертифікат', dataIndex: 'certificateNumber', key: 'certificateNumber', width: 150 },
    { title: 'Дата серт.', dataIndex: 'certificateDate', key: 'certificateDate', width: 120, render: (v: string) => formatDate(v) },
    {
      title: 'Дії', key: 'actions', width: 100,
      render: (_, record) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => handleEdit(record)} />
          <Popconfirm title="Видалити запис?" onConfirm={() => handleDelete(record.id)}>
            <Button icon={<DeleteOutlined />} size="small" danger />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Підвищення кваліфікації"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); form.resetFields(); setModalOpen(true); }}>
          Додати запис
        </Button>
      }
    >
      <Table
        columns={columns}
        dataSource={qualifications}
        rowKey="id"
        loading={loading}
        onRow={(record) => ({
          onClick: (e) => {
            if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm')) return;
            setSelectedRecord(record);
            setDrawerOpen(true);
          },
          style: { cursor: 'pointer' },
        })}
      />

      <Modal
        title={editingId ? 'Редагувати запис' : 'Новий запис'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditingId(null); fileListRef.current?.clearPending(); }}
        confirmLoading={saving}
        width={700}
        okText="Зберегти"
        cancelText="Скасувати"
      >
        <Form form={form} layout="vertical">
          <Form.Item label="Прикріплені файли">
            <FileAttachmentList
              ref={fileListRef}
              entityType="QUALIFICATION_IMPROVEMENT"
              entityId={editingId}
              teacherId={teacherId}
            />
          </Form.Item>
          <Form.Item name="title" label="Назва" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="organization" label="Організація">
            <Input />
          </Form.Item>
          <Space>
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
          </Space>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="certificateNumber" label="Номер сертифікату" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="certificateDate" label="Дата видачі серт.">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </div>
          <Form.Item name="country" label="Країна">
            <Input placeholder="Україна, Польща, США..." />
          </Form.Item>
          <Form.Item name="certificateUrl" label="Зовнішнє посилання" tooltip="Посилання на зовнішній ресурс (сайт організації, реєстр). Файли завантажуйте через блок нижче.">
            <Input placeholder="https://prometheus.org.ua/..., https://coursera.org/..." />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={selectedRecord?.title || 'Деталі'}
        placement="right"
        width={480}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setSelectedRecord(null); }}
      >
        {selectedRecord && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Назва">{selectedRecord.title}</Descriptions.Item>
              {selectedRecord.organization && <Descriptions.Item label="Організація">{selectedRecord.organization}</Descriptions.Item>}
              {selectedRecord.startDate && <Descriptions.Item label="Початок">{formatDate(selectedRecord.startDate)}</Descriptions.Item>}
              {selectedRecord.endDate && <Descriptions.Item label="Кінець">{formatDate(selectedRecord.endDate)}</Descriptions.Item>}
              {selectedRecord.hours != null && <Descriptions.Item label="Години">{selectedRecord.hours}</Descriptions.Item>}
              {selectedRecord.credits != null && <Descriptions.Item label="Кредити">{selectedRecord.credits}</Descriptions.Item>}
              {selectedRecord.certificateNumber && <Descriptions.Item label="Сертифікат">{selectedRecord.certificateNumber}</Descriptions.Item>}
              {selectedRecord.certificateDate && <Descriptions.Item label="Дата серт.">{formatDate(selectedRecord.certificateDate)}</Descriptions.Item>}
              {selectedRecord.certificateUrl && (
                <Descriptions.Item label="Зовнішнє посилання">
                  <a href={selectedRecord.certificateUrl} target="_blank" rel="noopener noreferrer">
                    {selectedRecord.certificateUrl.length > 50 ? selectedRecord.certificateUrl.substring(0, 50) + '...' : selectedRecord.certificateUrl}
                  </a>
                </Descriptions.Item>
              )}
            </Descriptions>

            <div style={{ marginTop: 16 }}>
              <Typography.Text strong>Прикріплені файли</Typography.Text>
              <FileAttachmentList
                entityType="QUALIFICATION_IMPROVEMENT"
                entityId={selectedRecord.id}
                teacherId={teacherId}
                editable={false}
              />
            </div>
          </>
        )}
      </Drawer>
    </Card>
  );
};

export default QualificationPage;
