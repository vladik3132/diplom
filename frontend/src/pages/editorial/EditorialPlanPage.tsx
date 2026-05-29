import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Space, Card, Popconfirm, message, Tag, DatePicker, Select, Collapse } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { parseDate, formatDate } from '@/utils/dateUtils';
import { EditorialPlan, EditorialPlanItem, EDITORIAL_STATUS_LABELS } from '@/types/editorial';
import { getEditorialPlans, createEditorialPlan, deleteEditorialPlan, getEditorialItems, createEditorialItem, updateEditorialItem, deleteEditorialItem } from '@/api/editorialApi';

const EditorialPlanPage: React.FC = () => {
  const [plans, setPlans] = useState<EditorialPlan[]>([]);
  const [, setLoading] = useState(false);
  const [planModalOpen, setPlanModalOpen] = useState(false);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [items, setItems] = useState<Record<number, EditorialPlanItem[]>>({});
  const [editingItemId, setEditingItemId] = useState<number | null>(null);
  const [planForm] = Form.useForm();
  const [itemForm] = Form.useForm();

  const loadPlans = async () => {
    setLoading(true);
    try {
      const data = await getEditorialPlans();
      setPlans(data);
    } catch {
      message.error('Помилка завантаження планів');
    } finally {
      setLoading(false);
    }
  };

  const loadItems = async (planId: number) => {
    try {
      const data = await getEditorialItems(planId);
      setItems(prev => ({ ...prev, [planId]: data }));
    } catch {
      message.error('Помилка завантаження елементів');
    }
  };

  useEffect(() => { loadPlans(); }, []);

  const handleCreatePlan = async () => {
    try {
      const values = await planForm.validateFields();
      await createEditorialPlan(values);
      message.success('План створено');
      setPlanModalOpen(false);
      planForm.resetFields();
      loadPlans();
    } catch {
      // validation
    }
  };

  const handleDeletePlan = async (id: number) => {
    await deleteEditorialPlan(id);
    message.success('План видалено');
    loadPlans();
  };

  const handleSaveItem = async () => {
    try {
      const values = await itemForm.validateFields();
      const payload = {
        ...values,
        plannedDate: values.plannedDate?.format('DD.MM.YYYY'),
        actualDate: values.actualDate?.format('DD.MM.YYYY'),
        plan: { id: selectedPlanId },
      };
      if (editingItemId) {
        await updateEditorialItem(editingItemId, payload);
        message.success('Елемент оновлено');
      } else {
        await createEditorialItem(payload);
        message.success('Елемент додано');
      }
      setItemModalOpen(false);
      itemForm.resetFields();
      setEditingItemId(null);
      if (selectedPlanId) loadItems(selectedPlanId);
    } catch {
      // validation
    }
  };

  const handleDeleteItem = async (id: number, planId: number) => {
    await deleteEditorialItem(id);
    message.success('Елемент видалено');
    loadItems(planId);
  };

  const statusColor: Record<string, string> = {
    PLANNED: 'blue', IN_PROGRESS: 'orange', COMPLETED: 'green', OVERDUE: 'red',
  };

  const itemColumns: ColumnsType<EditorialPlanItem> = [
    { title: 'Назва', dataIndex: 'title', key: 'title' },
    { title: 'Тип', dataIndex: 'type', key: 'type', width: 150 },
    { title: 'Заплановано', dataIndex: 'plannedDate', key: 'plannedDate', width: 120, render: (v: string) => formatDate(v) },
    { title: 'Фактично', dataIndex: 'actualDate', key: 'actualDate', width: 120, render: (v: string) => formatDate(v) },
    {
      title: 'Статус', dataIndex: 'status', key: 'status', width: 130,
      render: (status: string) => <Tag color={statusColor[status]}>{EDITORIAL_STATUS_LABELS[status]}</Tag>,
    },
    {
      title: 'Дії', key: 'actions', width: 100,
      render: (_, record) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => {
            setEditingItemId(record.id);
            setSelectedPlanId(record.plan?.id || null);
            itemForm.setFieldsValue({
              ...record,
              plannedDate: record.plannedDate ? parseDate(record.plannedDate) : null,
              actualDate: record.actualDate ? parseDate(record.actualDate) : null,
            });
            setItemModalOpen(true);
          }} />
          <Popconfirm title="Видалити елемент?" onConfirm={() => handleDeleteItem(record.id, record.plan?.id || 0)}>
            <Button icon={<DeleteOutlined />} size="small" danger />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card
        title="Редакційно-видавничі плани"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { planForm.resetFields(); setPlanModalOpen(true); }}>
            Додати план
          </Button>
        }
      >
        <Collapse
          onChange={(keys) => {
            const openKeys = Array.isArray(keys) ? keys : [keys];
            openKeys.forEach(k => {
              const planId = Number(k);
              if (!items[planId]) loadItems(planId);
            });
          }}
          items={plans.map(plan => ({
            key: String(plan.id),
            label: `${plan.title} (${plan.academicYear || ''})`,
            extra: (
              <Popconfirm title="Видалити план?" onConfirm={(e) => { e?.stopPropagation(); handleDeletePlan(plan.id); }}>
                <Button icon={<DeleteOutlined />} size="small" danger onClick={e => e.stopPropagation()} />
              </Popconfirm>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => { setSelectedPlanId(plan.id); setEditingItemId(null); itemForm.resetFields(); setItemModalOpen(true); }}
                >
                  Додати елемент
                </Button>
                <Table
                  columns={itemColumns}
                  dataSource={items[plan.id] || []}
                  rowKey="id"
                  size="small"
                  pagination={false}
                />
              </Space>
            ),
          }))}
        />
      </Card>

      <Modal title="Новий план" open={planModalOpen} onOk={handleCreatePlan} onCancel={() => setPlanModalOpen(false)} okText="Створити" cancelText="Скасувати">
        <Form form={planForm} layout="vertical">
          <Form.Item name="title" label="Назва" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="academicYear" label="Навчальний рік">
            <Input placeholder="2025-2026" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingItemId ? 'Редагувати елемент' : 'Новий елемент'}
        open={itemModalOpen}
        onOk={handleSaveItem}
        onCancel={() => { setItemModalOpen(false); itemForm.resetFields(); setEditingItemId(null); }}
        okText="Зберегти"
        cancelText="Скасувати"
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item name="title" label="Назва" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="Тип">
            <Input placeholder="Підручник, посібник, методичка..." />
          </Form.Item>
          <Space>
            <Form.Item name="plannedDate" label="Заплановано">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
            <Form.Item name="actualDate" label="Фактично">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
          <Form.Item name="status" label="Статус" rules={[{ required: true }]}>
            <Select options={Object.entries(EDITORIAL_STATUS_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default EditorialPlanPage;
