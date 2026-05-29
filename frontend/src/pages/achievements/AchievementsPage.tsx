import React, { useEffect, useState, useMemo } from 'react';
import { Table, Space, Card, message, Tag, Typography, Tooltip, Progress, Descriptions, Drawer, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { formatDate } from '@/utils/dateUtils';
import { Achievement, AchievementProgress, ACHIEVEMENT_TYPE_LABELS } from '@/types/achievement';
import {
  getAchievements,
  getAchievementProgress,
  refreshComplianceAll,
  refreshComplianceForTeacher,
} from '@/api/achievementApi';
import { useAuth } from '@/auth/AuthContext';

const { Text } = Typography;

interface Props {
  teacherId?: number;
}

/**
 * Досягнення (пункт 38) — READ-ONLY вигляд.
 * Досягнення автоматично генеруються при імпорті.
 * Залишено: перегляд таблиці, AI-валідація, історія перевірок.
 */
const AchievementsPage: React.FC<Props> = ({ teacherId: teacherIdProp }) => {
  const { user, isAdmin, isHead } = useAuth();
  const teacherId = teacherIdProp ?? user?.teacherId ?? undefined;
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<Achievement | null>(null);

  // Progress state (lightweight deterministic counts)
  const [progressData, setProgressData] = useState<AchievementProgress[]>([]);

  // Map achievementId -> progress for quick lookup
  const progressMap = useMemo(() => {
    const map = new Map<number, AchievementProgress>();
    progressData.forEach(p => map.set(p.achievementId, p));
    return map;
  }, [progressData]);

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await getAchievements(teacherId);
      setAchievements(data);
    } catch {
      message.error('Помилка завантаження досягнень');
    } finally {
      setLoading(false);
    }
  };

  const loadProgress = async () => {
    if (!teacherId) return;
    try {
      const data = await getAchievementProgress(teacherId);
      setProgressData(data);
    } catch {
      // silently fail — progress is supplementary
    }
  };

  useEffect(() => { loadData(); }, [teacherId]);

  useEffect(() => {
    if (teacherId) {
      loadProgress();
    }
  }, [teacherId]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      if (teacherId) {
        await refreshComplianceForTeacher(teacherId);
        message.success('Дані викладача перераховано');
      } else {
        const { refreshed } = await refreshComplianceAll();
        message.success(`Перераховано ${refreshed} викладачів`);
      }
      // перевантажуємо тільки те, що показуємо
      await loadData();
      if (teacherId) await loadProgress();
    } catch {
      message.error('Не вдалося перерахувати дані');
    } finally {
      setRefreshing(false);
    }
  };

  // Кнопка показується якщо:
  // - user-context = teacherId і він Admin/Head → перерахунок одного
  // - user-context = всі (немає teacherId) і user = Admin → перерахунок всіх
  const canRefresh = teacherId
    ? (isAdmin || isHead)
    : isAdmin;

  const columns: ColumnsType<Achievement> = [
    {
      title: 'Назва', dataIndex: 'title', key: 'title', ellipsis: true,
      render: (title: string, record: Achievement) => (
        <Tooltip title={record.description || title}>
          <span>{title}</span>
        </Tooltip>
      ),
    },
    {
      title: 'Тип (п.38)', dataIndex: 'achievementType', key: 'achievementType', width: 280,
      render: (type: string) => (
        <Tag color="blue">{ACHIEVEMENT_TYPE_LABELS[type] || type}</Tag>
      ),
    },
    {
      title: 'Прогрес', key: 'progress', width: 180,
      render: (_: unknown, record: Achievement) => {
        const prog = progressMap.get(record.id);
        if (!prog) {
          // No progress data loaded yet
          return <span style={{ fontSize: 12, color: '#999' }}>—</span>;
        }

        const percent = Math.round(prog.progress * 100);
        const color = prog.fulfilled ? '#52c41a' : percent > 0 ? '#faad14' : '#ff4d4f';

        return (
          <Tooltip title={prog.fulfilled ? 'Виконано' : `Потрібно: ${prog.requiredCount}`}>
            <Space size={6}>
              <Progress
                type="circle"
                size={30}
                percent={percent}
                strokeColor={color}
                format={() => prog.fulfilled ? '✓' : `${percent}%`}
              />
              <span style={{ fontSize: 13, color, fontWeight: 500 }}>
                {prog.label}
              </span>
            </Space>
          </Tooltip>
        );
      },
      sorter: (a, b) => {
        const pa = progressMap.get(a.id);
        const pb = progressMap.get(b.id);
        return (pa?.progress ?? 0) - (pb?.progress ?? 0);
      },
    },
  ];

  return (
    <Card
      title="Досягнення (пункт 38)"
      extra={
        <Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Досягнення формуються автоматично
          </Text>
          {canRefresh && (
            <Tooltip title={teacherId
              ? 'Перерахувати compliance для цього викладача'
              : 'Перерахувати compliance для ВСІХ викладачів (може зайняти час)'}
            >
              <Button
                size="small"
                icon={<ReloadOutlined />}
                loading={refreshing}
                onClick={handleRefresh}
              >
                Перерахувати
              </Button>
            </Tooltip>
          )}
        </Space>
      }
    >
      <Table
        columns={columns}
        dataSource={achievements}
        rowKey="id"
        loading={loading}
        expandable={{
          expandedRowRender: (record) => {
            const prog = progressMap.get(record.id);
            if (!prog?.reasoning) {
              return <Text type="secondary">Деталі підрахунку відсутні</Text>;
            }
            return (
              <pre style={{
                whiteSpace: 'pre-wrap',
                fontFamily: 'inherit',
                fontSize: 12,
                lineHeight: 1.6,
                background: '#fafafa',
                padding: 12,
                borderRadius: 4,
                margin: 0,
                color: '#262626',
              }}>
                {prog.reasoning}
              </pre>
            );
          },
          rowExpandable: (record) => !!progressMap.get(record.id)?.reasoning,
        }}
        onRow={(record) => ({
          onClick: (e) => {
            // Не відкривати Drawer при кліці на expand-стрілку чи кнопки
            const target = e.target as HTMLElement;
            if (target.closest('.ant-btn, .ant-popconfirm, .ant-table-row-expand-icon')) return;
            setSelectedRecord(record);
            setDrawerOpen(true);
          },
          style: { cursor: 'pointer' },
        })}
      />

      <Drawer
        title={selectedRecord?.title || 'Деталі досягнення'}
        placement="right"
        width={480}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setSelectedRecord(null); }}
      >
        {selectedRecord && (() => {
          const prog = progressMap.get(selectedRecord.id);
          return (
            <>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="Назва">{selectedRecord.title}</Descriptions.Item>
                <Descriptions.Item label="Тип (п.38)">
                  <Tag color="blue">{ACHIEVEMENT_TYPE_LABELS[selectedRecord.achievementType] || selectedRecord.achievementType}</Tag>
                </Descriptions.Item>
                {selectedRecord.dateAchieved && (
                  <Descriptions.Item label="Дата">{formatDate(selectedRecord.dateAchieved)}</Descriptions.Item>
                )}
                {prog && (
                  <Descriptions.Item label="Прогрес">
                    {prog.label} ({Math.round(prog.progress * 100)}%)
                    {prog.fulfilled && ' ✓'}
                  </Descriptions.Item>
                )}
                {selectedRecord.description && (
                  <Descriptions.Item label="Опис">{selectedRecord.description}</Descriptions.Item>
                )}
                {selectedRecord.notes && (
                  <Descriptions.Item label="Примітки">{selectedRecord.notes}</Descriptions.Item>
                )}
                {selectedRecord.documentUrl && (
                  <Descriptions.Item label="Документ">
                    <a href={selectedRecord.documentUrl} target="_blank" rel="noopener noreferrer">
                      {selectedRecord.documentUrl.length > 50 ? selectedRecord.documentUrl.substring(0, 50) + '...' : selectedRecord.documentUrl}
                    </a>
                  </Descriptions.Item>
                )}
              </Descriptions>

              {prog?.reasoning && (
                <div style={{ marginTop: 16 }}>
                  <Text strong>Обґрунтування підрахунку</Text>
                  <pre style={{
                    whiteSpace: 'pre-wrap',
                    fontFamily: 'inherit',
                    fontSize: 12,
                    lineHeight: 1.6,
                    background: '#fafafa',
                    padding: 12,
                    borderRadius: 4,
                    marginTop: 8,
                    color: '#262626',
                  }}>
                    {prog.reasoning}
                  </pre>
                </div>
              )}
            </>
          );
        })()}
      </Drawer>
    </Card>
  );
};

export default AchievementsPage;
