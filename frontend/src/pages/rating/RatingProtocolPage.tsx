import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
  Button, Card, Form, Input, Space, Typography, Select, message,
  Row, Col, DatePicker, Spin,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, DownloadOutlined, ArrowLeftOutlined,
  FileWordOutlined, SaveOutlined, ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { renderAsync } from 'docx-preview';
import {
  getRatingPeriods, generateProtocol, getProtocolSettings, saveProtocolSettings,
} from '../../api/ratingCalcApi';
import type {
  RatingPeriodDto, CommissionMember, ProtocolRequest, ProtocolSettingsData,
} from '../../api/ratingCalcApi';

const { Title, Text } = Typography;

const ROLE_OPTIONS = [
  { value: 'CHAIR' as const, label: 'Голова комісії' },
  { value: 'VICE_CHAIR' as const, label: 'Заступник голови' },
  { value: 'SECRETARY' as const, label: 'Секретар комісії' },
  { value: 'MEMBER' as const, label: 'Член комісії' },
];

const STORAGE_KEY = 'protocol_settings';
const saveLocal = (d: ProtocolSettingsData) => {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(d)); } catch {/* */}
};
const loadLocal = (): ProtocolSettingsData | null => {
  try { const r = localStorage.getItem(STORAGE_KEY); return r ? JSON.parse(r) : null; } catch { return null; }
};

const DEFAULT_MEMBERS: CommissionMember[] = [
  { role: 'CHAIR', rank: '', name: '', shortName: '' },
  { role: 'VICE_CHAIR', rank: '', name: '', shortName: '' },
  { role: 'SECRETARY', rank: '', name: '', shortName: '' },
  { role: 'MEMBER', rank: '', name: '', shortName: '' },
];

