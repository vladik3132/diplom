import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Button, Input, Typography, Space, Modal, Form, Select, Switch, message, DatePicker, Popconfirm, Tooltip, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, DeleteOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { Teacher, Department } from '../../types/teacher';
import { ComplianceReport } from '../../types/achievement';
import { getTeachersPaged, createTeacher, deleteTeacher } from '../../api/teacherApi';
import { getComplianceAll } from '../../api/achievementApi';
import { getDepartments } from '../../api/departmentApi';
import ComplianceBadge from '../../components/ComplianceBadge';

// POSITION_OPTIONS видалено — посада тепер призначається через staff_positions
// (розділ "Штат"). Викладач створюється без посади.

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

const { Title } = Typography;

const DEFAULT_PAGE_SIZE = 20;

const TeacherListPage: React.FC = () => {
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(1); // AntD is 1-based
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | undefined>();
  const [complianceMap, setComplianceMap] = useState<Record<number, ComplianceReport>>({});

  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const navigate = useNavigate();

  // Debounce search input — чекаємо 300ms після останнього введення
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchText.trim()), 300);
    return () => clearTimeout(t);
  }, [searchText]);

  // Коли змінюється фільтр/пошук — повертаємося на 1-у сторінку
  useEffect(() => {
    setCurrentPage(1);
  }, [debouncedSearch, selectedDepartmentId]);

  // ── Завантаження викладачів (server-side paging+search) ──
  const fetchTeachers = async () => {
    setLoading(true);
    try {
      const data = await getTeachersPaged({
        page: currentPage - 1, // backend — 0-based
        size: pageSize,
        search: debouncedSearch || undefined,
        departmentId: selectedDepartmentId,
      });
      // Бекенд може повернути або Page<T>, або List<T> (legacy роль TEACHER → один запис)
      if (Array.isArray(data as unknown)) {
        const list = (data as unknown) as Teacher[];
        setTeachers(list);
        setTotalElements(list.length);
      } else {
        setTeachers(data.content);
        setTotalElements(data.totalElements);
      }
    } catch {
      setTeachers([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeachers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, pageSize, debouncedSearch, selectedDepartmentId]);

  // ── Завантаження compliance-map (окремо, з кеш-бекенду — 1 SQL) ──
  // Перечитуємо тільки коли змінюється департамент-фільтр; свіжу інформацію
  // про п.38 отримуємо з cache-service на бекенді.
  const complianceLoadKey = useRef<string>('');
  useEffect(() => {
    const key = `${selectedDepartmentId ?? 'all'}`;
    if (complianceLoadKey.current === key) return;
    complianceLoadKey.current = key;
    getComplianceAll(selectedDepartmentId)
      .then((list) => {
        const map: Record<number, ComplianceReport> = {};
        list.forEach((c) => { map[c.teacherId] = c; });
        setComplianceMap(map);
      })
      .catch(() => setComplianceMap({}));
  }, [selectedDepartmentId]);

  // Departments — один раз
  useEffect(() => {
    getDepartments().then(setDepartments).catch(() => {});
  }, []);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload = {
        ...values,
        experienceStartDate: values.experienceStartDate?.format('DD.MM.YYYY') || null,
        dateOfBirth: values.dateOfBirth?.format('DD.MM.YYYY') || null,
      };
      await createTeacher(payload);
      message.success('Викладача додано');
      setModalOpen(false);
      form.resetFields();
      // Перечитуємо поточну сторінку + оновлюємо compliance-мапу
      fetchTeachers();
      getComplianceAll(selectedDepartmentId)
        .then((list) => {
          const map: Record<number, ComplianceReport> = {};
          list.forEach((c) => { map[c.teacherId] = c; });
          setComplianceMap(map);
        })
        .catch(() => {});
    } catch (error: unknown) {
      const e = error as { response?: { data?: { message?: string } } };
      if (e?.response?.data?.message) {
        message.error(e.response.data.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteTeacher(id);
      message.success('Викладача видалено');
      // Якщо видалили останній елемент на сторінці — перекидаємо на попередню
      if (teachers.length === 1 && currentPage > 1) {
        setCurrentPage(currentPage - 1);
      } else {
        fetchTeachers();
      }
    } catch {
      message.error('Помилка видалення');
    }
  };

  const handleTableChange = (paginationConf: TablePaginationConfig) => {
    if (paginationConf.current && paginationConf.current !== currentPage) {
      setCurrentPage(paginationConf.current);
    }
    if (paginationConf.pageSize && paginationConf.pageSize !== pageSize) {
      setPageSize(paginationConf.pageSize);
      setCurrentPage(1);
    }
  };

  const columns: ColumnsType<Teacher> = [
    {
      title: 'ПІБ',
      key: 'fullName',
      render: (_, record) =>
        `${record.lastName} ${record.firstName} ${record.patronymic || ''}`.trim(),
      // Сортування тепер server-side (by lastName asc) — клієнтський sorter видалений
    },
    {
      title: 'Посада',
      dataIndex: 'effectivePosition',
      key: 'effectivePosition',
      render: (pos: string | undefined) => pos || '—',
    },
    {
      title: 'Ступінь',
      key: 'academicDegree',
      render: (_: unknown, record: Teacher) => {
        if (!record.academicDegree) return '—';
        const cnt = record.academicDegreesCount ?? 0;
        return cnt > 1 ? `${record.academicDegree} (+${cnt - 1})` : record.academicDegree;
      },
    },
    {
      title: 'Звання',
      key: 'academicTitle',
      render: (_: unknown, record: Teacher) => {
        if (!record.academicTitle) return '—';
        const cnt = record.academicTitlesCount ?? 0;
        return cnt > 1 ? `${record.academicTitle} (+${cnt - 1})` : record.academicTitle;
      },
    },
    {
      title: 'Кафедра',
      key: 'department',
      render: (_: unknown, record: Teacher) => {
        return record.departmentNumber || record.departmentName || '—';
      },
    },
    {
      title: 'Статус п.38',
      key: 'complianceStatus',
      width: 160,
      render: (_, record) => {
        const c = complianceMap[record.id];
        return c ? <ComplianceBadge status={c.status} /> : '—';
      },
      // client-side фільтрація по статусу п.38 в рамках поточної сторінки
      filters: [
        { text: 'Відповідає', value: 'COMPLIANT' },
        { text: 'Попередження', value: 'WARNING' },
        { text: 'Не відповідає', value: 'NON_COMPLIANT' },
        { text: 'Стаж НПП менше 3 років', value: 'EXEMPT' },
      ],
      onFilter: (value, record) => complianceMap[record.id]?.status === value,
    },
    {
      title: 'Кваліф.',
      key: 'qualificationMatch',
      width: 80,
      align: 'center' as const,
      render: (_, record) => {
        const c = complianceMap[record.id];
        if (!c) return '—';
        const ok = c.qualificationMatchesDepartment;
        const details = [
          `Диплом: ${c.diplomaMatchesDepartment ? '✓' : '✗'}`,
          `Ступінь: ${c.degreeMatchesDepartment ? '✓' : '✗'}`,
        ].join('\n');
        return (
          <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{details}</span>}>
            {ok
              ? <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
              : <CloseCircleOutlined style={{ color: '#f5222d', fontSize: 16 }} />
            }
          </Tooltip>
        );
      },
      filters: [
        { text: 'Відповідає', value: true },
        { text: 'Не відповідає', value: false },
      ],
      onFilter: (value, record) => {
        const c = complianceMap[record.id];
        return c ? c.qualificationMatchesDepartment === value : false;
      },
    },
    {
      title: 'Публ. каф.',
      key: 'relevantPubs',
      width: 100,
      align: 'center' as const,
      render: (_, record) => {
        const c = complianceMap[record.id];
        if (!c) return '—';
        const count = c.relevantPublicationsCount;
        return (
          <Tooltip title={`Фахових статей за напрямком кафедри: ${count} (потрібно ≥5)`}>
            <Tag color={count >= 5 ? 'success' : count > 0 ? 'warning' : 'error'}
              style={{ minWidth: 36, textAlign: 'center' }}>
              {count}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: 'Дії',
      key: 'actions',
      width: 70,
      render: (_, record) => (
        <Popconfirm
          title="Видалити викладача?"
          description="Усі дані та файли будуть видалені."
          onConfirm={(e) => { e?.stopPropagation(); handleDelete(record.id); }}
          onCancel={(e) => e?.stopPropagation()}
          okText="Видалити"
          cancelText="Скасувати"
          okButtonProps={{ danger: true }}
        >
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={(e) => e.stopPropagation()}
          />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          Викладачі
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Додати викладача
        </Button>
      </div>

      <Space style={{ marginBottom: 16, width: '100%' }} wrap>
        <Input
          placeholder="Пошук за прізвищем..."
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          style={{ width: 320 }}
          allowClear
        />
        <Select
          placeholder="Усі кафедри"
          value={selectedDepartmentId}
          onChange={setSelectedDepartmentId}
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ width: 320 }}
          options={departments.map((d) => ({
            value: d.id,
            label: d.number ? `${d.number} — ${d.name}` : d.name,
          }))}
        />
      </Space>

      <Table
        columns={columns}
        dataSource={teachers}
        rowKey="id"
        loading={loading}
        locale={{ emptyText: 'Немає даних про викладачів' }}
        onChange={handleTableChange}
        onRow={(record) => ({
          onClick: () => navigate(`/teachers/${record.id}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: currentPage,
          pageSize,
          total: totalElements,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total) => `Всього: ${total}`,
        }}
      />

      <Modal
        title="Додати викладача"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        confirmLoading={saving}
        okText="Зберегти"
        cancelText="Скасувати"
        width={640}
      >
        <Form form={form} layout="vertical" initialValues={{ employmentType: 'MAIN', combatVeteranStatus: false }}>
          <Form.Item name="lastName" label="Прізвище" rules={[{ required: true, message: "Обов'язкове поле" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="firstName" label="Ім'я" rules={[{ required: true, message: "Обов'язкове поле" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="patronymic" label="По батькові">
            <Input />
          </Form.Item>
          <Space size="large" style={{ width: '100%' }}>
            <Form.Item name="dateOfBirth" label="Дата народження">
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="experienceStartDate" label="Початок стажу НПП">
              <DatePicker format="DD.MM.YYYY" placeholder="Дата" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="departmentId" label="Кафедра">
            <Select allowClear showSearch placeholder="Оберіть кафедру"
              optionFilterProp="label"
              options={departments.map(d => ({
                value: d.id, label: d.number ? `${d.number} — ${d.name}` : d.name,
              }))} />
          </Form.Item>
          {/*
            Поле "Посада" видалено з форми створення викладача — посада тепер
            призначається через розділ "Штат" (staff_positions). Адмін створює
            викладача без посади, потім у Штаті лінкує його зі штатною одиницею
            або редагує bootstrap-запис.
          */}
          <Form.Item name="militaryRank" label="Військове звання">
            <Select allowClear showSearch placeholder="Оберіть звання"
              options={MILITARY_RANK_OPTIONS.map(r => ({ value: r, label: r }))} />
          </Form.Item>
          <Form.Item name="employmentType" label="Тип зайнятості" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="MAIN">Основне місце роботи</Select.Option>
              <Select.Option value="PART_TIME">Сумісництво</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="email" label="Email">
            <Input type="email" />
          </Form.Item>
          <Form.Item name="phone" label="Телефон">
            <Input />
          </Form.Item>
          <Form.Item name="orcidId" label="ORCID">
            <Input placeholder="0000-0000-0000-0000" />
          </Form.Item>
          <Form.Item name="combatVeteranStatus" label="Учасник бойових дій" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TeacherListPage;
