import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Input, Upload, message, Space, Tag, Typography, Descriptions } from 'antd';
import { UploadOutlined, SearchOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { formatDate } from '@/utils/dateUtils';

import { FakhovyiJournal, JOURNAL_CATEGORY_LABELS, VerificationResult } from '@/types/fakhove';
import { uploadFakhoviExcel, getFakhoviJournals, verifyJournal, getFakhoviCount } from '@/api/fakhoviApi';
import { useAuth } from '@/auth/AuthContext';

const { Title, Text } = Typography;
const { Search } = Input;

const FakhoviJournalsPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'HEAD_OF_DEPARTMENT';
  const [allJournals, setAllJournals] = useState<FakhovyiJournal[]>([]);
  const [filteredJournals, setFilteredJournals] = useState<FakhovyiJournal[]>([]);
  const [loading, setLoading] = useState(false);
  const [counts, setCounts] = useState<{ fakhovi: number }>({ fakhovi: 0 });
  const [uploading, setUploading] = useState(false);

  // Verification state
  const [verifyName, setVerifyName] = useState('');
  const [verifyIssn, setVerifyIssn] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null);

  const loadJournals = async () => {
    setLoading(true);
    try {
      const result = await getFakhoviJournals();
      setAllJournals(result);
      setFilteredJournals(result);
    } catch {
      message.error('Помилка завантаження журналів');
    } finally {
      setLoading(false);
    }
  };

  const loadCounts = async () => {
    try {
      const c = await getFakhoviCount();
      setCounts({ fakhovi: c.fakhovi });
    } catch {
      // ignore
    }
  };

  useEffect(() => { loadJournals(); }, []);
  useEffect(() => { loadCounts(); }, []);

  const handleSearch = (value: string) => {
    const q = value.trim().toLowerCase();
    if (q) {
      setFilteredJournals(allJournals.filter(j =>
        j.name.toLowerCase().includes(q) ||
        (j.founders && j.founders.toLowerCase().includes(q)) ||
        (j.specialtyCodes && j.specialtyCodes.includes(q))
      ));
    } else {
      setFilteredJournals(allJournals);
    }
  };

  const handleUploadFakhovi: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const result = await uploadFakhoviExcel(file as File);
      message.success(`Імпортовано ${result.imported} фахових видань`);
      onSuccess?.(result);
      loadJournals();
      loadCounts();
    } catch (err: any) {
      message.error('Помилка імпорту фахових видань');
      onError?.(err);
    } finally {
      setUploading(false);
    }
  };

  const handleVerify = async () => {
    if (!verifyName.trim()) {
      message.warning('Введіть назву журналу');
      return;
    }
    setVerifying(true);
    try {
      const result = await verifyJournal(verifyName, verifyIssn || undefined);
      setVerificationResult(result);
    } catch {
      message.error('Помилка верифікації');
    } finally {
      setVerifying(false);
    }
  };

  const fakhoviColumns: ColumnsType<FakhovyiJournal> = [
    { title: 'Назва видання', dataIndex: 'name', key: 'name', ellipsis: true },
    { title: 'Засновники', dataIndex: 'founders', key: 'founders', ellipsis: true, width: 200 },
    { title: 'Спеціальності', dataIndex: 'specialtyCodes', key: 'specialtyCodes', ellipsis: true, width: 140 },
    {
      title: 'Категорія', dataIndex: 'category', key: 'category', width: 120,
      render: (cat: string) => cat ? (
        <Tag color={cat === 'CATEGORY_A' ? 'green' : 'blue'}>
          {JOURNAL_CATEGORY_LABELS[cat as keyof typeof JOURNAL_CATEGORY_LABELS] || cat}
        </Tag>
      ) : '—',
    },
    {
      title: 'Дата включення', dataIndex: 'inclusionDate', key: 'inclusionDate', width: 130,
      render: (v: string) => formatDate(v),
    },
  ];

  return (
    <div>
      <Title level={4}>Довідник фахових видань</Title>

      {/* Статистика */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space size="large">
          <Text>Фахових видань: <strong>{counts.fakhovi}</strong></Text>
        </Space>
      </Card>

      {/* Upload секція — тільки для адміна/начальника */}
      {isAdmin && (
        <Card size="small" title="Імпорт даних" style={{ marginBottom: 16 }}>
          <Upload
            accept=".xlsx,.xls"
            showUploadList={false}
            customRequest={handleUploadFakhovi}
          >
            <Button icon={<UploadOutlined />} loading={uploading}>
              Завантажити фахові (Excel data.gov.ua)
            </Button>
          </Upload>
        </Card>
      )}

      {/* Верифікація журналу */}
      <Card size="small" title="Верифікація журналу" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Input
              placeholder="Назва журналу"
              value={verifyName}
              onChange={e => setVerifyName(e.target.value)}
              style={{ width: 400 }}
            />
            <Input
              placeholder="ISSN (опціонально)"
              value={verifyIssn}
              onChange={e => setVerifyIssn(e.target.value)}
              style={{ width: 180 }}
            />
            <Button
              icon={<SafetyCertificateOutlined />}
              onClick={handleVerify}
              loading={verifying}
              type="primary"
            >
              Перевірити
            </Button>
          </Space>
          {verificationResult && (
            <Descriptions bordered size="small" column={2} style={{ marginTop: 8 }}>
              <Descriptions.Item label="Фахове">
                <Tag color={verificationResult.isFakhove ? 'green' : 'default'}>
                  {verificationResult.isFakhove ? 'Так' : 'Ні'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Категорія">
                {verificationResult.category
                  ? <Tag color={verificationResult.category === 'CATEGORY_A' ? 'green' : 'blue'}>
                      {JOURNAL_CATEGORY_LABELS[verificationResult.category]}
                    </Tag>
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Знайдене видання">
                {verificationResult.matchedFakhoveName || '—'}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Space>
      </Card>

      {/* Таблиця журналів */}
      <Card size="small">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Search
            placeholder="Пошук за назвою..."
            allowClear
            enterButton={<SearchOutlined />}
            onSearch={handleSearch}
            style={{ width: 400 }}
          />

          <Table
            dataSource={filteredJournals}
            columns={fakhoviColumns}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 20,
              showSizeChanger: false,
              showTotal: (total) => `Всього: ${total}`,
            }}
          />
        </Space>
      </Card>
    </div>
  );
};

export default FakhoviJournalsPage;