const RatingProtocolPage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const previewRef = useRef<HTMLDivElement>(null);
  const debRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const lastBlobRef = useRef<Blob | null>(null);

  const [periods, setPeriods] = useState<RatingPeriodDto[]>([]);
  const [periodId, setPeriodId] = useState<number | null>(null);
  const [dling, setDling] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [members, setMembers] = useState<CommissionMember[]>(DEFAULT_MEMBERS);

  /* ── init ── */
  useEffect(() => {
    const local = loadLocal();
    if (local) applySettings(local);
    Promise.all([getRatingPeriods(), getProtocolSettings().catch(() => null)]).then(([p, remote]) => {
      setPeriods(p);
      const a = p.find(x => x.active);
      setPeriodId(a ? a.id : p[0]?.id ?? null);
      if (remote && !local) { applySettings(remote); saveLocal(remote); }
    });
  }, []);

  const applySettings = (s: ProtocolSettingsData) => {
    if (s.institutionName) form.setFieldValue('institutionName', s.institutionName);
    if (s.orderNumber) form.setFieldValue('orderNumber', s.orderNumber);
    if (s.orderDate) form.setFieldValue('orderDate', s.orderDate);
    if (s.commissionMembers?.length) setMembers(s.commissionMembers);
  };

  /* ── persist ── */
  const doSave = useCallback(() => {
    const v = form.getFieldsValue();
    const d: ProtocolSettingsData = {
      institutionName: v.institutionName, orderNumber: v.orderNumber,
      orderDate: v.orderDate, commissionMembers: members,
    };
    saveLocal(d);
    saveProtocolSettings(d).catch(() => {});
  }, [members, form]);

  /* ── build request ── */
  const buildRequest = useCallback((): ProtocolRequest => {
    const v = form.getFieldsValue();
    return {
      institutionName: v.institutionName || '',
      protocolNumber: v.protocolNumber || '',
      protocolDate: v.protocolDate ? dayjs(v.protocolDate).format('DD.MM.YYYY') : '',
      orderNumber: v.orderNumber || '',
      orderDate: v.orderDate || '',
      commissionMembers: members.filter(m => m.name),
    };
  }, [members, form]);

  /* ── generate preview ── */
  const refreshPreview = useCallback(async () => {
    if (!periodId || !previewRef.current) return;
    setPreviewing(true);
    try {
      const blob = await generateProtocol(periodId, buildRequest());
      lastBlobRef.current = blob;
      // Clear container
      const container = previewRef.current;
      container.innerHTML = '';
      // Render DOCX into container
      await renderAsync(blob, container, undefined, {
        className: 'docx-preview',
        inWrapper: true,
        ignoreWidth: false,
        ignoreHeight: false,
        ignoreFonts: false,
        breakPages: true,
        ignoreLastRenderedPageBreak: true,
        experimental: false,
        trimXmlDeclaration: true,
        useBase64URL: false,
        renderHeaders: true,
        renderFooters: true,
        renderFootnotes: true,
        renderEndnotes: true,
      });
    } catch (e) {
      console.error('Preview error', e);
      if (previewRef.current) {
        previewRef.current.innerHTML = '<p style="color:red;padding:20px">Помилка генерації попереднього перегляду</p>';
      }
    } finally {
      setPreviewing(false);
    }
  }, [periodId, buildRequest]);

  /* ── auto-refresh preview (debounced) ── */
  const schedulePreview = useCallback(() => {
    if (debRef.current) clearTimeout(debRef.current);
    debRef.current = setTimeout(() => {
      doSave();
      refreshPreview();
    }, 1000);
  }, [doSave, refreshPreview]);

  // Initial preview when period selected
  useEffect(() => {
    if (periodId) {
      // Small delay to let form initialize
      const t = setTimeout(refreshPreview, 300);
      return () => clearTimeout(t);
    }
  }, [periodId]);

  /* ── members / form change ── */
  const addM = () => setMembers([...members, { role: 'MEMBER', rank: '', name: '', shortName: '' }]);
  const delM = (i: number) => {
    setMembers(members.filter((_, j) => j !== i));
    setTimeout(schedulePreview, 50);
  };
  const updM = (i: number, f: keyof CommissionMember, v: string) => {
    setMembers(ms => ms.map((m, j) => j === i ? { ...m, [f]: v } : m));
    schedulePreview();
  };

  const onFormChange = () => { schedulePreview(); };
  const onSave = () => { doSave(); message.success('Збережено'); };

  /* ── download (use cached blob or regenerate) ── */
  const onDownload = async () => {
    if (!periodId) return;
    setDling(true);
    try {
      const blob = lastBlobRef.current || await generateProtocol(periodId, buildRequest());
      const url = URL.createObjectURL(blob);
      Object.assign(document.createElement('a'), {
        href: url, download: `protocol_rating_${periods.find(p => p.id === periodId)?.name || ''}.docx`,
      }).click();
      URL.revokeObjectURL(url);
      message.success('Завантажено');
    } catch { message.error('Помилка'); }
    finally { setDling(false); }
  };

  return (
    <div>
      {/* ── Toolbar ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/rating')}>Назад</Button>
          <Title level={4} style={{ margin: 0 }}><FileWordOutlined /> Протокол рейтингової комісії</Title>
        </Space>
        <Space>
          <Select value={periodId ?? undefined} onChange={v => { setPeriodId(v); }} style={{ width: 200 }}
            placeholder="Період" options={periods.map(p => ({ value: p.id, label: `${p.name}${p.active ? ' ●' : ''}` }))} />
          <Button icon={<ReloadOutlined />} onClick={refreshPreview} loading={previewing}>Оновити</Button>
          <Button icon={<SaveOutlined />} onClick={onSave}>Зберегти</Button>
          <Button type="primary" icon={<DownloadOutlined />} loading={dling} onClick={onDownload}
            disabled={!periodId}>Завантажити DOCX</Button>
        </Space>
      </div>

      <Row gutter={16}>
        {/* ═══ LEFT: settings ═══ */}
        <Col xs={24} lg={7}>
          <Card title="Параметри протоколу" size="small" style={{ marginBottom: 12 }}>
            <Form form={form} layout="vertical" size="small" onValuesChange={onFormChange}
              initialValues={{ institutionName: 'Військового інституту телекомунікацій та інформатизації імені Героїв Крут' }}>
              <Form.Item label="Назва закладу (родовий відмінок)" name="institutionName">
                <Input.TextArea rows={2} />
              </Form.Item>
              <Row gutter={8}>
                <Col span={12}><Form.Item label="Дата протоколу" name="protocolDate">
                  <DatePicker style={{ width: '100%' }} format="DD.MM.YYYY" />
                </Form.Item></Col>
                <Col span={12}><Form.Item label="№ протоколу" name="protocolNumber">
                  <Input placeholder="1" />
                </Form.Item></Col>
              </Row>
              <Row gutter={8}>
                <Col span={12}><Form.Item label="№ наказу" name="orderNumber">
                  <Input placeholder="___/нагд" />
                </Form.Item></Col>
                <Col span={12}><Form.Item label="Дата наказу" name="orderDate">
                  <Input placeholder="___.___ 20___" />
                </Form.Item></Col>
              </Row>
            </Form>
          </Card>

          <Card title="Склад комісії" size="small"
            extra={<Button type="link" size="small" icon={<PlusOutlined />} onClick={addM}>Додати</Button>}>
            {members.map((m, i) => (
              <div key={i} style={{ marginBottom: 10, padding: 8, background: '#fafafa', borderRadius: 6, border: '1px solid #f0f0f0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                  <Select value={m.role} onChange={v => updM(i, 'role', v)} size="small"
                    style={{ width: 175 }} options={ROLE_OPTIONS} />
                  {members.length > 1 && <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => delM(i)} />}
                </div>
                <Input size="small" placeholder="Звання (полковник)" value={m.rank}
                  onChange={e => updM(i, 'rank', e.target.value)} style={{ marginBottom: 3 }} />
                <Input size="small" placeholder="Ім'я ПРІЗВИЩЕ" value={m.name}
                  onChange={e => updM(i, 'name', e.target.value)} style={{ marginBottom: 3 }} />
                <Input size="small" placeholder="Скор. ПІБ (Сачука О.В.)" value={m.shortName}
                  onChange={e => updM(i, 'shortName', e.target.value)} />
              </div>
            ))}
            <Text type="secondary" style={{ fontSize: 11 }}>Звання в називному — в тексті автоматично родовий</Text>
          </Card>
        </Col>

        {/* ═══ RIGHT: DOCX preview ═══ */}
        <Col xs={24} lg={17}>
          <Card
            title="Попередній перегляд"
            size="small"
            styles={{ body: { padding: 0, position: 'relative', minHeight: 400 } }}
          >
            {previewing && (
              <div style={{
                position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
                background: 'rgba(255,255,255,0.7)', zIndex: 10,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Spin size="large" tip="Генерація..." />
              </div>
            )}
            <div
              ref={previewRef}
              style={{ background: '#e0e0e0', minHeight: 400, overflow: 'auto' }}
            />
          </Card>
        </Col>
      </Row>

      {/* docx-preview wrapper styling */}
      <style>{`
        .docx-preview .docx-wrapper {
          background: #e0e0e0 !important;
          padding: 16px !important;
        }
        .docx-preview .docx-wrapper > section.docx {
          box-shadow: 0 2px 8px rgba(0,0,0,0.2) !important;
          margin-bottom: 16px !important;
        }
      `}</style>
    </div>
  );
};

export default RatingProtocolPage;
