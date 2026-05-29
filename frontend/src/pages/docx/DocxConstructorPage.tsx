import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Card, Button, Space, Select, Input, InputNumber, Steps, Table, Tag,
  Upload, message, Popconfirm, Alert, Tooltip, Divider, Radio,
} from 'antd';
import {
  UploadOutlined, FileWordOutlined, SaveOutlined,
  DownloadOutlined, DeleteOutlined, InboxOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd';
import {
  uploadTemplate, getDataFields, getMappings, getMapping,
  createMapping, updateMapping, deleteMapping, exportDepartments,
  exportByTeachers, exportByProgram,
} from '@/api/docxExportApi';
import { getDepartments } from '@/api/departmentApi';
import { getTeachers } from '@/api/teacherApi';
import { getEducationalPrograms } from '@/api/educationalProgramApi';
import type { EducationalProgram } from '@/types/educationalProgram';
import type {
  ColumnHeader, DataField, DocxExportTemplate, ColumnMapping,
} from '@/types/docxExport';
import { DATA_FIELD_GROUP_LABELS } from '@/types/docxExport';
import type { Department, Teacher } from '@/types/teacher';

const { Dragger } = Upload;

const DocxConstructorPage: React.FC = () => {
  // ── Step control ──────────────────────────────────────────────────
  const [currentStep, setCurrentStep] = useState(0);

  // ── Step 1: Template ──────────────────────────────────────────────
  const [savedConfigs, setSavedConfigs] = useState<DocxExportTemplate[]>([]);
  const [selectedConfigId, setSelectedConfigId] = useState<number | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [tableIndex, setTableIndex] = useState(0);
  const [headerRowCount, setHeaderRowCount] = useState(1);
  const [uploading, setUploading] = useState(false);
  const [templateFileName, setTemplateFileName] = useState<string | null>(null);
  const [originalFileName, setOriginalFileName] = useState<string | null>(null);

  // ── Step 2: Mapping ───────────────────────────────────────────────
  const [columns, setColumns] = useState<ColumnHeader[]>([]);
  const [dataFields, setDataFields] = useState<DataField[]>([]);
  const [mappings, setMappings] = useState<ColumnMapping[]>([]);
  const [configName, setConfigName] = useState('');

  // ── Step 3: Export ────────────────────────────────────────────────
  const [exportMode, setExportMode] = useState<'departments' | 'teachers' | 'program'>('departments');
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedDeptIds, setSelectedDeptIds] = useState<number[]>([]);
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([]);
  const [selectedTeacherIds, setSelectedTeacherIds] = useState<number[]>([]);
  const [programs, setPrograms] = useState<EducationalProgram[]>([]);
  const [selectedProgramId, setSelectedProgramId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);

  // ── Load initial data ─────────────────────────────────────────────

  const loadConfigs = useCallback(async () => {
    try {
      const data = await getMappings();
      setSavedConfigs(data);
    } catch {
      // silent
    }
  }, []);

  const loadDataFields = useCallback(async () => {
    try {
      const data = await getDataFields();
      setDataFields(data);
    } catch {
      message.error('Не вдалось завантажити каталог полів');
    }
  }, []);

  const loadDepartments = useCallback(async () => {
    try {
      const data = await getDepartments();
      setDepartments(data);
    } catch {
      // silent
    }
  }, []);

  const loadTeachers = useCallback(async () => {
    try {
      const data = await getTeachers();
      setAllTeachers(data);
    } catch {
      // silent
    }
  }, []);

  const loadPrograms = useCallback(async () => {
    try {
      const data = await getEducationalPrograms();
      setPrograms(data);
    } catch {
      // silent
    }
  }, []);

  useEffect(() => {
    loadConfigs();
    loadDataFields();
    loadDepartments();
    loadTeachers();
    loadPrograms();
  }, [loadConfigs, loadDataFields, loadDepartments, loadTeachers, loadPrograms]);

  // ── Grouped options for Select ────────────────────────────────────

  const groupedOptions = useMemo(() => {
    const groups = new Map<string, DataField[]>();
    for (const f of dataFields) {
      const g = f.group || 'other';
      if (!groups.has(g)) groups.set(g, []);
      groups.get(g)!.push(f);
    }

    return Array.from(groups.entries()).map(([groupKey, fields]) => ({
      label: DATA_FIELD_GROUP_LABELS[groupKey] || groupKey,
      options: fields.map(f => ({
        value: f.key,
        label: f.label,
        description: f.descriptionHint,
      })),
    }));
  }, [dataFields]);

  // ── Step 1 handlers ───────────────────────────────────────────────

  const handleUpload = async () => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.error('Оберіть файл шаблону');
      return;
    }

    setUploading(true);
    try {
      const result = await uploadTemplate(file, tableIndex, headerRowCount);
      setTemplateFileName(result.templateFileName);
      setOriginalFileName(result.originalFileName);
      setColumns(result.columns);
      setMappings(result.columns.map(col => ({
        columnIndex: col.columnIndex,
        headerText: col.headerText,
        fieldKeys: [],
      })));
      setSelectedConfigId(null);
      setConfigName('');
      message.success(`Шаблон завантажено: ${result.columns.length} колонок`);
      setCurrentStep(1);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Помилка завантаження шаблону');
    } finally {
      setUploading(false);
    }
  };

  const handleLoadConfig = async (id: number) => {
    try {
      const config = await getMapping(id);
      setSelectedConfigId(id);
      setConfigName(config.name);
      setTemplateFileName(config.templateFileName);

      const parsed = JSON.parse(config.columnMappingsJson) as Array<{
        columnIndex: number;
        headerText: string;
        fieldKey?: string;
        fieldKeys?: string[];
      }>;

      // Normalize: support both old (fieldKey) and new (fieldKeys) format
      const normalized: ColumnMapping[] = parsed.map(m => ({
        columnIndex: m.columnIndex,
        headerText: m.headerText,
        fieldKeys: m.fieldKeys ?? (m.fieldKey ? [m.fieldKey] : []),
      }));

      setColumns(normalized.map(m => ({ columnIndex: m.columnIndex, headerText: m.headerText })));
      setMappings(normalized);
      setTableIndex(config.tableIndex ?? 0);
      setHeaderRowCount(config.headerRowCount ?? 1);
      setOriginalFileName(config.originalFileName || null);

      message.success(`Конфігурацію "${config.name}" завантажено`);
      setCurrentStep(2);
    } catch {
      message.error('Помилка завантаження конфігурації');
    }
  };

  // ── Step 2 handlers ───────────────────────────────────────────────

  const handleFieldKeysChange = (columnIndex: number, fieldKeys: string[]) => {
    setMappings(prev =>
      prev.map(m => m.columnIndex === columnIndex ? { ...m, fieldKeys } : m),
    );
  };

  const handleSaveMapping = async () => {
    if (!configName.trim()) {
      message.error('Введіть назву конфігурації');
      return;
    }
    if (!templateFileName) {
      message.error('Спочатку завантажте шаблон');
      return;
    }

    const mappingsJson = JSON.stringify(mappings);

    try {
      if (selectedConfigId) {
        await updateMapping(selectedConfigId, {
          name: configName,
          columnMappingsJson: mappingsJson,
          tableIndex,
          headerRowCount,
        });
        message.success('Конфігурацію оновлено');
      } else {
        const created = await createMapping({
          name: configName,
          templateFileName,
          originalFileName: originalFileName || undefined,
          columnMappingsJson: mappingsJson,
          tableIndex,
          headerRowCount,
        });
        setSelectedConfigId(created.id);
        message.success('Конфігурацію збережено');
      }
      loadConfigs();
      setCurrentStep(2);
    } catch {
      message.error('Помилка збереження конфігурації');
    }
  };

  // ── Step 3 handler ────────────────────────────────────────────────

  const handleExport = async () => {
    if (!selectedConfigId) {
      message.error('Збережіть конфігурацію маппінгу');
      return;
    }
    if (exportMode === 'departments' && selectedDeptIds.length === 0) {
      message.error('Оберіть хоча б одну кафедру');
      return;
    }
    if (exportMode === 'teachers' && selectedTeacherIds.length === 0) {
      message.error('Оберіть хоча б одного викладача');
      return;
    }
    if (exportMode === 'program' && !selectedProgramId) {
      message.error('Оберіть освітню програму');
      return;
    }

    setExporting(true);
    try {
      if (exportMode === 'teachers') {
        await exportByTeachers(selectedConfigId, selectedTeacherIds);
      } else if (exportMode === 'program') {
        await exportByProgram(selectedConfigId, selectedProgramId!);
      } else {
        await exportDepartments(selectedConfigId, selectedDeptIds);
      }
      message.success('Документ згенеровано та завантажено');
    } catch (e: any) {
      let errMsg = 'Помилка генерації документу';
      if (e?.response?.data instanceof Blob) {
        errMsg = await e.response.data.text();
      } else if (typeof e?.response?.data === 'string') {
        errMsg = e.response.data;
      } else if (e?.response?.data?.message) {
        errMsg = e.response.data.message;
      }
      message.error(errMsg);
    } finally {
      setExporting(false);
    }
  };

  const handleDeleteConfig = async (id: number) => {
    try {
      await deleteMapping(id);
      message.success('Конфігурацію видалено');
      if (selectedConfigId === id) {
        setSelectedConfigId(null);
        setConfigName('');
      }
      loadConfigs();
    } catch {
      message.error('Помилка видалення');
    }
  };

  // ── Mapping table columns ─────────────────────────────────────────

  const mappingTableColumns = [
    {
      title: '#',
      dataIndex: 'columnIndex',
      key: 'columnIndex',
      width: 50,
      render: (idx: number) => idx + 1,
    },
    {
      title: 'Заголовок з шаблону',
      dataIndex: 'headerText',
      key: 'headerText',
      width: 280,
      render: (text: string) => (
        <Tag color="blue" style={{ whiteSpace: 'normal', maxWidth: 260 }}>
          {text || '(порожній)'}
        </Tag>
      ),
    },
    {
      title: 'Поля даних (порядок збережеться)',
      key: 'fieldKeys',
      render: (_: unknown, record: ColumnMapping) => (
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="Оберіть одне або кілька полів..."
          value={record.fieldKeys}
          onChange={(val: string[]) => handleFieldKeysChange(record.columnIndex, val)}
          options={groupedOptions}
          optionRender={(option) => {
            const field = dataFields.find(f => f.key === option.value);
            return (
              <div>
                <div>{option.label}</div>
                {field?.descriptionHint && (
                  <div style={{ fontSize: 11, color: '#999' }}>{field.descriptionHint}</div>
                )}
              </div>
            );
          }}
          maxTagCount="responsive"
        />
      ),
    },
  ];

  // ── Mapped fields count ───────────────────────────────────────────

  const mappedCount = mappings.filter(m => m.fieldKeys.length > 0).length;
  const totalColumns = columns.length;

  // ── Render ────────────────────────────────────────────────────────

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card
        title={
          <Space>
            <FileWordOutlined />
            Експорт документів по кафедрі
          </Space>
        }
      >
        <Steps
          current={currentStep}
          onChange={(step) => {
            if (step < currentStep) {
              setCurrentStep(step);
            } else if (step === 1 && columns.length > 0) {
              setCurrentStep(1);
            } else if (step === 2 && selectedConfigId) {
              setCurrentStep(2);
            }
          }}
          items={[
            { title: 'Шаблон', description: 'Завантажити або обрати' },
            { title: 'Маппінг', description: 'Налаштувати колонки' },
            { title: 'Експорт', description: 'Обрати кафедри' },
          ]}
          style={{ marginBottom: 24 }}
        />

        {/* ── STEP 1: Template ──────────────────────────────────────── */}
        {currentStep === 0 && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {savedConfigs.length > 0 && (
              <Card size="small" title="Збережені конфігурації">
                <Table
                  dataSource={savedConfigs}
                  rowKey="id"
                  size="small"
                  pagination={false}
                  columns={[
                    { title: 'Назва', dataIndex: 'name', key: 'name' },
                    {
                      title: 'Файл шаблону',
                      dataIndex: 'originalFileName',
                      key: 'originalFileName',
                      render: (v: string) => v || '—',
                    },
                    {
                      title: 'Дії',
                      key: 'actions',
                      width: 180,
                      render: (_: unknown, record: DocxExportTemplate) => (
                        <Space>
                          <Button
                            size="small"
                            type="primary"
                            onClick={() => handleLoadConfig(record.id)}
                          >
                            Обрати
                          </Button>
                          <Popconfirm
                            title="Видалити конфігурацію?"
                            onConfirm={() => handleDeleteConfig(record.id)}
                          >
                            <Button size="small" danger icon={<DeleteOutlined />} />
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Card>
            )}

            <Card size="small" title="Завантажити новий шаблон">
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Dragger
                  accept=".docx"
                  maxCount={1}
                  fileList={fileList}
                  beforeUpload={() => false}
                  onChange={({ fileList: newList }) => setFileList(newList)}
                >
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p className="ant-upload-text">
                    Натисніть або перетягніть .docx файл шаблону
                  </p>
                  <p className="ant-upload-hint">
                    Шаблон повинен містити таблицю з заголовками колонок
                  </p>
                </Dragger>

                <Space>
                  <Tooltip title="Індекс таблиці в документі (0 = перша таблиця)">
                    <span>Таблиця №:</span>
                  </Tooltip>
                  <InputNumber
                    value={tableIndex}
                    onChange={(v) => setTableIndex(v ?? 0)}
                    min={0}
                    max={10}
                    style={{ width: 80 }}
                  />

                  <Tooltip title="Кількість рядків заголовка таблиці">
                    <span>Рядків заголовка:</span>
                  </Tooltip>
                  <InputNumber
                    value={headerRowCount}
                    onChange={(v) => setHeaderRowCount(v ?? 1)}
                    min={1}
                    max={5}
                    style={{ width: 80 }}
                  />
                </Space>

                <Button
                  type="primary"
                  icon={<UploadOutlined />}
                  loading={uploading}
                  disabled={fileList.length === 0}
                  onClick={handleUpload}
                >
                  Завантажити та розпарсити
                </Button>
              </Space>
            </Card>
          </Space>
        )}

        {/* ── STEP 2: Mapping ──────────────────────────────────────── */}
        {currentStep === 1 && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Alert
              type="info"
              showIcon
              message={`Знайдено ${totalColumns} колонок. Оберіть поля даних для кожної колонки.`}
              description={
                <>
                  {originalFileName && <div>Шаблон: {originalFileName}</div>}
                  <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>
                    Можна обрати кілька полів — вони будуть виведені в порядку вибору, кожне з нового рядка.
                  </div>
                </>
              }
            />

            <Table
              dataSource={mappings}
              rowKey="columnIndex"
              columns={mappingTableColumns}
              pagination={false}
              size="small"
              bordered
            />

            <div style={{ color: '#666' }}>
              Зіставлено: {mappedCount} / {totalColumns} колонок
            </div>

            <Divider />

            <Space>
              <Input
                placeholder="Назва конфігурації"
                value={configName}
                onChange={(e) => setConfigName(e.target.value)}
                style={{ width: 300 }}
              />
              <Button
                type="primary"
                icon={<SaveOutlined />}
                onClick={handleSaveMapping}
                disabled={mappedCount === 0}
              >
                {selectedConfigId ? 'Оновити конфігурацію' : 'Зберегти конфігурацію'}
              </Button>
            </Space>
          </Space>
        )}

        {/* ── STEP 3: Export ───────────────────────────────────────── */}
        {currentStep === 2 && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {selectedConfigId ? (
              <Alert
                type="success"
                showIcon
                message={`Конфігурація: "${configName || savedConfigs.find(c => c.id === selectedConfigId)?.name || ''}"`}
                description={`Маппінг: ${mappedCount} полів з ${totalColumns} колонок`}
              />
            ) : (
              <Alert
                type="warning"
                showIcon
                message="Конфігурація не збережена"
                description="Поверніться до кроку маппінгу та збережіть конфігурацію"
              />
            )}

            <Card size="small" title="Оберіть дані для експорту">
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Radio.Group
                  value={exportMode}
                  onChange={(e) => setExportMode(e.target.value)}
                  optionType="button"
                  buttonStyle="solid"
                  options={[
                    { value: 'departments', label: 'За кафедрами' },
                    { value: 'teachers', label: 'За викладачами' },
                    { value: 'program', label: 'За ОПП' },
                  ]}
                />

                {exportMode === 'departments' && (
                  <>
                    <Space>
                      <Button
                        size="small"
                        onClick={() => setSelectedDeptIds(departments.map(d => d.id))}
                        disabled={selectedDeptIds.length === departments.length}
                      >
                        Обрати всі
                      </Button>
                      <Button
                        size="small"
                        onClick={() => setSelectedDeptIds([])}
                        disabled={selectedDeptIds.length === 0}
                      >
                        Скинути
                      </Button>
                    </Space>
                    <Select
                      mode="multiple"
                      placeholder="Оберіть кафедри"
                      style={{ width: '100%', maxWidth: 600 }}
                      value={selectedDeptIds}
                      onChange={setSelectedDeptIds}
                      showSearch
                      optionFilterProp="label"
                      maxTagCount="responsive"
                      options={departments.map((d: any) => ({
                        value: d.id,
                        label: d.number ? `${d.number} — ${d.name}` : d.name,
                      }))}
                    />
                  </>
                )}

                {exportMode === 'teachers' && (
                  <Select
                    mode="multiple"
                    placeholder="Почніть вводити прізвище..."
                    style={{ width: '100%', maxWidth: 600 }}
                    value={selectedTeacherIds}
                    onChange={setSelectedTeacherIds}
                    showSearch
                    optionFilterProp="label"
                    maxTagCount="responsive"
                    filterOption={(input, option) =>
                      (option?.label?.toString() || '').toLowerCase().includes(input.toLowerCase())
                    }
                    options={allTeachers.map(t => ({
                      value: t.id,
                      label: `${t.lastName} ${t.firstName}${t.patronymic ? ' ' + t.patronymic : ''}`,
                    }))}
                  />
                )}

                {exportMode === 'program' && (
                  <Select
                    placeholder="Оберіть освітню програму..."
                    style={{ width: '100%', maxWidth: 600 }}
                    value={selectedProgramId}
                    onChange={setSelectedProgramId}
                    showSearch
                    optionFilterProp="label"
                    allowClear
                    options={programs.map(p => ({
                      value: p.id,
                      label: `${p.name}${p.shortCode ? ` (${p.shortCode})` : ''}${p.enrollmentYear ? ` — ${p.enrollmentYear}` : ''}${p.degree ? ` [${p.degree}]` : ''}`,
                    }))}
                  />
                )}

                <Button
                  type="primary"
                  size="large"
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  disabled={
                    !selectedConfigId ||
                    (exportMode === 'departments' && selectedDeptIds.length === 0) ||
                    (exportMode === 'teachers' && selectedTeacherIds.length === 0) ||
                    (exportMode === 'program' && !selectedProgramId)
                  }
                  onClick={handleExport}
                >
                  Експорт DOCX
                  {exportMode === 'departments' && selectedDeptIds.length > 0 && ` (${selectedDeptIds.length} каф.)`}
                  {exportMode === 'teachers' && selectedTeacherIds.length > 0 && ` (${selectedTeacherIds.length} осіб)`}
                  {exportMode === 'program' && selectedProgramId && (() => {
                    const p = programs.find(pr => pr.id === selectedProgramId);
                    return p ? ` (${p.shortCode || p.name})` : '';
                  })()}
                </Button>
              </Space>
            </Card>
          </Space>
        )}
      </Card>
    </Space>
  );
};

export default DocxConstructorPage;
