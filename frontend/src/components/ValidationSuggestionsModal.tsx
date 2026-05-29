import React, { useMemo } from 'react';
import { Modal, Table, Tag, Progress, Button, Space, Typography, Tooltip, Alert, Collapse } from 'antd';
import { RobotOutlined, CheckCircleOutlined, CloseCircleOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { AchievementValidationSuggestion, ACHIEVEMENT_TYPE_LABELS } from '@/types/achievement';

const { Text } = Typography;

interface Props {
  open: boolean;
  suggestions: AchievementValidationSuggestion[];
  totalValidated: number;
  fulfilledCount: number;
  notFulfilledCount: number;
  sessionId?: string;
  onClose: () => void;
}

/** Група досягнень одного викладача */
interface TeacherGroup {
  teacherName: string;
  items: AchievementValidationSuggestion[];
  fulfilled: number;
  notFulfilled: number;
}

const ValidationSuggestionsModal: React.FC<Props> = ({
  open, suggestions, totalValidated, fulfilledCount, notFulfilledCount, sessionId, onClose,
}) => {
  const progressColor = (progress: number) => {
    if (progress >= 1.0) return '#52c41a';
    if (progress >= 0.6) return '#faad14';
    return '#f5222d';
  };

  const typeLabel = (type: string) =>
    ACHIEVEMENT_TYPE_LABELS[type] || type;

  // Сортуємо: спочатку невиконані, потім виконані
  const sortItems = (items: AchievementValidationSuggestion[]) =>
    [...items].sort((a, b) => {
      if (a.fulfilled === b.fulfilled) return a.progress - b.progress;
      return a.fulfilled ? 1 : -1;
    });

  // Групуємо по teacherName
  const groups = useMemo<TeacherGroup[]>(() => {
    const map = new Map<string, AchievementValidationSuggestion[]>();
    for (const s of suggestions) {
      const name = s.teacherName || 'Невідомий';
      if (!map.has(name)) map.set(name, []);
      map.get(name)!.push(s);
    }
    return Array.from(map.entries()).map(([teacherName, items]) => ({
      teacherName,
      items,
      fulfilled: items.filter(i => i.fulfilled).length,
      notFulfilled: items.filter(i => !i.fulfilled).length,
    }));
  }, [suggestions]);

  const isMultiTeacher = groups.length > 1;

  const columns: ColumnsType<AchievementValidationSuggestion> = [
    {
      title: 'Підпункт',
      key: 'type',
      width: 260,
      render: (_, record) => (
        <Tooltip title={record.descriptionPreview}>
          <Tag color="blue">{typeLabel(record.achievementType)}</Tag>
        </Tooltip>
      ),
    },
    {
      title: 'Прогрес',
      key: 'progress',
      width: 180,
      render: (_, record) => (
        <Space>
          <Progress
            percent={Math.round(record.progress * 100)}
            size="small"
            style={{ width: 80 }}
            strokeColor={progressColor(record.progress)}
          />
          <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
            {record.currentCount}/{record.requiredCount}
          </Text>
        </Space>
      ),
      sorter: (a, b) => a.progress - b.progress,
    },
    {
      title: 'Статус',
      key: 'fulfilled',
      width: 140,
      render: (_, record) => record.fulfilled
        ? <Tag color="green" icon={<CheckCircleOutlined />}>Виконано</Tag>
        : <Tag color="red" icon={<CloseCircleOutlined />}>Не виконано</Tag>,
      filters: [
        { text: 'Виконано', value: true },
        { text: 'Не виконано', value: false },
      ],
      onFilter: (value, record) => record.fulfilled === value,
    },
    {
      title: 'Обґрунтування',
      dataIndex: 'reasoning',
      key: 'reasoning',
      render: (text: string) => {
        if (!text) return <Text type="secondary">—</Text>;
        const parts = text.split(' | ').map((part, idx) => {
          const trimmed = part.trim();
          if (trimmed.startsWith('\uD83E\uDD16')) {
            return <div key={idx} style={{ color: '#1677ff', fontSize: 12 }}>{trimmed}</div>;
          }
          if (trimmed.startsWith('\u26A0\uFE0F') || trimmed.includes('\u26A0\uFE0F')) {
            return <div key={idx} style={{ color: '#faad14', fontSize: 12, fontWeight: 500 }}>{trimmed}</div>;
          }
          if (trimmed.startsWith('\uD83D\uDCA1')) {
            return <div key={idx} style={{ color: '#fa8c16', fontSize: 12, fontStyle: 'italic' }}>{trimmed}</div>;
          }
          return <div key={idx} style={{ fontSize: 12 }}>{trimmed}</div>;
        });
        return (
          <Tooltip title={text} overlayStyle={{ maxWidth: 600 }}>
            <div style={{ maxWidth: 400 }}>{parts}</div>
          </Tooltip>
        );
      },
    },
  ];

  /** Таблиця результатів для одного набору */
  const renderTable = (items: AchievementValidationSuggestion[]) => (
    <Table
      columns={columns}
      dataSource={sortItems(items)}
      rowKey="achievementId"
      size="small"
      pagination={false}
      scroll={isMultiTeacher ? undefined : { y: 450 }}
    />
  );

  /** Заголовок акордеону для одного викладача */
  const renderGroupHeader = (group: TeacherGroup) => (
    <Space>
      <UserOutlined />
      <Text strong>{group.teacherName}</Text>
      <Tag color="green">{group.fulfilled}</Tag>
      {group.notFulfilled > 0 && <Tag color="red">{group.notFulfilled}</Tag>}
      <Text type="secondary" style={{ fontSize: 12 }}>
        ({group.items.length} досягнень)
      </Text>
    </Space>
  );

  return (
    <Modal
      title={
        <Space>
          <RobotOutlined />
          <span>AI-перевірка виконання п.38</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={1100}
      footer={[
        <Text key="session" type="secondary" style={{ float: 'left', lineHeight: '32px', fontSize: 12 }}>
          Сесія: {sessionId}
        </Text>,
        <Button key="close" type="primary" onClick={onClose}>
          Закрити
        </Button>,
      ]}
    >
      <Alert
        type={notFulfilledCount > 0 ? 'warning' : 'success'}
        showIcon
        icon={<RobotOutlined />}
        message={
          <span>
            Перевірено <b>{totalValidated}</b> досягнень
            {isMultiTeacher && <> ({groups.length} викладачів)</>}.{' '}
            <Tag color="green" style={{ marginLeft: 8 }}>Виконано: {fulfilledCount}</Tag>
            {notFulfilledCount > 0 && (
              <Tag color="red">Не виконано: {notFulfilledCount}</Tag>
            )}
          </span>
        }
        style={{ marginBottom: 16 }}
      />

      {isMultiTeacher ? (
        <Collapse
          defaultActiveKey={groups.filter(g => g.notFulfilled > 0).map(g => g.teacherName)}
          style={{ maxHeight: 500, overflowY: 'auto' }}
          items={groups.map(group => ({
            key: group.teacherName,
            label: renderGroupHeader(group),
            children: renderTable(group.items),
          }))}
        />
      ) : (
        renderTable(suggestions)
      )}
    </Modal>
  );
};

export default ValidationSuggestionsModal;
