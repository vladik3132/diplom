import React, { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Card, Input, Button, List, Space, message, Typography, Tag, Tooltip, Modal, Table } from 'antd';
import { SendOutlined, RobotOutlined, UserOutlined, ClearOutlined, ReloadOutlined, ToolOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import api from '@/api/axiosInstance';
import { useAuth } from '@/auth/AuthContext';

const CONVERSATION_KEY = 'ai-conversation-id';

/**
 * Отримати існуючий conversationId з localStorage або згенерувати новий.
 * Fallback на ephemeral UUID якщо localStorage недоступний (наприклад, private mode).
 */
const getOrCreateConversationId = (): string => {
  try {
    let id = localStorage.getItem(CONVERSATION_KEY);
    if (!id) {
      id = (typeof crypto !== 'undefined' && crypto.randomUUID)
        ? crypto.randomUUID()
        : `conv-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      localStorage.setItem(CONVERSATION_KEY, id);
    }
    return id;
  } catch {
    return `conv-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
};

const generateNewId = (): string => {
  try {
    return (typeof crypto !== 'undefined' && crypto.randomUUID)
      ? crypto.randomUUID()
      : `conv-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  } catch {
    return `conv-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
};

const { Text } = Typography;

const markdownStyles = `
.ai-markdown p { margin: 4px 0; }
.ai-markdown ul, .ai-markdown ol { margin: 4px 0; padding-left: 20px; }
.ai-markdown li { margin: 2px 0; }
.ai-markdown h1, .ai-markdown h2, .ai-markdown h3 { margin: 8px 0 4px; }
.ai-markdown h1 { font-size: 1.2em; }
.ai-markdown h2 { font-size: 1.1em; }
.ai-markdown h3 { font-size: 1em; }
.ai-markdown hr { margin: 8px 0; border: none; border-top: 1px solid #e8e8e8; }
.ai-markdown code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 0.9em; }
.ai-markdown pre { background: #f5f5f5; padding: 8px; border-radius: 4px; overflow-x: auto; }
.ai-markdown pre code { background: none; padding: 0; }
.ai-markdown strong { font-weight: 600; }
.ai-markdown blockquote { border-left: 3px solid #1890ff; padding-left: 12px; margin: 4px 0; color: #555; }
.ai-markdown { overflow-x: auto; }
.ai-markdown table {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 0.92em;
  table-layout: auto;
}
.ai-markdown th, .ai-markdown td {
  border: 1px solid #d9d9d9;
  padding: 6px 10px;
  vertical-align: top;
  text-align: left;
}
.ai-markdown th {
  background: #e6f4ff;
  font-weight: 600;
  white-space: nowrap;
}
.ai-markdown tbody tr:nth-child(even) { background: #fafafa; }
.ai-markdown tbody tr:hover { background: #f0f7ff; }
.ai-markdown del { color: #999; text-decoration: line-through; }
.ai-loading-dots::after {
  display: inline-block;
  animation: ai-dots 1.4s steps(4, end) infinite;
  content: '';
  width: 1em;
  text-align: left;
}
@keyframes ai-dots {
  0%   { content: ''; }
  25%  { content: '.'; }
  50%  { content: '..'; }
  75%  { content: '...'; }
  100% { content: ''; }
}
`;

interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

interface CombatNormalizationItem {
  teacherId: number;
  teacherName: string;
  original: string;
  normalized: string;
  status: 'OK' | 'ALREADY_CANONICAL' | 'FALLBACK_REGEX' | 'FAILED';
  reason: string;
}

interface CombatNormalizationReport {
  totalScanned: number;
  alreadyCanonical: number;
  normalizedByAi: number;
  normalizedByRegex: number;
  failed: number;
  items: CombatNormalizationItem[];
}

const LOADING_HINTS = [
  'Аналізую запит…',
  'Визначаю які дані потрібні…',
  'Викликаю інструменти БД…',
  'Обробляю результати…',
  'Формую відповідь…',
];

const AiAssistantPage: React.FC = () => {
  const { isAdmin, isHead } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingHint, setLoadingHint] = useState(LOADING_HINTS[0]);
  const [reindexing, setReindexing] = useState(false);
  const [combatModalOpen, setCombatModalOpen] = useState(false);
  const [combatLoading, setCombatLoading] = useState(false);
  const [combatReport, setCombatReport] = useState<CombatNormalizationReport | null>(null);
  const [conversationId, setConversationId] = useState<string>(() => getOrCreateConversationId());
  const msgIdRef = React.useRef(0);

  /** Запускає dry-run нормалізації дат бойового досвіду — показує що буде змінено. */
  const handleCombatDatesPreview = async () => {
    setCombatModalOpen(true);
    setCombatLoading(true);
    setCombatReport(null);
    try {
      const { data } = await api.post<CombatNormalizationReport>(
        '/admin/normalize-combat-dates?dryRun=true'
      );
      setCombatReport(data);
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Помилка попереднього перегляду');
      setCombatModalOpen(false);
    } finally {
      setCombatLoading(false);
    }
  };

  /** Застосовує нормалізацію (без dryRun) та закриває модалку. */
  const handleCombatDatesApply = async () => {
    setCombatLoading(true);
    try {
      const { data } = await api.post<CombatNormalizationReport>(
        '/admin/normalize-combat-dates?dryRun=false'
      );
      setCombatReport(data);
      message.success(
        `Оновлено ${data.normalizedByAi + data.normalizedByRegex} записів `
        + `(вже канонічних: ${data.alreadyCanonical}, помилок: ${data.failed})`
      );
    } catch (err: any) {
      message.error(err?.response?.data?.message || 'Помилка застосування');
    } finally {
      setCombatLoading(false);
    }
  };

  /** Ручна реіндексація RAG (лише для ADMIN). */
  const handleReindex = async () => {
    setReindexing(true);
    try {
      const { data } = await api.post('/ai/reindex');
      message.success(`Проіндексовано ${data.indexed} викладачів у vector store`);
    } catch (err: any) {
      if (err?.response?.status === 501) {
        message.warning('RAG недоступний (dev профіль)');
      } else {
        message.error('Помилка реіндексації');
      }
    } finally {
      setReindexing(false);
    }
  };

  // Ротація підказок під час завантаження (UX: показує що йде робота з tools)
  React.useEffect(() => {
    if (!loading) { setLoadingHint(LOADING_HINTS[0]); return; }
    let idx = 0;
    const int = setInterval(() => {
      idx = (idx + 1) % LOADING_HINTS.length;
      setLoadingHint(LOADING_HINTS[idx]);
    }, 1800);
    return () => clearInterval(int);
  }, [loading]);

  /**
   * Новий чат: очищує пам'ять на бекенді (best-effort), генерує новий conversationId
   * та очищує UI. Якщо backend недоступний — все одно очищується локально.
   */
  const handleNewChat = async () => {
    const oldId = conversationId;
    // Best-effort cleanup on backend (ignore errors — memory auto-clears on restart anyway)
    api.delete(`/ai/chat/${encodeURIComponent(oldId)}`).catch(() => { /* ignore */ });

    const newId = generateNewId();
    try {
      localStorage.setItem(CONVERSATION_KEY, newId);
    } catch { /* ignore */ }
    setConversationId(newId);
    setMessages([]);
    msgIdRef.current = 0;
    setInput('');
  };

  const handleSend = async (text?: string) => {
    const msg = (text ?? input).trim();
    if (!msg) return;

    const userMsg: ChatMessage = {
      id: ++msgIdRef.current,
      role: 'user',
      content: msg,
      timestamp: new Date().toLocaleTimeString(),
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const { data } = await api.post('/ai/chat', { message: msg, conversationId });
      if (data.success) {
        const aiMsg: ChatMessage = {
          id: ++msgIdRef.current,
          role: 'assistant',
          content: data.response,
          timestamp: new Date().toLocaleTimeString(),
        };
        setMessages(prev => [...prev, aiMsg]);
      } else {
        message.error(data.error || 'Помилка AI');
      }
    } catch {
      message.error('AI-асистент недоступний. Перезапустіть бекенд з ai.enabled=true');
    } finally {
      setLoading(false);
    }
  };

  const sendMessage = () => handleSend();

  const classifyAchievement = async () => {
    if (!input.trim()) return;

    const userMsg: ChatMessage = {
      id: ++msgIdRef.current,
      role: 'user',
      content: `[Класифікація] ${input.trim()}`,
      timestamp: new Date().toLocaleTimeString(),
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const { data } = await api.post('/ai/classify', { message: input.trim() });
      const aiMsg: ChatMessage = {
        id: ++msgIdRef.current,
        role: 'assistant',
        content: `Тип п.38: пп.${data.type}\nВпевненість: ${(data.confidence * 100).toFixed(0)}%\nОбґрунтування: ${data.reasoning}`,
        timestamp: new Date().toLocaleTimeString(),
      };
      setMessages(prev => [...prev, aiMsg]);
    } catch {
      message.error('Помилка класифікації');
    } finally {
      setLoading(false);
    }
  };

  // Role guard — ПІСЛЯ всіх хуків, щоб не порушити Rules of Hooks.
  // Доступ лише для ADMIN та HEAD_OF_DEPARTMENT; TEACHER → редірект на dashboard.
  if (!isAdmin && !isHead) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <Card
      title={
        <Space wrap>
          <RobotOutlined /> AI-асистент
          <Tag color="purple">пам'ять діалогу</Tag>
          <Tag color="blue">tool calling</Tag>
          <Tag color="magenta">семантичний пошук</Tag>
        </Space>
      }
      extra={
        <Space>
          {isAdmin && (
            <>
              <Tooltip title="Прогнати всі поля «Дати бойового досвіду» через AI-нормалізатор. Спочатку покаже preview — застосуй вручну.">
                <Button
                  icon={<ToolOutlined />}
                  onClick={handleCombatDatesPreview}
                  disabled={loading || combatLoading}
                >
                  Норм. дати БД
                </Button>
              </Tooltip>
              <Tooltip title="Повна реіндексація RAG (vector store). Запускається автоматично кожні 5 хв — натискайте лише після bulk import.">
                <Button
                  icon={<ReloadOutlined />}
                  onClick={handleReindex}
                  loading={reindexing}
                  disabled={loading}
                >
                  Реіндекс
                </Button>
              </Tooltip>
            </>
          )}
          <Tooltip title="Розпочати новий чат (очищує історію діалогу)">
            <Button
              icon={<ClearOutlined />}
              onClick={handleNewChat}
              disabled={loading || messages.length === 0}
            >
              Новий чат
            </Button>
          </Tooltip>
        </Space>
      }
    >
      <style>{markdownStyles}</style>
      <div style={{ height: 500, overflowY: 'auto', marginBottom: 16, padding: 16, background: '#fafafa', borderRadius: 8 }}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
            <RobotOutlined style={{ fontSize: 48 }} />
            <p>AI-асистент з доступом до актуальних даних системи</p>
            <p style={{ fontSize: 12, color: '#bbb', marginTop: -8 }}>
              AI викликає інструменти БД для точних даних. Можна ставити уточнюючі запитання — пам'ятає контекст діалогу.
            </p>

            <div style={{ marginTop: 16, textAlign: 'left', maxWidth: 640, margin: '16px auto' }}>
              <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>📊 Огляд і статистика</Text>
              </div>
              <Space wrap size={[4, 8]} style={{ marginBottom: 12 }}>
                {[
                  'Загальний стан системи',
                  'Огляд усіх кафедр',
                  'Хто не відповідає п.38?',
                  'Проблемні кафедри',
                ].map((q) => (
                  <Tag key={q} color="blue" style={{ cursor: 'pointer' }} onClick={() => handleSend(q)}>{q}</Tag>
                ))}
              </Space>

              <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>👤 Конкретні викладачі та кафедри</Text>
              </div>
              <Space wrap size={[4, 8]} style={{ marginBottom: 12 }}>
                {[
                  'Розкажи про Стоцького',
                  'Хто працює на кафедрі 210?',
                  'Стан кафедри управління військ зв\'язку',
                ].map((q) => (
                  <Tag key={q} color="geekblue" style={{ cursor: 'pointer' }} onClick={() => handleSend(q)}>{q}</Tag>
                ))}
              </Space>

              <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>🎓 Мови, курси, досягнення</Text>
              </div>
              <Space wrap size={[4, 8]} style={{ marginBottom: 12 }}>
                {[
                  'Хто має СМР ≥ 2 з англійської?',
                  'Хто проходив курси NATO?',
                  'Хто проходив SANS?',
                ].map((q) => (
                  <Tag key={q} color="cyan" style={{ cursor: 'pointer' }} onClick={() => handleSend(q)}>{q}</Tag>
                ))}
              </Space>

              <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>🔍 Семантичний пошук (RAG)</Text>
              </div>
              <Space wrap size={[4, 8]} style={{ marginBottom: 12 }}>
                {[
                  'Хто воював в АТО/ООС?',
                  'Хто досліджував штучний інтелект?',
                  'Фахівці з кібербезпеки',
                  'Хто працював над радіолокацією?',
                ].map((q) => (
                  <Tag key={q} color="purple" style={{ cursor: 'pointer' }} onClick={() => handleSend(q)}>{q}</Tag>
                ))}
              </Space>

              <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>🤖 Інше</Text>
              </div>
              <Space wrap size={[4, 8]}>
                <Tag color="green" style={{ cursor: 'pointer' }} onClick={() => setInput('Опишіть досягнення для класифікації...')}>
                  Класифікувати досягнення п.38
                </Tag>
              </Space>
            </div>
          </div>
        ) : (
          <>
            <List
              dataSource={messages}
              renderItem={(msg) => (
                <List.Item style={{ border: 'none', padding: '4px 0' }}>
                  <div style={{
                    display: 'flex',
                    justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                    width: '100%',
                  }}>
                    <div style={{
                      maxWidth: '70%',
                      padding: '8px 16px',
                      borderRadius: 12,
                      background: msg.role === 'user' ? '#1890ff' : '#fff',
                      color: msg.role === 'user' ? '#fff' : '#000',
                      border: msg.role === 'assistant' ? '1px solid #d9d9d9' : 'none',
                    }}>
                      <Space size={4} style={{ marginBottom: 4 }}>
                        {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                        <Text style={{ fontSize: 11, color: msg.role === 'user' ? '#ccc' : '#999' }}>{msg.timestamp}</Text>
                      </Space>
                      {msg.role === 'assistant' ? (
                        <div className="ai-markdown">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                        </div>
                      ) : (
                        <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                      )}
                    </div>
                  </div>
                </List.Item>
              )}
            />
            {loading && (
              <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '4px 0' }}>
                <div style={{
                  maxWidth: '70%',
                  padding: '8px 16px',
                  borderRadius: 12,
                  background: '#fff',
                  border: '1px solid #d9d9d9',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}>
                  <RobotOutlined style={{ color: '#1890ff' }} />
                  <span className="ai-loading-dots" style={{ fontSize: 13, color: '#666' }}>
                    {loadingHint}
                  </span>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <Space.Compact style={{ width: '100%' }}>
        <Input
          value={input}
          onChange={e => setInput(e.target.value)}
          onPressEnter={sendMessage}
          placeholder="Введіть повідомлення..."
          disabled={loading}
          size="large"
        />
        <Button type="primary" icon={<SendOutlined />} onClick={sendMessage} loading={loading} size="large">
          Запитати
        </Button>
        <Button onClick={classifyAchievement} loading={loading} size="large">
          Класифікувати п.38
        </Button>
      </Space.Compact>

      <Modal
        title={<Space><ToolOutlined /> Нормалізація дат бойового досвіду</Space>}
        open={combatModalOpen}
        onCancel={() => setCombatModalOpen(false)}
        width={1000}
        footer={[
          <Button key="cancel" onClick={() => setCombatModalOpen(false)}>
            Закрити
          </Button>,
          <Button
            key="apply"
            type="primary"
            loading={combatLoading}
            disabled={!combatReport || (combatReport.normalizedByAi + combatReport.normalizedByRegex) === 0}
            onClick={handleCombatDatesApply}
          >
            Застосувати ({combatReport ? combatReport.normalizedByAi + combatReport.normalizedByRegex : 0} змін)
          </Button>,
        ]}
      >
        {combatLoading && !combatReport && <div style={{ padding: 32, textAlign: 'center' }}>Аналізую…</div>}
        {combatReport && (
          <>
            <Space wrap style={{ marginBottom: 12 }}>
              <Tag>Усього: {combatReport.totalScanned}</Tag>
              <Tag color="default">Вже канонічно: {combatReport.alreadyCanonical}</Tag>
              <Tag color="green">AI: {combatReport.normalizedByAi}</Tag>
              <Tag color="blue">Regex: {combatReport.normalizedByRegex}</Tag>
              <Tag color="red">Помилок: {combatReport.failed}</Tag>
            </Space>
            <Table
              size="small"
              rowKey="teacherId"
              pagination={{ pageSize: 10 }}
              dataSource={combatReport.items}
              columns={[
                { title: 'Викладач', dataIndex: 'teacherName', width: 180, ellipsis: true },
                {
                  title: 'Як було',
                  dataIndex: 'original',
                  ellipsis: true,
                  render: (v: string) => <code style={{ fontSize: 11 }}>{v}</code>,
                },
                {
                  title: 'Як буде',
                  dataIndex: 'normalized',
                  ellipsis: true,
                  render: (v: string, r: CombatNormalizationItem) => (
                    <code style={{ fontSize: 11, color: r.status === 'FAILED' ? '#cf1322' : '#389e0d' }}>{v}</code>
                  ),
                },
                {
                  title: 'Статус',
                  dataIndex: 'status',
                  width: 140,
                  render: (s: CombatNormalizationItem['status']) => {
                    const map = {
                      OK: { color: 'green', label: 'AI' },
                      ALREADY_CANONICAL: { color: 'default', label: 'Канон.' },
                      FALLBACK_REGEX: { color: 'blue', label: 'Regex' },
                      FAILED: { color: 'red', label: 'Не вдалось' },
                    } as const;
                    return <Tag color={map[s].color}>{map[s].label}</Tag>;
                  },
                },
              ]}
            />
          </>
        )}
      </Modal>
    </Card>
  );
};

export default AiAssistantPage;
