import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Select, Input, DatePicker, Space, Popconfirm, message, Card } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { formatDate } from '../utils/dateUtils';
import type { ProgramWorkingGroup } from '../types/rating';
import { WORKING_GROUP_ROLE_LABELS } from '../types/rating';
import {
  getWorkingGroup, addWorkingGroupMember, updateWorkingGroupMember, removeWorkingGroupMember,
} from '../api/ratingApi';
import type { Teacher } from '../types/teacher';
import { getTeachers } from '../api/teacherApi';

interface Props {
  programId: number;
}

const ProgramWorkingGroupSection: React.FC<Props> = ({ programId }) => {
  const [members, setMembers] = useState<ProgramWorkingGroup[]>([]);
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const [m, t] = await Promise.all([
        getWorkingGroup(programId),
        getTeachers(),
      ]);
      setMembers(m);
      setTeachers(t);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [programId]);

  const handleAdd = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await addWorkingGroupMember(programId, {
        teacherId: values.teacherId,
        role: values.role,
        orderNumber: values.orderNumber,
        orderDate: values.orderDate?.format('DD.MM.YYYY'),
      });
      message.success('Додано до робочої групи');
      setModalOpen(false);
      form.resetFields();
      fetchData();
    } catch (err: any) {
      if (err?.response?.data?.message) message.error(err.response.data.message);
    } finally {
      setSaving(false);
    }
  };

  const handleRoleChange = async (id: number, newRole: string) => {
    try {
      const member = members.find(m => m.id === id);
      if (!member) return;
      await updateWorkingGroupMember(programId, id, { ...member, role: newRole as any });
      fetchData();
    } catch {
      message.error('Помилка оновлення');
    }
  };

  const handleRemove = async (id: number) => {
    await removeWorkingGroupMember(programId, id);
    message.success('Видалено');
    fetchData();
  };

  // Exclude already added teachers
  const existingTeacherIds = new Set(members.map(m => m.teacherId));
  const availableTeachers = teachers.filter(t => !existingTeacherIds.has(t.id));

  const columns: ColumnsType<ProgramWorkingGroup> = [
    { title: 'Викладач', dataIndex: 'teacherName', key: 'teacherName' },
    {
      title: 'Роль', dataIndex: 'role', key: 'role', width: 140,
      render: (role, record) => (
        <Select
          value={role}
          size="small"
          onChange={(v) => handleRoleChange(record.id!, v)}
          style={{ width: 120 }}
          options={Object.entries(WORKING_GROUP_ROLE_LABELS).map(([value, label]) => ({ value, label }))}
        />
      ),
    },
    {
      title: 'Наказ', key: 'order', width: 200,
      render: (_, r) => r.orderNumber
        ? `${r.orderNumber}${r.orderDate ? ` від ${formatDate(r.orderDate)}` : ''}`
        : '—',
    },
    {
      title: '', key: 'actions', width: 50,
      render: (_, record) => (
        <Popconfirm title="Видалити з робочої групи?" onConfirm={() => handleRemove(record.id!)} okText="Так" cancelText="Ні">
          <Button type="text" size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card size="small" title="Робоча група ОПП" style={{ marginTop: 16 }}
      extra={<Button size="small" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true); }}>Додати</Button>}
    >
      <Table
        dataSource={members}
        columns={columns}
        rowKey="id"
        size="small"
        loading={loading}
        pagination={false}
        locale={{ emptyText: 'Робочу групу не сформовано' }}
      />

      <Modal
        title="Додати до робочої групи"
        open={modalOpen}
        onOk={handleAdd}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        okText="Додати"
        cancelText="Скасувати"
      >
        <Form form={form} layout="vertical" initialValues={{ role: 'MEMBER' }}>
          <Form.Item name="teacherId" label="Викладач" rules={[{ required: true, message: 'Оберіть викладача' }]}>
            <Select
              showSearch
              placeholder="Пошук викладача..."
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
              options={availableTeachers.map(t => ({
                value: t.id,
                label: `${t.lastName} ${t.firstName} ${t.patronymic || ''}`.trim(),
              }))}
            />
          </Form.Item>
          <Form.Item name="role" label="Роль" rules={[{ required: true }]}>
            <Select options={Object.entries(WORKING_GROUP_ROLE_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Space size="middle">
            <Form.Item name="orderNumber" label="№ наказу">
              <Input style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="orderDate" label="Дата наказу">
              <DatePicker format="DD.MM.YYYY" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
};

export default ProgramWorkingGroupSection;
