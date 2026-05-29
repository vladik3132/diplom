import React, { useEffect, useRef, useState } from 'react';
import { Card, Button, Select, Typography, Space, message, Alert, Descriptions, Tag, Switch, Tooltip } from 'antd';
import { UploadOutlined, ImportOutlined, FileWordOutlined, DeleteOutlined, RobotOutlined } from '@ant-design/icons';
import { getDepartments } from '@/api/departmentApi';
import { Department } from '@/types/teacher';
import { useAuth } from '@/auth/AuthContext';

const { Title, Text, Paragraph } = Typography;

interface ImportResult {
  teachersImported: number;
  achievementsImported: number;
  disciplinesAssigned: number;
  publicationsImported: number;
  ppDataImported: number;
  qualificationsImported: number;
  careerRecordsImported: number;
  languageSkillsImported: number;
  errors: string[];
}

const DataImportPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | undefined>();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [useAi, setUseAi] = useState(true);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getDepartments().then((depts) => {
      setDepartments(depts);
      if (!isAdmin && user?.departmentId) {
        setSelectedDepartmentId(user.departmentId);
      }
    }).catch(() => setDepartments([]));
  }, [isAdmin, user?.departmentId]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.endsWith('.docx')) {
      message.error('Підтримуються тільки файли .docx');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    setSelectedFile(file);
  };

  const handleRemoveFile = () => {
    setSelectedFile(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleUpload = async () => {
    if (!selectedDepartmentId) {
      message.warning('Оберіть кафедру');
      return;
    }
    if (!selectedFile) {
      message.warning('Оберіть DOCX файл');
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('departmentId', String(selectedDepartmentId));
    formData.append('useAi', String(useAi));

    setLoading(true);
    setResult(null);

    try {
      const token = localStorage.getItem('keycloak_token') || localStorage.getItem('token');
      const resp = await fetch('/api/import/docx', {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      });
      if (!resp.ok) {
        const errText = await resp.text();
        throw new Error(errText || `HTTP ${resp.status}`);
      }
      const data: ImportResult = await resp.json();
      setResult(data);
      if (data.teachersImported > 0) {
        message.success(`Імпортовано ${data.teachersImported} викладачів, ${data.achievementsImported} досягнень, ${data.publicationsImported || 0} публікацій, ${data.ppDataImported || 0} структур. даних, ${data.qualificationsImported || 0} кваліфікацій, ${data.careerRecordsImported || 0} послужних записів, ${data.languageSkillsImported || 0} мов, ${data.disciplinesAssigned || 0} дисциплін`);
      } else if (data.errors.length > 0) {
        message.warning('Імпорт завершено з помилками');
      } else {
        message.info('Не знайдено даних для імпорту');
      }
    } catch (err: any) {
      message.error('Помилка імпорту: ' + (err?.message || 'невідома помилка'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Title level={4}>
        <ImportOutlined /> Імпорт даних з DOCX
      </Title>

      <Card style={{ marginBottom: 16 }}>
        <Paragraph>
          Завантажте документ <Text strong>кадрового забезпечення</Text> у форматі DOCX.
          Система автоматично розпарсить таблицю та створить записи про викладачів, їхні досягнення за п.38.
        </Paragraph>
        <Paragraph type="secondary">
          Підтримуваний формат: таблиця кадрового забезпечення (11 стовпців: ПІБ, посада, дисципліни, освіта, наукова кваліфікація,
          стаж, бойовий досвід, ORCID/email/Scopus, публікації, підпункти п.38).
        </Paragraph>
      </Card>

      <Card title="Параметри імпорту" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>1. Оберіть кафедру:</Text>
            <Select
              placeholder="Оберіть кафедру, до якої належать викладачі"
              value={selectedDepartmentId}
              onChange={setSelectedDepartmentId}
              style={{ width: '100%', maxWidth: 500 }}
              showSearch
              optionFilterProp="label"
              disabled={!isAdmin}
              options={departments.map((d: any) => ({ value: d.id, label: d.number ? `${d.number} — ${d.name}` : d.name }))}
            />
          </div>

          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>2. Оберіть файл DOCX:</Text>
            <Space>
              <Button
                icon={<FileWordOutlined />}
                onClick={() => fileInputRef.current?.click()}
              >
                Обрати файл .docx
              </Button>
              {selectedFile && (
                <>
                  <Text>{selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)</Text>
                  <Button icon={<DeleteOutlined />} size="small" danger onClick={handleRemoveFile} />
                </>
              )}
            </Space>
            <input
              ref={fileInputRef}
              type="file"
              accept=".docx"
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />
          </div>

          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>3. Режим парсингу:</Text>
            <Space>
              <Switch
                checked={useAi}
                onChange={setUseAi}
                checkedChildren={<><RobotOutlined /> ШІ</>}
                unCheckedChildren="Regex"
              />
              <Tooltip title={useAi
                ? 'ШІ (Mistral) аналізує таблицю та витягує структуровані дані. Краще справляється з нестандартним форматуванням.'
                : 'Regex парсер — швидкий, але вимагає точного формату таблиці.'
              }>
                <Text type="secondary">
                  {useAi ? 'ШІ-парсинг (рекомендовано)' : 'Regex-парсинг (базовий)'}
                </Text>
              </Tooltip>
            </Space>
          </div>

          <Button
            type="primary"
            icon={useAi ? <RobotOutlined /> : <UploadOutlined />}
            onClick={handleUpload}
            loading={loading}
            disabled={!selectedDepartmentId || !selectedFile}
            size="large"
          >
            {useAi ? 'Імпортувати з ШІ' : 'Імпортувати'}
          </Button>
        </Space>
      </Card>

      {result && (
        <Card title="Результат імпорту">
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="Викладачів імпортовано">
              <Tag color={result.teachersImported > 0 ? 'green' : 'default'}>
                {result.teachersImported}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Досягнень імпортовано">
              <Tag color={result.achievementsImported > 0 ? 'blue' : 'default'}>
                {result.achievementsImported}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Публікацій імпортовано">
              <Tag color={(result.publicationsImported || 0) > 0 ? 'cyan' : 'default'}>
                {result.publicationsImported || 0}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Структурованих даних (пп.5-20)">
              <Tag color={(result.ppDataImported || 0) > 0 ? 'lime' : 'default'}>
                {result.ppDataImported || 0}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Кваліфікацій імпортовано">
              <Tag color={(result.qualificationsImported || 0) > 0 ? 'orange' : 'default'}>
                {result.qualificationsImported || 0}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Послужних записів імпортовано">
              <Tag color={(result.careerRecordsImported || 0) > 0 ? 'geekblue' : 'default'}>
                {result.careerRecordsImported || 0}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Іноземних мов імпортовано">
              <Tag color={(result.languageSkillsImported || 0) > 0 ? 'magenta' : 'default'}>
                {result.languageSkillsImported || 0}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Дисциплін призначено">
              <Tag color={(result.disciplinesAssigned || 0) > 0 ? 'purple' : 'default'}>
                {result.disciplinesAssigned || 0}
              </Tag>
            </Descriptions.Item>
          </Descriptions>

          {result.errors.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 16 }}
              message={`Помилки (${result.errors.length})`}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {result.errors.map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              }
            />
          )}
        </Card>
      )}
    </div>
  );
};

export default DataImportPage;
