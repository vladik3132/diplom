import React, { useEffect, useState, useRef } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, DatePicker, Space, Card, Popconfirm, message, Tag, Descriptions, Dropdown, Drawer, Typography, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { formatDate, parseDate } from '@/utils/dateUtils';
import { PlusOutlined, EditOutlined, DeleteOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  Publication,
  PublicationType,
  ArticleCategory,
  MethodicalSubtype,
  ApprobationSubtype,
  PUBLICATION_TYPE_LABELS,
  ARTICLE_CATEGORY_LABELS,
  PUBLICATION_STATUS_LABELS,
  PP_TYPE_LABELS,
  METHODICAL_SUBTYPE_LABELS,
  APPROBATION_SUBTYPE_LABELS,
} from '@/types/publication';
import { getPublications, createPublication, updatePublication, deletePublication, updatePublicationStatus, classifyPublication, verifyScopus, reclassifySubtypes } from '@/api/publicationApi';
import type { PublicationStatus } from '@/types/publication';
import { useAuth } from '@/auth/AuthContext';
import FileAttachmentList from '@/components/FileAttachmentList';
import type { FileAttachmentListRef } from '@/components/FileAttachmentList';

interface Props {
  teacherId?: number;
}

const PublicationsPage: React.FC<Props> = ({ teacherId: teacherIdProp }) => {
  const { user, isAdmin, isHead } = useAuth();
  const teacherId = teacherIdProp ?? user?.teacherId ?? undefined;
  const [publications, setPublications] = useState<Publication[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<Publication | null>(null);
  const [form] = Form.useForm();
  const fileListRef = useRef<FileAttachmentListRef>(null);
  const [selectedType, setSelectedType] = useState<PublicationType | null>(null);

  // Sort priority: SCOPUS > WOS > CATEGORY_A > CATEGORY_B > rest; within same category: by year desc
  const categoryPriority: Record<string, number> = {
    SCOPUS: 0, WOS: 1, CATEGORY_A: 2, CATEGORY_B: 3,
  };

  /**
   * Повертає ms-timestamp для коректного числового порівняння дат публікацій.
   * Якщо publicationDate є — використовує його (ISO YYYY-MM-DD).
   * Інакше fallback на year (1 січня року).
   * Якщо нічого немає — 0 (записи без дат йдуть в кінець descend-сорту).
   */
  const effectiveTimestamp = (p: Publication): number => {
    if (p.publicationDate) {
      const t = Date.parse(p.publicationDate);
      if (!isNaN(t)) return t;
    }
    if (p.year) {
      const t = Date.parse(`${p.year}-01-01`);
      if (!isNaN(t)) return t;
    }
    return 0;
  };

  const sortPublications = (pubs: Publication[]): Publication[] => {
    return [...pubs].sort((a, b) => {
      const pa = a.articleCategory ? (categoryPriority[a.articleCategory] ?? 10) : 10;
      const pb = b.articleCategory ? (categoryPriority[b.articleCategory] ?? 10) : 10;
      if (pa !== pb) return pa - pb;
      // Newer first (descending timestamp).
      return effectiveTimestamp(b) - effectiveTimestamp(a);
    });
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await getPublications(teacherId);
      setPublications(sortPublications(data));
      if (selectedRecord) {
        const updated = data.find((d: Publication) => d.id === selectedRecord.id);
        if (updated) setSelectedRecord(updated);
        else { setSelectedRecord(null); setDrawerOpen(false); }
      }
    } catch {
      message.error('Помилка завантаження публікацій');
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
      const payload: Partial<Publication> = {
        ...values,
        teacher: teacherId ? { id: teacherId, lastName: '', firstName: '' } : undefined,
        articleCategory: values.type === 'ARTICLE' ? values.articleCategory : undefined,
        // dayjs → ISO YYYY-MM-DD
        publicationDate: values.publicationDate
          ? (values.publicationDate as dayjs.Dayjs).format('YYYY-MM-DD')
          : undefined,
        // Якщо введено тільки дату, year автоматично; інакше беремо введений year
        year: values.publicationDate
          ? (values.publicationDate as dayjs.Dayjs).year()
          : values.year,
      };
      if (editingId) {
        await updatePublication(editingId, payload);
        message.success('Публікацію оновлено');
      } else {
        const created = await createPublication(payload);
        const pending = fileListRef.current?.getPendingFiles() || [];
        if (pending.length > 0) {
          await fileListRef.current?.uploadPendingFiles('PUBLICATION', created.id, teacherId);
        }
        message.success('Публікацію додано');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingId(null);
      setSelectedType(null);
      loadData();
    } catch {
      // validation error
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (record: Publication) => {
    setEditingId(record.id);
    setSelectedType(record.type);
    form.setFieldsValue({
      ...record,
      // parseDate підтримує і ISO (YYYY-MM-DD з backend), і DD.MM.YYYY (якщо поле вже відформатоване).
      publicationDate: parseDate(record.publicationDate),
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    await deletePublication(id);
    message.success('Публікацію видалено');
    loadData();
  };

  const typeColor: Record<PublicationType, string> = {
    ARTICLE: 'blue',
    PATENT: 'gold',
    DECLARATIVE_PATENT: 'gold',
    COPYRIGHT: 'lime',
    TEXTBOOK: 'cyan',
    STUDY_GUIDE: 'cyan',
    METHODICAL: 'geekblue',
    MONOGRAPH: 'magenta',
    APPROBATION: 'orange',
    POPULAR_SCIENTIFIC: 'volcano',
    OTHER: 'default',
  };

  const categoryColor: Record<ArticleCategory, string> = {
    SCOPUS: 'blue',
    WOS: 'purple',
    CATEGORY_A: 'green',
    CATEGORY_B: 'cyan',
  };

  const statusColor: Record<string, string> = {
    NOT_VALIDATED: 'default',
    AI_VALIDATED: 'processing',
    NEEDS_ATTENTION: 'warning',
    HEAD_VALIDATED: 'success',
    OUTDATED: 'error',
  };

  // Поля для Drawer
  const detailFields: { key: keyof Publication; label: string }[] = [
    { key: 'type', label: 'Тип' },
    { key: 'articleCategory', label: 'Категорія' },
    { key: 'journalName', label: 'Журнал/Видання' },
    { key: 'publisher', label: 'Видавництво' },
    { key: 'publicationDate', label: 'Дата публікації' },
    { key: 'volume', label: 'Том/Випуск' },
    { key: 'pages', label: 'Сторінки' },
    { key: 'totalPages', label: 'Загалом сторінок' },
    { key: 'city', label: 'Місто' },
    { key: 'authors', label: 'Автори' },
    { key: 'doi', label: 'DOI' },
    { key: 'isbn', label: 'ISBN' },
    { key: 'issn', label: 'ISSN' },
    { key: 'url', label: 'URL' },
    { key: 'conferenceInfo', label: 'Конференція' },
    { key: 'authorSheetCount', label: 'Авторських аркушів' },
    { key: 'sourceSection', label: 'Пункт' },
  ];

  const handleClassifyType = async (id: number, newType: string) => {
    try {
      await classifyPublication(id, { type: newType });
      message.success(`Тип змінено: ${PUBLICATION_TYPE_LABELS[newType as PublicationType]}`);
      loadData();
    } catch {
      message.error('Помилка зміни типу');
    }
  };

  const handleClassifyCategory = async (id: number, newCat: string) => {
    try {
      await classifyPublication(id, { articleCategory: newCat });
      message.success(newCat ? `Категорія: ${ARTICLE_CATEGORY_LABELS[newCat as ArticleCategory]}` : 'Категорію знято');
      loadData();
    } catch {
      message.error('Помилка зміни категорії');
    }
  };

  const handleClassifyMethodicalSubtype = async (id: number, newSub: string) => {
    try {
      await classifyPublication(id, { methodicalSubtype: newSub });
      message.success(newSub ? `Підтип: ${METHODICAL_SUBTYPE_LABELS[newSub as MethodicalSubtype]}` : 'Підтип знято');
      loadData();
    } catch {
      message.error('Помилка зміни підтипу');
    }
  };

  const handleClassifyApprobationSubtype = async (id: number, newSub: string) => {
    try {
      await classifyPublication(id, { approbationSubtype: newSub });
      message.success(newSub ? `Рівень: ${APPROBATION_SUBTYPE_LABELS[newSub as ApprobationSubtype]}` : 'Рівень знято');
      loadData();
    } catch {
      message.error('Помилка зміни рівня видання');
    }
  };

  const columns: ColumnsType<Publication> = [
    { title: 'Назва', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: 'Тип', dataIndex: 'type', key: 'type', width: 170,
      render: (type: PublicationType, record: Publication) => {
        const typeTag = <Tag color={typeColor[type]}>{PUBLICATION_TYPE_LABELS[type] || type}</Tag>;
        // Категорія (Scopus/WoS/A/B) — тільки для наукових статей
        const catTag = type === 'ARTICLE' && record.articleCategory ? (
          <Tag color={categoryColor[record.articleCategory]}>
            {ARTICLE_CATEGORY_LABELS[record.articleCategory]}
          </Tag>
        ) : null;
        // Підтип — для методичних або рівень видання для апробацій
        const subtypeTag = record.methodicalSubtype
          ? <Tag color="cyan">{METHODICAL_SUBTYPE_LABELS[record.methodicalSubtype] || record.methodicalSubtype}</Tag>
          : record.approbationSubtype
            ? <Tag color="geekblue">{APPROBATION_SUBTYPE_LABELS[record.approbationSubtype] || record.approbationSubtype}</Tag>
            : null;

        if (!isAdmin) {
          return <Space direction="vertical" size={2}>{typeTag}{catTag}{subtypeTag}</Space>;
        }

        // Admin: inline dropdowns
        const typeItems = Object.entries(PUBLICATION_TYPE_LABELS).map(([value, label]) => ({
          key: value,
          label: <Tag color={typeColor[value as PublicationType]}>{label}</Tag>,
          disabled: value === type,
        }));

        const categoryItems = [
          { key: '', label: <Tag>— без категорії —</Tag>, disabled: !record.articleCategory },
          ...Object.entries(ARTICLE_CATEGORY_LABELS).map(([value, label]) => ({
            key: value,
            label: <Tag color={categoryColor[value as ArticleCategory]}>{label}</Tag>,
            disabled: value === record.articleCategory,
          })),
        ];

        return (
          <Space direction="vertical" size={2}>
            <Dropdown
              menu={{ items: typeItems, onClick: ({ key }) => handleClassifyType(record.id, key) }}
              trigger={['click']}
            >
              <Tooltip title="Змінити тип">
                <span style={{ cursor: 'pointer' }}>{typeTag}</span>
              </Tooltip>
            </Dropdown>
            {type === 'ARTICLE' && (
              <Dropdown
                menu={{ items: categoryItems, onClick: ({ key }) => handleClassifyCategory(record.id, key) }}
                trigger={['click']}
              >
                <Tooltip title="Змінити категорію">
                  <span style={{ cursor: 'pointer' }}>
                    {catTag || <Tag style={{ borderStyle: 'dashed', cursor: 'pointer' }}>+ категорія</Tag>}
                  </span>
                </Tooltip>
              </Dropdown>
            )}
            {type === 'METHODICAL' && (
              <Dropdown
                menu={{
                  items: [
                    { key: '', label: <Tag>— без підтипу —</Tag>, disabled: !record.methodicalSubtype },
                    ...Object.entries(METHODICAL_SUBTYPE_LABELS).map(([value, label]) => ({
                      key: value,
                      label: <Tag color="cyan">{label}</Tag>,
                      disabled: value === record.methodicalSubtype,
                    })),
                  ],
                  onClick: ({ key }) => handleClassifyMethodicalSubtype(record.id, key),
                }}
                trigger={['click']}
              >
                <Tooltip title="Змінити підтип">
                  <span style={{ cursor: 'pointer' }}>
                    {record.methodicalSubtype
                      ? <Tag color="cyan">{METHODICAL_SUBTYPE_LABELS[record.methodicalSubtype] || record.methodicalSubtype}</Tag>
                      : <Tag style={{ borderStyle: 'dashed', cursor: 'pointer' }}>+ підтип</Tag>}
                  </span>
                </Tooltip>
              </Dropdown>
            )}
            {(type === 'APPROBATION' || type === 'POPULAR_SCIENTIFIC') && (
              <Dropdown
                menu={{
                  items: [
                    { key: '', label: <Tag>— без рівня —</Tag>, disabled: !record.approbationSubtype },
                    ...Object.entries(APPROBATION_SUBTYPE_LABELS).map(([value, label]) => ({
                      key: value,
                      label: <Tag color="geekblue">{label}</Tag>,
                      disabled: value === record.approbationSubtype,
                    })),
                  ],
                  onClick: ({ key }) => handleClassifyApprobationSubtype(record.id, key),
                }}
                trigger={['click']}
              >
                <Tooltip title="Змінити рівень видання">
                  <span style={{ cursor: 'pointer' }}>
                    {record.approbationSubtype
                      ? <Tag color="geekblue">{APPROBATION_SUBTYPE_LABELS[record.approbationSubtype] || record.approbationSubtype}</Tag>
                      : <Tag style={{ borderStyle: 'dashed', cursor: 'pointer' }}>+ рівень</Tag>}
                  </span>
                </Tooltip>
              </Dropdown>
            )}
          </Space>
        );
      },
      filters: Object.entries(PUBLICATION_TYPE_LABELS).map(([value, text]) => ({ text, value })),
      onFilter: (value, record) => record.type === value,
    },
    { title: 'Журнал/Видання', dataIndex: 'journalName', key: 'journalName', ellipsis: true },
    { title: 'Видавництво', dataIndex: 'publisher', key: 'publisher', width: 160, ellipsis: true },
    {
      title: 'Дата',
      key: 'publicationDate',
      width: 110,
      render: (_: unknown, r: Publication) =>
        r.publicationDate ? formatDate(r.publicationDate) : (r.year ? String(r.year) : '—'),
      // Numeric-sorter через ms timestamp — працює правильно незалежно від zero-padding
      // та формату дати. Записи без дати/року отримують 0 і йдуть в кінець descend-сорту.
      sorter: (a: Publication, b: Publication) => {
        const ta = effectiveTimestamp(a);
        const tb = effectiveTimestamp(b);
        return ta - tb;
      },
      sortDirections: ['descend', 'ascend'],
    },
    {
      title: 'Статус', dataIndex: 'status', key: 'status', width: 180,
      render: (status: string, record: Publication) => {
        const tag = status ? (
          <Tag color={statusColor[status]}>
            {PUBLICATION_STATUS_LABELS[status as PublicationStatus] || status}
          </Tag>
        ) : <Tag>—</Tag>;

        const relevanceIcon = record.fieldRelevant === false ? (
          <Tooltip title="Не відповідає напряму кафедри">
            <WarningOutlined style={{ color: '#faad14', marginLeft: 4 }} />
          </Tooltip>
        ) : record.fieldRelevant === true ? (
          <Tooltip title="Відповідає напряму кафедри">
            <CheckCircleOutlined style={{ color: '#52c41a', marginLeft: 4 }} />
          </Tooltip>
        ) : null;

        // HEAD/ADMIN can change status
        if (!isAdmin && !isHead) return <>{tag}{relevanceIcon}</>;

        const statusItems = Object.entries(PUBLICATION_STATUS_LABELS).map(([value, label]) => ({
          key: value,
          label: <Tag color={statusColor[value]}>{label}</Tag>,
          disabled: value === status,
        }));

        return (
          <>
            <Dropdown
              menu={{
                items: statusItems,
                onClick: async ({ key }) => {
                  try {
                    await updatePublicationStatus(record.id, key);
                    message.success(`Статус змінено: ${PUBLICATION_STATUS_LABELS[key as PublicationStatus]}`);
                    loadData();
                  } catch {
                    message.error('Помилка зміни статусу');
                  }
                },
              }}
              trigger={['click']}
            >
              <span style={{ cursor: 'pointer' }}>{tag}</span>
            </Dropdown>
            {relevanceIcon}
          </>
        );
      },
    },
    {
      title: 'ПП', dataIndex: 'ppType', key: 'ppType', width: 70,
      render: (pp: string) => pp ? (
        <Tag>{PP_TYPE_LABELS[pp as keyof typeof PP_TYPE_LABELS] || pp}</Tag>
      ) : null,
    },
    { title: 'DOI', dataIndex: 'doi', key: 'doi', width: 140, ellipsis: true },
    { title: 'Автори', dataIndex: 'authors', key: 'authors', ellipsis: true },
    {
      title: 'Дії', key: 'actions', width: 100,
      render: (_, record) => (
        <Space>
          <Button icon={<EditOutlined />} size="small" onClick={() => handleEdit(record)} />
          <Popconfirm title="Видалити публікацію?" onConfirm={() => handleDelete(record.id)}>
            <Button icon={<DeleteOutlined />} size="small" danger />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Публікації"
      extra={
        <Space>
          {isAdmin && (
            <Button onClick={async () => {
              try {
                const res = await reclassifySubtypes();
                message.success(`Перекласифіковано підтипів: ${res.reclassified}`);
                loadData();
              } catch { message.error('Помилка перекласифікації'); }
            }}>
              Перекласифікувати підтипи
            </Button>
          )}
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); setSelectedType(null); form.resetFields(); setModalOpen(true); }}>
            Додати публікацію
          </Button>
        </Space>
      }
    >
      <Table
        columns={columns}
        dataSource={publications}
        rowKey="id"
        loading={loading}
        onRow={(record) => ({
          onClick: (e) => {
            if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm, .ant-dropdown, .ant-dropdown-trigger')) return;
            setSelectedRecord(record);
            setDrawerOpen(true);
          },
          style: { cursor: 'pointer' },
        })}
      />

      <Modal
        title={editingId ? 'Редагувати публікацію' : 'Нова публікація'}
        open={modalOpen}
        onOk={handleSave}
        confirmLoading={saving}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditingId(null); setSelectedType(null); fileListRef.current?.clearPending(); }}
        width={750}
        okText="Зберегти"
        cancelText="Скасувати"
      >
        <Form form={form} layout="vertical" style={{ maxHeight: '60vh', overflowY: 'auto', paddingRight: 8 }}>
          <Form.Item label="Прикріплені файли">
            <FileAttachmentList
              ref={fileListRef}
              entityType="PUBLICATION"
              entityId={editingId}
              teacherId={teacherId}
            />
          </Form.Item>
          <Form.Item name="title" label="Назва" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>

          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="type" label="Тип" rules={[{ required: true, message: 'Оберіть тип' }]} style={{ flex: 1 }}>
              <Select
                options={Object.entries(PUBLICATION_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
                onChange={(val: PublicationType) => {
                  setSelectedType(val);
                  if (val !== 'ARTICLE') {
                    form.setFieldValue('articleCategory', undefined);
                  }
                  if (val !== 'METHODICAL') {
                    form.setFieldValue('methodicalSubtype', undefined);
                  }
                  if (val !== 'APPROBATION' && val !== 'POPULAR_SCIENTIFIC') {
                    form.setFieldValue('approbationSubtype', undefined);
                  }
                }}
              />
            </Form.Item>
            {selectedType === 'ARTICLE' && (
              <Form.Item name="articleCategory" label="Категорія" style={{ width: 200 }}>
                <Select
                  allowClear
                  placeholder="Оберіть категорію"
                  options={Object.entries(ARTICLE_CATEGORY_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            )}
          </div>
          {selectedType === 'METHODICAL' && (
            <Form.Item name="methodicalSubtype" label="Підтип методичної праці">
              <Select allowClear placeholder="Оберіть підтип"
                options={Object.entries(METHODICAL_SUBTYPE_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          )}
          {(selectedType === 'APPROBATION' || selectedType === 'POPULAR_SCIENTIFIC') && (
            <Form.Item name="approbationSubtype" label="Рівень видання">
              <Select allowClear placeholder="Оберіть рівень видання"
                options={Object.entries(APPROBATION_SUBTYPE_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          )}

          <Form.Item name="journalName" label="Журнал/Видання">
            <Input />
          </Form.Item>

          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item
              name="publicationDate"
              label="Дата публікації"
              style={{ width: 180 }}
              tooltip="Якщо відомий лише рік — виберіть 1 січня цього року"
            >
              <DatePicker format="DD.MM.YYYY" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="volume" label="Том" style={{ width: 120 }}>
              <Input />
            </Form.Item>
            <Form.Item name="pages" label="Сторінки" style={{ width: 120 }}>
              <Input />
            </Form.Item>
            <Form.Item name="totalPages" label="Загалом стор." style={{ width: 120 }}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="publisher" label="Видавництво" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="city" label="Місто" style={{ width: 180 }}>
              <Input />
            </Form.Item>
          </div>

          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="doi" label="DOI" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="isbn" label="ISBN" style={{ width: 180 }}>
              <Input />
            </Form.Item>
            <Form.Item name="issn" label="ISSN" style={{ width: 180 }}>
              <Input />
            </Form.Item>
          </div>

          <Form.Item name="url" label="Зовнішнє посилання" tooltip="Посилання на публікацію (DOI, сайт журналу, Scopus, WoS тощо)">
            <Input placeholder="https://doi.org/..., https://journals...." />
          </Form.Item>

          <Form.Item name="authors" label="Автори">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Form.Item name="conferenceInfo" label="Інформація про конференцію">
            <Input.TextArea rows={2} placeholder="Назва конференції, дата, місце" />
          </Form.Item>

          <Form.Item name="authorSheetCount" label="Авторських аркушів">
            <InputNumber min={0} step={0.1} style={{ width: 160 }} />
          </Form.Item>

          <Form.Item name="dstuCitation"
            label={<span>Бібліографічний опис (ДСТУ 8302:2015) <Tag color="blue" style={{ marginLeft: 4, fontSize: 11 }}>авто</Tag></span>}
            tooltip="Генерується автоматично з полів вище. Можете відредагувати вручну за потреби."
          >
            <Input.TextArea rows={2} placeholder="Генерується автоматично після збереження" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={selectedRecord?.title || 'Деталі публікації'}
        placement="right"
        width={480}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setSelectedRecord(null); }}
      >
        {selectedRecord && (
          <>
            {selectedRecord.dstuCitation && (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 4 }}>
                <span style={{ fontSize: 11, color: '#888', marginRight: 6 }}>ДСТУ 8302:2015</span>
                <Tag color="blue" style={{ fontSize: 10 }}>авто</Tag>
                <div style={{ marginTop: 4, fontStyle: 'italic' }}>{selectedRecord.dstuCitation}</div>
              </div>
            )}

            {selectedRecord.articleCategory && selectedRecord.fieldRelevant !== undefined && selectedRecord.fieldRelevant !== null && (
              <div style={{
                marginBottom: 16, padding: '8px 12px',
                background: selectedRecord.fieldRelevant ? '#f6ffed' : '#fffbe6',
                border: `1px solid ${selectedRecord.fieldRelevant ? '#b7eb8f' : '#ffe58f'}`,
                borderRadius: 4
              }}>
                {selectedRecord.fieldRelevant ? (
                  <><CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6 }} />Відповідає напряму діяльності кафедри</>
                ) : (
                  <><WarningOutlined style={{ color: '#faad14', marginRight: 6 }} />Не відповідає напряму діяльності кафедри — потребує уваги</>
                )}
              </div>
            )}

            <Descriptions column={1} size="small" bordered>
              {detailFields.map(f => {
                let val = selectedRecord[f.key];
                if (val === undefined || val === null || val === '') return null;
                let displayVal: React.ReactNode = String(val);
                if (f.key === 'type') displayVal = PUBLICATION_TYPE_LABELS[val as PublicationType] || String(val);
                else if (f.key === 'articleCategory') displayVal = ARTICLE_CATEGORY_LABELS[val as ArticleCategory] || String(val);
                else if (f.key === 'publicationDate') displayVal = formatDate(val as string);
                else if (f.key === 'url') displayVal = (
                  <a href={val as string} target="_blank" rel="noopener noreferrer">
                    {(val as string).length > 50 ? (val as string).substring(0, 50) + '...' : val as string}
                  </a>
                );
                return (
                  <Descriptions.Item key={f.key} label={f.label}>
                    {displayVal}
                  </Descriptions.Item>
                );
              })}
            </Descriptions>

            <div style={{ marginTop: 16 }}>
              <Typography.Text strong>Прикріплені файли</Typography.Text>
              <FileAttachmentList
                entityType="PUBLICATION"
                entityId={selectedRecord.id}
                teacherId={teacherId}
                editable={false}
              />
            </div>

            <div style={{ marginTop: 16 }}>
              <Button
                size="small"
                type={selectedRecord.articleCategory === 'SCOPUS' ? 'default' : 'primary'}
                onClick={async () => {
                  try {
                    const result = await verifyScopus(selectedRecord.id);
                    if (result.error) {
                      message.warning(result.error);
                    } else if (result.found && result.authorConfirmed) {
                      message.success(
                        `Scopus: підтверджено (${result.searchMethod}). ` +
                        (result.matchedAuthorName ? `Автор: ${result.matchedAuthorName}` : '')
                      );
                      loadData(); // refresh to show updated category
                    } else if (result.found && !result.authorConfirmed) {
                      message.warning('Scopus: стаття знайдена, але авторство НЕ підтверджено');
                    } else {
                      message.info('Scopus: стаття не знайдена в Scopus');
                    }
                  } catch {
                    message.error('Помилка перевірки Scopus');
                  }
                }}
              >
                {selectedRecord.articleCategory === 'SCOPUS' ? '✓ Scopus підтверджено' : 'Перевірити в Scopus'}
              </Button>
            </div>
          </>
        )}
      </Drawer>
    </Card>
  );
};

export default PublicationsPage;
