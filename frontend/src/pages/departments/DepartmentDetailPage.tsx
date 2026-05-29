import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Row, Col, Statistic, Table, Typography, Progress, Button, Tag, Space, Spin, Tooltip, message, Empty } from 'antd';
import {
  ArrowLeftOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CrownOutlined,
  ExclamationCircleOutlined,
  UserOutlined,
  CalendarOutlined,
  ShopOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend, ResponsiveContainer,
  Tooltip as RechartsTooltip,
} from 'recharts';
import { getDepartmentComplianceSummary } from '@/api/departmentApi';
import ComplianceBadge from '@/components/ComplianceBadge';
import { DepartmentComplianceSummary, TeacherDegreeBrief, TeacherTitleBrief, Point37TeacherBrief } from '@/types/teacher';
import { ComplianceReport, ComplianceStatus } from '@/types/achievement';
import { rankOfPosition } from '@/utils/positionSeniority';

const { Title, Text } = Typography;

/** Рендерить tooltip-контент для картки зі ступенями. */
const degreeTooltip = (teachers?: TeacherDegreeBrief[]) => {
  if (!teachers || teachers.length === 0) return 'Немає викладачів';
  return (
    <div style={{ maxWidth: 360 }}>
      {teachers.map((t) => (
        <div key={t.teacherId} style={{ marginBottom: 4 }}>
          <strong>{t.fullName}</strong>
          {t.degreeName && <div style={{ fontSize: 12 }}>{t.degreeName}</div>}
          {t.speciality && <div style={{ fontSize: 12, opacity: 0.85 }}>{t.speciality}</div>}
        </div>
      ))}
    </div>
  );
};

/** Рендерить tooltip-контент для картки зі званнями. */
const titleTooltip = (teachers?: TeacherTitleBrief[]) => {
  if (!teachers || teachers.length === 0) return 'Немає викладачів';
  return (
    <div style={{ maxWidth: 360 }}>
      {teachers.map((t) => (
        <div key={t.teacherId} style={{ marginBottom: 4 }}>
          <strong>{t.fullName}</strong>
          {t.titleName && <div style={{ fontSize: 12 }}>{t.titleName}</div>}
        </div>
      ))}
    </div>
  );
};

/** Рендерить tooltip зі списком ПІБ (без додаткової деталізації). */
const nameListTooltip = (names?: string[]) => {
  if (!names || names.length === 0) return 'Немає викладачів';
  return (
    <div style={{ maxWidth: 320 }}>
      {names.map((n, i) => <div key={i}>{n}</div>)}
    </div>
  );
};

const DepartmentDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [summary, setSummary] = useState<DepartmentComplianceSummary | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getDepartmentComplianceSummary(Number(id))
      .then(setSummary)
      .catch(() => message.error('Помилка завантаження даних кафедри'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!summary) {
    return <Text type="secondary">Кафедру не знайдено</Text>;
  }

  const defendedCount = summary.defendedInSpecialtyCount ?? 0;
  const defendedReq = summary.defendedInSpecialtyRequirement ?? 3;
  const defendedPercent = Math.min(100, Math.round((defendedCount / defendedReq) * 100));
  const defendedColor = summary.defendedInSpecialtyCompliant ? '#52c41a' : '#f5222d';

  const columns: ColumnsType<ComplianceReport> = [
    {
      title: 'Посада',
      dataIndex: 'position',
      key: 'position',
      width: 240,
      sorter: (a, b) => rankOfPosition(a.position) - rankOfPosition(b.position),
      render: (pos: string | undefined, record) => {
        const display = pos || '—';
        if (record.bootstrappedPosition) {
          return (
            <Tooltip title="Посаду створено автоматично з картки викладача. Перевірте у розділі «Штат».">
              <span>
                <ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 6 }} />
                {display}
              </span>
            </Tooltip>
          );
        }
        return display;
      },
    },
    {
      title: 'Звання',
      dataIndex: 'militaryRank',
      key: 'militaryRank',
      width: 150,
      render: (rank: string | undefined) => rank || '—',
    },
    {
      title: 'ПІБ',
      dataIndex: 'teacherName',
      key: 'teacherName',
      sorter: (a, b) => a.teacherName.localeCompare(b.teacherName),
      render: (name: string, record) => {
        const link = <a onClick={() => navigate(`/teachers/${record.teacherId}`)}>{name}</a>;
        if (record.employmentType === 'PART_TIME') {
          return (
            <Space size={6}>
              {link}
              <Tag color="orange">Сумісник</Tag>
            </Space>
          );
        }
        return link;
      },
    },
    {
      title: 'п.38 Статус',
      dataIndex: 'status',
      key: 'status',
      width: 180,
      filters: [
        { text: 'Відповідає', value: 'COMPLIANT' },
        { text: 'Попередження', value: 'WARNING' },
        { text: 'Не відповідає', value: 'NON_COMPLIANT' },
        { text: 'Звільнений', value: 'EXEMPT' },
      ],
      onFilter: (value, record) => record.status === value,
      render: (status: ComplianceStatus) => <ComplianceBadge status={status} />,
    },
    {
      title: 'Досягнень',
      dataIndex: 'uniqueTypeCount',
      key: 'uniqueTypeCount',
      width: 120,
      sorter: (a, b) => a.uniqueTypeCount - b.uniqueTypeCount,
      render: (count: number) => (
        <Tag color={count >= 4 ? 'green' : count >= 3 ? 'orange' : 'red'}>
          {count} / 4
        </Tag>
      ),
    },
  ];

  // ── Колір значення картки залежно від кількості ──
  const valueColor = (count: number, accent: string) => count > 0 ? accent : '#bfbfbf';

  /**
   * Рендер одного ряду статистичних карток (MAIN або PART_TIME).
   */
  const renderEmploymentRow = (
    titleSuffix: string,
    totalTeachers: number | undefined,
    allTeachers: TeacherDegreeBrief[] | undefined,
    phdTeachers: TeacherDegreeBrief[] | undefined,
    candidateTeachers: TeacherDegreeBrief[] | undefined,
    doctorOfScienceTeachers: TeacherDegreeBrief[] | undefined,
    docentTeachers: TeacherTitleBrief[] | undefined,
    snsTeachers: TeacherTitleBrief[] | undefined,
    sndTeachers: TeacherTitleBrief[] | undefined,
    professorTeachers: TeacherTitleBrief[] | undefined,
    point38Count: number | undefined,
    point38Teachers: string[] | undefined,
  ) => {
    const phd = phdTeachers?.length ?? 0;
    const cand = candidateTeachers?.length ?? 0;
    const dsc = doctorOfScienceTeachers?.length ?? 0;
    const doc = docentTeachers?.length ?? 0;
    const sns = snsTeachers?.length ?? 0;
    const snd = sndTeachers?.length ?? 0;
    const prof = professorTeachers?.length ?? 0;
    const total = totalTeachers ?? 0;
    const p38 = point38Count ?? 0;

    return (
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={degreeTooltip(allTeachers)} placement="bottom">
            <Card hoverable>
              <Statistic
                title={`Всього викладачів ${titleSuffix}`}
                value={total}
                prefix={<TeamOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={
            <div>
              <div style={{ marginBottom: 6, fontWeight: 600 }}>Доктор філософії ({phd}):</div>
              {degreeTooltip(phdTeachers)}
              <div style={{ margin: '10px 0 6px', fontWeight: 600 }}>Кандидат наук ({cand}):</div>
              {degreeTooltip(candidateTeachers)}
            </div>
          } placement="bottom">
            <Card hoverable>
              <Statistic
                title="Доктор філософії / к.н."
                value={`${phd} / ${cand}`}
                valueStyle={{ color: valueColor(phd + cand, '#1890ff') }}
                prefix={<CrownOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={degreeTooltip(doctorOfScienceTeachers)} placement="bottom">
            <Card hoverable>
              <Statistic
                title="Доктор наук"
                value={dsc}
                valueStyle={{ color: valueColor(dsc, '#722ed1') }}
                prefix={<CrownOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={
            <div>
              <div style={{ marginBottom: 6, fontWeight: 600 }}>Доцент ({doc}):</div>
              {titleTooltip(docentTeachers)}
              <div style={{ margin: '10px 0 6px', fontWeight: 600 }}>СНС ({sns}):</div>
              {titleTooltip(snsTeachers)}
              <div style={{ margin: '10px 0 6px', fontWeight: 600 }}>СНД ({snd}):</div>
              {titleTooltip(sndTeachers)}
            </div>
          } placement="bottom">
            <Card hoverable>
              <Statistic
                title="Доцент / СНС / СНД"
                value={`${doc} / ${sns} / ${snd}`}
                valueStyle={{ color: valueColor(doc + sns + snd, '#13c2c2') }}
                prefix={<CrownOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={titleTooltip(professorTeachers)} placement="bottom">
            <Card hoverable>
              <Statistic
                title="Професор (звання)"
                value={prof}
                valueStyle={{ color: valueColor(prof, '#eb2f96') }}
                prefix={<CrownOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={nameListTooltip(point38Teachers)} placement="bottom">
            <Card hoverable>
              <Statistic
                title="п.38 Відповідають"
                value={p38}
                valueStyle={{ color: valueColor(p38, '#3f8600') }}
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
      </Row>
    );
  };

  // ── Графік публікацій ──
  const pubData = (summary.publicationsByYear ?? []).map(b => ({
    year: String(b.year),
    Scopus: b.scopus,
    WoS: b.wos,
    'Кат. А': b.categoryA,
    'Кат. Б': b.categoryB,
  }));
  const hasAnyPublications = pubData.some(
    d => d.Scopus + d.WoS + d['Кат. А'] + d['Кат. Б'] > 0,
  );

  // ── п.37 tooltip helpers ──
  const checkIcon = (ok: boolean) => ok
    ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
    : <CloseCircleOutlined style={{ color: '#f5222d' }} />;

  /** Tooltip-блок з деталізацією п.37 по всім MAIN-викладачам. */
  const point37Tooltip = (teachers?: Point37TeacherBrief[]) => {
    if (!teachers || teachers.length === 0) return 'Немає MAIN-викладачів';
    // Сортуємо: спочатку compliant, потім ні
    const sorted = [...teachers].sort((a, b) =>
      (b.point37Compliant ? 1 : 0) - (a.point37Compliant ? 1 : 0));
    return (
      <div style={{ maxWidth: 460 }}>
        <div style={{ fontSize: 11, opacity: 0.85, marginBottom: 6 }}>
          А1 — диплом · А2 — ступінь · А3 — пп.20 · А4 — пп.6 · Б — пп.1
        </div>
        {sorted.map((t) => (
          <div key={t.teacherId} style={{
            marginBottom: 4,
            paddingBottom: 4,
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {t.point37Compliant
                ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
                : <CloseCircleOutlined style={{ color: '#f5222d' }} />}
              <strong>{t.fullName}</strong>
            </div>
            <div style={{ fontSize: 12, opacity: 0.9, marginTop: 2 }}>
              <Space size={8}>
                <span>{checkIcon(t.a1Diploma)} А1</span>
                <span>{checkIcon(t.a2Degree)} А2</span>
                <span>{checkIcon(t.a3Practical)} А3</span>
                <span>{checkIcon(t.a4Supervision)} А4</span>
                <span style={{ marginLeft: 6 }}>{checkIcon(t.blockB)} Б</span>
              </Space>
            </div>
          </div>
        ))}
      </div>
    );
  };

  const mainTotal = summary.mainTotalTeachers ?? 0;
  const point37Count = summary.point37CompliantCount ?? 0;

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/departments')}>
          Назад
        </Button>
      </Space>

      <Title level={4} style={{ marginBottom: 4 }}>
        {summary.departmentName}
      </Title>
      {summary.facultyName && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
          {summary.facultyName}
        </Text>
      )}

      {/* ════════════════════════════════════ */}
      {/* РЯД 1 — Основне місце роботи (MAIN) */}
      {/* ════════════════════════════════════ */}
      <div style={{ marginBottom: 8 }}>
        <Text strong>Основне місце роботи</Text>
      </div>
      {renderEmploymentRow(
        '(основне)',
        summary.mainTotalTeachers,
        summary.mainAllTeachers,
        summary.mainPhdTeachers,
        summary.mainCandidateTeachers,
        summary.mainDoctorOfScienceTeachers,
        summary.mainDocentTeachers,
        summary.mainSnsTeachers,
        summary.mainSndTeachers,
        summary.mainProfessorTitleTeachers,
        summary.mainPoint38CompliantCount,
        summary.mainPoint38CompliantTeachers,
      )}

      {/* ════════════════════════════════════ */}
      {/* РЯД 2 — Сумісники (PART_TIME)       */}
      {/* ════════════════════════════════════ */}
      <div style={{ marginBottom: 8 }}>
        <Text strong>Сумісники</Text>
      </div>
      {renderEmploymentRow(
        '(сумісники)',
        summary.partTimeTotalTeachers,
        summary.partTimeAllTeachers,
        summary.partTimePhdTeachers,
        summary.partTimeCandidateTeachers,
        summary.partTimeDoctorOfScienceTeachers,
        summary.partTimeDocentTeachers,
        summary.partTimeSnsTeachers,
        summary.partTimeSndTeachers,
        summary.partTimeProfessorTitleTeachers,
        summary.partTimePoint38CompliantCount,
        summary.partTimePoint38CompliantTeachers,
      )}

      {/* ════════════════════════════════════ */}
      {/* РЯД 3 — Додаткові метрики          */}
      {/* ════════════════════════════════════ */}
      <div style={{ marginBottom: 8 }}>
        <Text strong>Додаткова статистика</Text>
      </div>
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={
            <div>
              <div style={{ marginBottom: 6, fontWeight: 600 }}>
                Військові ({summary.militaryCount ?? 0}):
              </div>
              {nameListTooltip(summary.militaryTeachers)}
              <div style={{ margin: '10px 0 6px', fontWeight: 600 }}>
                Цивільні / Працівники ЗСУ ({summary.civilianCount ?? 0}):
              </div>
              {nameListTooltip(summary.civilianTeachers)}
            </div>
          } placement="bottom">
            <Card hoverable>
              <Statistic
                title="Військові / цивільні"
                value={`${summary.militaryCount ?? 0} / ${summary.civilianCount ?? 0}`}
                valueStyle={{ color: '#fa541c' }}
                prefix={<UserOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Card>
            <Statistic
              title="Середній вік"
              value={summary.averageAgeYears ?? '—'}
              suffix={summary.averageAgeYears ? 'років' : ''}
              valueStyle={{ color: '#13c2c2' }}
              prefix={<CalendarOutlined />}
              precision={summary.averageAgeYears ? 1 : undefined}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Card>
            <Statistic
              title="Вакантні посади"
              value={summary.vacantPositionsCount ?? 0}
              valueStyle={{ color: valueColor(summary.vacantPositionsCount ?? 0, '#faad14') }}
              prefix={<ShopOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip
            title={summary.departmentRatingTotalScore != null
              ? `Сумарні бали кафедри: ${summary.departmentRatingTotalScore}`
              : 'Активний рейтинговий період відсутній або кафедра не присутня у рейтингу'}
            placement="bottom"
          >
            <Card hoverable>
              <Statistic
                title="Місце в рейтингу"
                value={summary.departmentRatingRank != null
                  ? `${summary.departmentRatingRank} / ${summary.departmentRatingTotalDepts ?? '?'}`
                  : '—'}
                valueStyle={{ color: '#722ed1' }}
                prefix={<TrophyOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip
            title={summary.staffingPercent != null
              ? `Зайнято ${summary.occupiedStaffRate ?? 0} зі ${summary.totalStaffRate ?? 0} ставок`
              : 'Штатного розпису для кафедри ще немає'}
            placement="bottom"
          >
            <Card hoverable>
              <Statistic
                title="Укомплектованість"
                value={summary.staffingPercent != null ? summary.staffingPercent : '—'}
                suffix={summary.staffingPercent != null ? '%' : ''}
                precision={summary.staffingPercent != null ? 1 : undefined}
                valueStyle={{
                  color: summary.staffingPercent == null
                    ? '#bfbfbf'
                    : summary.staffingPercent >= 90 ? '#52c41a'
                    : summary.staffingPercent >= 75 ? '#faad14'
                    : '#f5222d',
                }}
                prefix={<ShopOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Tooltip title={point37Tooltip(summary.point37TeachersDetail)} placement="bottomLeft">
            <Card hoverable>
              <Statistic
                title="Відповідність п.37"
                value={mainTotal > 0 ? `${point37Count} / ${mainTotal}` : '—'}
                valueStyle={{
                  color: mainTotal === 0
                    ? '#bfbfbf'
                    : point37Count === mainTotal ? '#52c41a'
                    : point37Count >= Math.ceil(mainTotal / 2) ? '#faad14'
                    : '#f5222d',
                }}
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Tooltip>
        </Col>
      </Row>

      {/* Дві половинки: Закон про ВО + Графік публікацій */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={12}>
          <Card style={{ height: '100%' }}>
            <Row gutter={24} align="middle">
              <Col>
                <Progress
                  type="dashboard"
                  percent={defendedPercent}
                  strokeColor={defendedColor}
                  format={() => `${defendedCount} / ${defendedReq}`}
                  size={120}
                />
              </Col>
              <Col flex="auto">
                <Title level={5} style={{ marginTop: 0 }}>
                  Кваліфікація за напрямом кафедри (Закон «Про вищу освіту»)
                </Title>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Вимога: щонайменше {defendedReq} особи з науковим <b>ступенем</b> за напрямом
                  кафедри <b>АБО</b> з вченим <b>званням</b> за напрямом кафедри. Зіставлення
                  виконує AI: для ступеня — за темою/спеціальністю дисертації, для звання —
                  за кафедрою/спеціальністю, на якій його присвоєно.
                </Text>
                <div style={{ marginTop: 12 }}>
                  {summary.defendedInSpecialtyCompliant ? (
                    <Tag color="green" icon={<CheckCircleOutlined />}>
                      Виконано ({defendedCount} з {defendedReq})
                    </Tag>
                  ) : (
                    <Tag color="red" icon={<CloseCircleOutlined />}>
                      Не виконано ({defendedCount} з {defendedReq})
                    </Tag>
                  )}
                </div>
                {summary.defendedInSpecialtyTeachers && summary.defendedInSpecialtyTeachers.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Зараховано: {summary.defendedInSpecialtyTeachers.join(', ')}
                    </Text>
                  </div>
                )}
              </Col>
            </Row>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Публікації за 5 років (накопичувально)" size="small" style={{ height: '100%' }}>
            {hasAnyPublications ? (
              <ResponsiveContainer width="100%" height={210}>
                <BarChart data={pubData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="year" />
                  <YAxis allowDecimals={false} />
                  <RechartsTooltip />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="Scopus" stackId="a" fill="#722ed1" />
                  <Bar dataKey="WoS" stackId="a" fill="#1890ff" />
                  <Bar dataKey="Кат. А" stackId="a" fill="#52c41a" />
                  <Bar dataKey="Кат. Б" stackId="a" fill="#faad14" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Немає публікацій за 5 років"
                style={{ padding: '24px 0' }}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* Teacher compliance table */}
      <Card title="Викладачі кафедри — відповідність п.38">
        <Table
          dataSource={summary.teacherReports}
          columns={columns}
          rowKey="teacherId"
          locale={{ emptyText: 'Немає викладачів' }}
          pagination={false}
          size="small"
        />
      </Card>
    </div>
  );
};

export default DepartmentDetailPage;
