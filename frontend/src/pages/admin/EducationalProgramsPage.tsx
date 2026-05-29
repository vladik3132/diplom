import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Card, Table, Button, Modal, Form, Input, InputNumber, Select, Dropdown,
  Tag, Space, Popconfirm, Typography, message, Tooltip,
  Radio, Empty, Breadcrumb, Collapse, Switch,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ArrowLeftOutlined, UserOutlined, CheckCircleOutlined, CloseCircleOutlined, MinusCircleOutlined, DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useSearchParams } from 'react-router-dom';

import type { EducationalProgram, ProgramStaffStats, DisciplineStaffingDto } from '@/types/educationalProgram';
import {
  getEducationalPrograms, createEducationalProgram,
  updateEducationalProgram, deleteEducationalProgram,
  getProgramStaffStats, getDisciplineStaffing,
} from '@/api/educationalProgramApi';
import { getDepartments } from '@/api/departmentApi';
import { getDisciplines, getDisciplineTeachers, assignTeacherDiscipline, removeTeacherDiscipline } from '@/api/disciplineApi';
import { getTeachers } from '@/api/teacherApi';
import { getComplianceAll } from '@/api/achievementApi';
import type { Department, Teacher } from '@/types/teacher';
import type { Discipline, TeacherDiscipline } from '@/types/discipline';
import type { ComplianceReport } from '@/types/achievement';
import { getMappings, exportByProgram } from '@/api/docxExportApi';
import type { DocxExportTemplate } from '@/types/docxExport';
import { useAuth } from '@/auth/AuthContext';
import ProgramWorkingGroupSection from '@/components/ProgramWorkingGroupSection';

const { Title, Text } = Typography;

const EducationalProgramsPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isHead = user?.role === 'HEAD_OF_DEPARTMENT';
  const canAssign = isAdmin || isHead;

  const [searchParams, setSearchParams] = useSearchParams();
  const drillProgramId = searchParams.get('programId') ? Number(searchParams.get('programId')) : null;

  const [programs, setPrograms] = useState<EducationalProgram[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedDegree, setSelectedDegree] = useState<string>('бакалавр');
  const [selectedForm, setSelectedForm] = useState<string>('денна');
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EducationalProgram | null>(null);
  const [form] = Form.useForm();

  // Drill-down: disciplines for selected program
  const [drillDisciplines, setDrillDisciplines] = useState<Discipline[]>([]);
  const [drillLoading, setDrillLoading] = useState(false);

  // Teacher assignment
  const [teacherModalOpen, setTeacherModalOpen] = useState(false);
  const [teacherModalDiscipline, setTeacherModalDiscipline] = useState<Discipline | null>(null);
  const [assignedTeachers, setAssignedTeachers] = useState<TeacherDiscipline[]>([]);
  const [deptTeachers, setDeptTeachers] = useState<Teacher[]>([]);
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([]);
  const [allTeachersLoading, setAllTeachersLoading] = useState(false);
  const [allowAllTeachers, setAllowAllTeachers] = useState(false);
  const [teacherAssigning, setTeacherAssigning] = useState(false);
  // Map: disciplineId → assigned teachers (for inline display)
  const [teachersByDiscipline, setTeachersByDiscipline] = useState<Record<number, TeacherDiscipline[]>>({});
  // Map: teacherId → compliance report (for modal display)
  const [complianceMap, setComplianceMap] = useState<Record<number, ComplianceReport>>({});
  // Staff stats for drilled-down program
  const [staffStats, setStaffStats] = useState<ProgramStaffStats | null>(null);
  // Discipline staffing (п.36/п.37) map: disciplineId → DisciplineStaffingDto
  const [disciplineStaffing, setDisciplineStaffing] = useState<Record<number, DisciplineStaffingDto>>({});
  // Staff stats for each program in list view
  const [programStatsMap, setProgramStatsMap] = useState<Record<number, ProgramStaffStats>>({});
  // Export templates
  const [exportTemplates, setExportTemplates] = useState<DocxExportTemplate[]>([]);
  const [exporting, setExporting] = useState(false);

  const loadPrograms = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getEducationalPrograms();
      setPrograms(data);
      // Load staff stats for all programs in parallel
      const statsEntries = await Promise.all(
        data.map(async (p) => {
          try {
            const stats = await getProgramStaffStats(p.id);
            return [p.id, stats] as [number, ProgramStaffStats];
          } catch {
            return null;
          }
        })
      );
      const map: Record<number, ProgramStaffStats> = {};
      statsEntries.forEach((e) => { if (e) map[e[0]] = e[1]; });
      setProgramStatsMap(map);
    } catch {
      message.error('Помилка завантаження програм');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDepartments = useCallback(async () => {
    try {
      const data = await getDepartments();
      setDepartments(data);
    } catch { /* ignore */ }
  }, []);

  const loadExportTemplates = useCallback(async () => {
    try {
      const data = await getMappings();
      setExportTemplates(data);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    loadPrograms();
    loadDepartments();
    if (canAssign) loadExportTemplates();
  }, [loadPrograms, loadDepartments, loadExportTemplates, canAssign]);

  // Derive drillProgram from URL param + loaded programs
  const drillProgram = useMemo(
    () => (drillProgramId ? programs.find((p) => p.id === drillProgramId) || null : null),
    [drillProgramId, programs],
  );

  // Load disciplines + their teacher assignments when drill-down program changes
  useEffect(() => {
    if (!drillProgramId) {
      setDrillDisciplines([]);
      setTeachersByDiscipline({});
      setStaffStats(null);
      setDisciplineStaffing({});
      return;
    }
    setDrillLoading(true);
    // Load staff stats and discipline staffing in parallel with disciplines
    getProgramStaffStats(drillProgramId)
      .then(setStaffStats)
      .catch(() => setStaffStats(null));
    getDisciplineStaffing(drillProgramId)
      .then(setDisciplineStaffing)
      .catch(() => setDisciplineStaffing({}));
    getDisciplines({ programId: drillProgramId })
      .then(async (disciplines) => {
        setDrillDisciplines(disciplines);
        // Load teachers for all disciplines in parallel
        const entries = await Promise.all(
          disciplines.map(async (d) => {
            try {
              const tds = await getDisciplineTeachers(d.id);
              return [d.id, tds] as [number, TeacherDiscipline[]];
            } catch {
              return [d.id, []] as [number, TeacherDiscipline[]];
            }
          })
        );
        setTeachersByDiscipline(Object.fromEntries(entries));
      })
      .catch(() => {
        message.error('Помилка завантаження дисциплін');
        setDrillDisciplines([]);
      })
      .finally(() => setDrillLoading(false));
  }, [drillProgramId]);

  const openDrill = useCallback((program: EducationalProgram) => {
    setSearchParams({ programId: String(program.id) });
  }, [setSearchParams]);

  const closeDrill = useCallback(() => {
    setSearchParams({});
  }, [setSearchParams]);

  // ─── Teacher assignment ───
  const openTeacherModal = useCallback(async (discipline: Discipline) => {
    setTeacherModalDiscipline(discipline);
    setTeacherModalOpen(true);
    setAllowAllTeachers(false);   // завжди починаємо з вузького списку (виклaдачі кафедри)
    try {
      const deptId = discipline.department?.id;
      const [assigned, teachers, compliance] = await Promise.all([
        getDisciplineTeachers(discipline.id),
        deptId ? getTeachers({ departmentId: deptId }) : Promise.resolve([]),
        deptId ? getComplianceAll(deptId) : Promise.resolve([]),
      ]);
      setAssignedTeachers(assigned);
      setDeptTeachers(teachers);
      const cMap: Record<number, ComplianceReport> = {};
      compliance.forEach((c) => { cMap[c.teacherId] = c; });
      setComplianceMap(cMap);
    } catch {
      message.error('Помилка завантаження викладачів');
    }
  }, []);

  /**
   * Lazy-load: усі викладачі системи + їх compliance.
   * Викликається коли користувач вмикає switch "З усіх кафедр" вперше.
   */
  const loadAllTeachers = useCallback(async () => {
    if (allTeachers.length > 0) return;   // вже завантажено
    setAllTeachersLoading(true);
    try {
      const [teachers, compliance] = await Promise.all([
        getTeachers(),                          // без departmentId — всі
        getComplianceAll(),                     // compliance для всіх
      ]);
      setAllTeachers(teachers);
      // Доповнюємо complianceMap (existing + new) — щоб optionRender знаходив compliance
      // як для викладачів кафедри, так і для всіх інших.
      setComplianceMap((prev) => {
        const merged = { ...prev };
        compliance.forEach((c) => { merged[c.teacherId] = c; });
        return merged;
      });
    } catch {
      message.error('Помилка завантаження повного списку викладачів');
      setAllowAllTeachers(false);   // повертаємо в OFF при помилці
    } finally {
      setAllTeachersLoading(false);
    }
  }, [allTeachers.length]);

  const handleAllowAllTeachersChange = (checked: boolean) => {
    setAllowAllTeachers(checked);
    if (checked) loadAllTeachers();
  };

  const refreshDisciplineTeachers = async (disciplineId: number) => {
    const updated = await getDisciplineTeachers(disciplineId);
    setAssignedTeachers(updated);
    setTeachersByDiscipline((prev) => ({ ...prev, [disciplineId]: updated }));
    // Refresh staff stats and staffing
    if (drillProgramId) {
      getProgramStaffStats(drillProgramId).then(setStaffStats).catch(() => {});
      getDisciplineStaffing(drillProgramId).then(setDisciplineStaffing).catch(() => {});
    }
  };

  const handleAssignTeacher = async (teacherId: number) => {
    if (!teacherModalDiscipline) return;
    setTeacherAssigning(true);
    try {
      await assignTeacherDiscipline({
        teacher: { id: teacherId } as any,
        discipline: { id: teacherModalDiscipline.id } as any,
      });
      await refreshDisciplineTeachers(teacherModalDiscipline.id);
      message.success('Викладача призначено');
    } catch {
      message.error('Помилка призначення');
    } finally {
      setTeacherAssigning(false);
    }
  };

  const handleRemoveTeacher = async (tdId: number) => {
    if (!teacherModalDiscipline) return;
    try {
      await removeTeacherDiscipline(tdId);
      await refreshDisciplineTeachers(teacherModalDiscipline.id);
      message.success('Призначення знято');
    } catch {
      message.error('Помилка видалення');
    }
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: EducationalProgram) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      departmentId: record.department?.id,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      if (editing) {
        await updateEducationalProgram(editing.id, values);
        message.success('Програму оновлено');
      } else {
        await createEducationalProgram(values);
        message.success('Програму створено');
      }
      setModalOpen(false);
      form.resetFields();
      loadPrograms();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error('Помилка збереження');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteEducationalProgram(id);
      message.success('Програму видалено');
      loadPrograms();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const handleExportByProgram = async (templateId: number, programId: number) => {
    setExporting(true);
    try {
      await exportByProgram(templateId, programId);
      message.success('Документ згенеровано та завантажено');
    } catch (e: any) {
      const errorMsg = e?.response?.data instanceof Blob
        ? await e.response.data.text()
        : e?.response?.data?.message || e?.message || 'Помилка експорту';
      message.error(errorMsg);
    } finally {
      setExporting(false);
    }
  };

  // Filter by degree + form, then get available years
  const programsByDegreeAndForm = programs.filter(
    (p) =>
      (p.degree || '').toLowerCase() === selectedDegree.toLowerCase() &&
      (p.educationForm || '').toLowerCase().includes(selectedForm.toLowerCase())
  );

  const availableYears = [...new Set(programsByDegreeAndForm.map((p) => p.enrollmentYear).filter(Boolean) as number[])]
    .sort((a, b) => b - a);

  // Auto-select first year if current is not available
  React.useEffect(() => {
    if (availableYears.length > 0 && (selectedYear === null || !availableYears.includes(selectedYear))) {
      setSelectedYear(availableYears[0]);
    }
  }, [availableYears.join(',')]); // eslint-disable-line react-hooks/exhaustive-deps

  const filteredPrograms = programsByDegreeAndForm.filter(
    (p) => selectedYear === null || p.enrollmentYear === selectedYear
  );

  const deptLabel = (d?: { id: number; number?: string; name: string }) =>
    d ? (d.number ? `${d.number} — ${d.name}` : d.name) : '—';

  const columns: ColumnsType<EducationalProgram> = [
    {
      title: 'Скор.', dataIndex: 'shortCode', key: 'shortCode', width: 120,
      render: (v: string) => v ? <Tag color="geekblue">{v}</Tag> : '—',
    },
    {
      title: 'Назва', dataIndex: 'name', key: 'name', ellipsis: true, width: 220,
      render: (v: string, record) => (
        <a onClick={(e) => { e.stopPropagation(); openDrill(record); }}>{v}</a>
      ),
    },
    { title: 'Спеціальність', dataIndex: 'specialty', key: 'specialty', ellipsis: true, width: 180, render: (v: string) => v || '—' },
    { title: 'Спеціалізація', dataIndex: 'specialization', key: 'specialization', ellipsis: true, render: (v: string) => v || '—' },
    {
      title: 'Кафедра', key: 'department', width: 250, ellipsis: true,
      render: (_, record) => record.department
        ? <span title={deptLabel(record.department)}>{deptLabel(record.department)}</span>
        : '—',
    },
    {
      title: 'Статус', key: 'status', width: 80, align: 'center' as const,
      render: (_: unknown, record: EducationalProgram) => {
        const stats = programStatsMap[record.id];
        if (!stats) return <Tag>—</Tag>;
        const checks = [
          stats.point35Compliant,
          stats.point35cCompliant,
          stats.point36Compliant,
        ];
        const passed = checks.filter(Boolean).length;
        const color = passed === 3 ? 'success' : passed >= 2 ? 'warning' : 'error';
        const icon = passed === 3 ? '✓' : passed >= 2 ? '⚠' : '✗';
        const tooltip = [
          `п.35 (≥50%): ${stats.point35Compliant ? '✓' : '✗'} (${stats.mainWithDegreePercent}%)`,
          `п.35с (≥3 осіб): ${stats.point35cCompliant ? '✓' : '✗'} (${stats.qualifiedMainCount})`,
          `п.36+п.37 (компоненти): ${stats.point36Compliant ? '✓' : '✗'} (${stats.disciplinesFullyStaffed}/${stats.disciplinesTotal})`,
        ].join('\n');
        return (
          <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{tooltip}</span>}>
            <Tag color={color}>{icon} {passed}/3</Tag>
          </Tooltip>
        );
      },
      sorter: (a: EducationalProgram, b: EducationalProgram) => {
        const sa = programStatsMap[a.id];
        const sb = programStatsMap[b.id];
        const ca = sa ? [sa.point35Compliant, sa.point35cCompliant, sa.point36Compliant].filter(Boolean).length : -1;
        const cb = sb ? [sb.point35Compliant, sb.point35cCompliant, sb.point36Compliant].filter(Boolean).length : -1;
        return ca - cb;
      },
    },
    {
      title: 'Дії', key: 'actions', width: 100,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />}
            onClick={(e) => { e.stopPropagation(); openEdit(record); }} />
          <Popconfirm title="Видалити програму?" onConfirm={() => handleDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ─── Semester & group helpers ───
  const CODE_GROUPS: { prefix: string; label: string; color: string }[] = [
    { prefix: 'ОК', label: 'Обов\'язкові компоненти (ОК)', color: 'blue' },
    { prefix: 'ВК', label: 'Вибіркові компоненти (ВК)', color: 'green' },
    { prefix: 'ВП', label: 'Військово-професійні (ВП)', color: 'volcano' },
  ];

  const getCodePrefix = (code?: string): string => {
    if (!code) return '';
    const match = code.match(/^(ОК|ВК|ВП)/i);
    return match ? match[1].toUpperCase() : '';
  };

  /** Get all semesters a discipline belongs to (from hoursBySemester, creditsBySemester, examSemesters, creditSemesters) */
  const getDisciplineSemesters = (d: Discipline): number[] => {
    const sems = new Set<number>();
    // hoursBySemester: {"1": 60, "3": 30}
    try {
      const hbs = d.hoursBySemester ? JSON.parse(d.hoursBySemester) : {};
      Object.keys(hbs).forEach((k) => { const n = Number(k); if (n > 0) sems.add(n); });
    } catch { /* ignore */ }
    // creditsBySemester
    try {
      const cbs = d.creditsBySemester ? JSON.parse(d.creditsBySemester) : {};
      Object.keys(cbs).forEach((k) => { const n = Number(k); if (n > 0) sems.add(n); });
    } catch { /* ignore */ }
    // examSemesters: "1 3"
    if (d.examSemesters) {
      d.examSemesters.split(/[\s,;]+/).forEach((s) => { const n = Number(s); if (n > 0) sems.add(n); });
    }
    // creditSemesters: "2"
    if (d.creditSemesters) {
      d.creditSemesters.split(/[\s,;]+/).forEach((s) => { const n = Number(s); if (n > 0) sems.add(n); });
    }
    return [...sems].sort((a, b) => a - b);
  };

  /** Build semester → grouped disciplines structure */
  const semesterData = useMemo(() => {
    const semMap = new Map<number, Discipline[]>();
    const noSemester: Discipline[] = [];

    drillDisciplines.forEach((d) => {
      const sems = getDisciplineSemesters(d);
      if (sems.length === 0) {
        noSemester.push(d);
      } else {
        sems.forEach((sem) => {
          if (!semMap.has(sem)) semMap.set(sem, []);
          semMap.get(sem)!.push(d);
        });
      }
    });

    const sorted = [...semMap.entries()].sort(([a], [b]) => a - b);
    return { semesters: sorted, noSemester };
  }, [drillDisciplines]);

  const staffingTooltip = (ds: DisciplineStaffingDto | undefined) => {
    if (!ds || ds.teachers.length === 0) return 'Немає призначених викладачів';
    return ds.teachers.map((t) => {
      const parts: string[] = [t.teacherName];
      parts.push(`п.38: ${t.point36Compliant ? '✓' : '✗'} (${t.point38TypeCount}/4)`);
      const qualLabels = [
        t.hasMatchingDiploma ? 'освіта' : null,
        t.hasMatchingDegree ? 'ступінь' : null,
        t.hasPracticalExperience ? 'проф.стаж' : null,
        t.hasDissertationSupervision ? 'керівник' : null,
      ].filter(Boolean).join(', ');
      parts.push(`кваліф: ${t.point37aCompliant ? '✓' : '✗'} ${qualLabels || '—'}`);
      parts.push(`публ: ${t.point37bCompliant ? '✓' : '✗'} (${t.qualifiedPublicationsCount}/5)`);
      return parts.join(' | ');
    }).join('\n');
  };

  const disciplineColumns: ColumnsType<Discipline> = [
    {
      title: '', key: 'staffingStatus', width: 36, align: 'center',
      render: (_, r) => {
        const ds = disciplineStaffing[r.id];
        if (!ds) return null;
        const tds = teachersByDiscipline[r.id];
        if (!tds || tds.length === 0) return <Tooltip title="Немає викладачів"><MinusCircleOutlined style={{ color: '#8c8c8c' }} /></Tooltip>;
        return (
          <Tooltip title={<pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap' }}>{staffingTooltip(ds)}</pre>}>
            {ds.staffed
              ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
              : <CloseCircleOutlined style={{ color: '#f5222d' }} />
            }
          </Tooltip>
        );
      },
    },
    { title: 'Код', dataIndex: 'code', key: 'code', width: 90 },
    { title: 'Назва дисципліни', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: 'Викладачі', key: 'assignedTeachers', width: 320,
      render: (_, r) => {
        const tds = teachersByDiscipline[r.id];
        if (!tds || tds.length === 0) return <Text type="secondary">—</Text>;
        const ds = disciplineStaffing[r.id];
        return (
          <div style={{ lineHeight: 1.3 }}>
            {tds.map((td) => {
              const tq = ds?.teachers.find((t) => t.teacherId === td.teacher?.id);
              const name = td.teacher
                ? `${td.teacher.lastName} ${td.teacher.firstName?.charAt(0) || ''}.`
                : '?';
              const color = tq?.fullyCompliant ? '#52c41a' : tq ? '#f5222d' : undefined;

              // Build compact info line
              const infoParts: string[] = [];
              if (tq?.academicDegree) infoParts.push(tq.academicDegree);
              if (tq?.academicTitle) infoParts.push(tq.academicTitle);

              // Compliance badges
              const badges: React.ReactNode[] = [];
              if (tq) {
                badges.push(
                  <span key="p38" style={{ color: tq.point36Compliant ? '#52c41a' : '#f5222d' }}>
                    п.38:{tq.point38TypeCount}/4
                  </span>
                );
                const qualLabels = [
                  tq.hasMatchingDiploma ? 'освіта' : null,
                  tq.hasMatchingDegree ? 'ступінь' : null,
                  tq.hasPracticalExperience ? 'проф.стаж' : null,
                  tq.hasDissertationSupervision ? 'керівник' : null,
                ].filter(Boolean).join(', ');
                badges.push(
                  <span key="qual" style={{ color: tq.point37aCompliant ? '#52c41a' : '#f5222d' }}>
                    кваліф:{qualLabels || '—'}
                  </span>
                );
                badges.push(
                  <span key="pub" style={{ color: tq.point37bCompliant ? '#52c41a' : '#f5222d' }}>
                    публ:{tq.qualifiedPublicationsCount}/5
                  </span>
                );
              }

              return (
                <div key={td.id} style={{ marginBottom: tds.length > 1 ? 4 : 0 }}>
                  <span style={color ? { color, fontWeight: 500 } : undefined}>{name}</span>
                  {infoParts.length > 0 && (
                    <span style={{ color: '#8c8c8c', fontSize: 11, marginLeft: 4 }}>
                      ({infoParts.join(', ')})
                    </span>
                  )}
                  {badges.length > 0 && (
                    <div style={{ fontSize: 11, display: 'flex', gap: 6 }}>
                      {badges}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        );
      },
    },
    {
      title: 'Кафедра', key: 'department', width: 100,
      render: (_, r) => r.department?.number || '—',
    },
    { title: 'ЄКТС', dataIndex: 'credits', key: 'credits', width: 70 },
    { title: 'Годин', dataIndex: 'totalHours', key: 'totalHours', width: 65 },
    { title: 'Аудит.', dataIndex: 'auditoryHours', key: 'auditoryHours', width: 65 },
    { title: 'Лекц.', dataIndex: 'hoursLecture', key: 'hoursLecture', width: 60 },
    { title: 'Груп.', dataIndex: 'hoursGroup', key: 'hoursGroup', width: 60 },
    { title: 'Практ.', dataIndex: 'hoursPractical', key: 'hoursPractical', width: 60 },
    { title: 'Сампо', dataIndex: 'hoursSelfStudy', key: 'hoursSelfStudy', width: 65 },
    ...(canAssign ? [{
      title: '', key: 'teachers', width: 40,
      render: (_: any, r: Discipline) => (
        <Button
          type="text"
          size="small"
          icon={<UserOutlined />}
          onClick={(e) => { e.stopPropagation(); openTeacherModal(r); }}
          title="Викладачі"
        />
      ),
    }] : []),
  ];

  /** Render grouped disciplines table for a semester */
  const renderSemesterContent = (disciplines: Discipline[]) => {
    // Group by code prefix
    const grouped = new Map<string, Discipline[]>();
    const other: Discipline[] = [];

    disciplines.forEach((d) => {
      const prefix = getCodePrefix(d.code);
      if (prefix) {
        if (!grouped.has(prefix)) grouped.set(prefix, []);
        grouped.get(prefix)!.push(d);
      } else {
        other.push(d);
      }
    });

    const sections: React.ReactNode[] = [];

    CODE_GROUPS.forEach(({ prefix, label, color }) => {
      const items = grouped.get(prefix);
      if (!items || items.length === 0) return;
      sections.push(
        <div key={prefix} style={{ marginBottom: 16 }}>
          <Tag color={color} style={{ marginBottom: 8, fontSize: 13 }}>{label}</Tag>
          <Table
            dataSource={items}
            columns={disciplineColumns}
            rowKey="id"
            size="small"
            pagination={false}
            showHeader={sections.length === 0}
          />
        </div>
      );
    });

    if (other.length > 0) {
      sections.push(
        <div key="other" style={{ marginBottom: 16 }}>
          <Tag color="default" style={{ marginBottom: 8, fontSize: 13 }}>Інші</Tag>
          <Table
            dataSource={other}
            columns={disciplineColumns}
            rowKey="id"
            size="small"
            pagination={false}
            showHeader={sections.length === 0}
          />
        </div>
      );
    }

    return <>{sections}</>;
  };

  const departmentOptions = departments.map((d) => ({
    value: d.id,
    label: d.number ? `${d.number} — ${d.name}` : d.name,
  }));


  const teacherInfoLine = (_teacher: Teacher) => {
    // Degree/title info is no longer in TeacherDto — shown via TeacherQualificationDto
    return '';
  };

  // ─── Drill-down view: disciplines of selected program ───
  if (drillProgram) {
    return (
      <div>
        <Breadcrumb
          style={{ marginBottom: 12 }}
          items={[
            {
              title: <a onClick={closeDrill}>Освітньо-професійні програми</a>,
            },
            {
              title: drillProgram.shortCode
                ? `${drillProgram.shortCode} — ${drillProgram.name}`
                : drillProgram.name,
            },
          ]}
        />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={closeDrill}>Назад</Button>
            <Title level={4} style={{ margin: 0 }}>
              {drillProgram.shortCode && <Tag color="geekblue">{drillProgram.shortCode}</Tag>}
              {drillProgram.name}
            </Title>
          </Space>
          {canAssign && exportTemplates.length > 0 && (
            <Dropdown
              menu={{
                items: exportTemplates.map((t) => ({
                  key: t.id,
                  label: t.name,
                })),
                onClick: ({ key }) => handleExportByProgram(Number(key), drillProgram.id),
              }}
              disabled={exporting}
            >
              <Button icon={<DownloadOutlined />} loading={exporting}>
                Експорт викладачів ОПП
              </Button>
            </Dropdown>
          )}
        </div>

        <Card size="small" style={{ marginBottom: 16 }}>
          <Space size="large" wrap>
            {drillProgram.specialty && <Text><Text strong>Спеціальність:</Text> {drillProgram.specialty}</Text>}
            {drillProgram.specialization && <Text><Text strong>Спеціалізація:</Text> {drillProgram.specialization}</Text>}
            {drillProgram.degree && <Text><Text strong>Ступінь:</Text> {drillProgram.degree}</Text>}
            {drillProgram.enrollmentYear && <Text><Text strong>Рік набору:</Text> {drillProgram.enrollmentYear}</Text>}
            {drillProgram.department && <Text><Text strong>Кафедра:</Text> {deptLabel(drillProgram.department)}</Text>}
          </Space>
        </Card>

        {staffStats && (
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space size="large" wrap>
              <span>
                <Text strong>Викладачів: </Text>
                <Text>{staffStats.totalTeachers}</Text>
              </span>
              <span>
                <Text strong>п.35 (≥50% зі ступенем/званням, осн.): </Text>
                {staffStats.point35Compliant
                  ? <Tag color="success">{staffStats.mainWithDegreePercent}% ({staffStats.mainWithDegreeCount}/{staffStats.totalTeachers})</Tag>
                  : <Tag color="error">{staffStats.mainWithDegreePercent}% ({staffStats.mainWithDegreeCount}/{staffStats.totalTeachers})</Tag>
                }
              </span>
              {staffStats.degree && ['магістр', 'доктор філософії'].some((d) => staffStats.degree!.toLowerCase().includes(d)) && (
                <span>
                  <Text strong>
                    Доктори/професори ({staffStats.degree?.toLowerCase().includes('магістр') ? '≥10%' : '≥2'}):
                  </Text>{' '}
                  {(() => {
                    const isMaster = staffStats.degree?.toLowerCase().includes('магістр');
                    const ok = isMaster
                      ? staffStats.doctorsOrProfessorsPercent >= 10
                      : staffStats.doctorsOrProfessorsCount >= 2;
                    return ok
                      ? <Tag color="success">{staffStats.doctorsOrProfessorsCount} ({staffStats.doctorsOrProfessorsPercent}%)</Tag>
                      : <Tag color="error">{staffStats.doctorsOrProfessorsCount} ({staffStats.doctorsOrProfessorsPercent}%)</Tag>;
                  })()}
                </span>
              )}
              <Tooltip title="Мін. 3 особи зі ступенем/званням + основне місце роботи + відповідна кваліфікація">
                <span>
                  <Text strong>п.35с (≥3 осіб зі ступенем, осн.): </Text>
                  {staffStats.point35cCompliant
                    ? <Tag color="success">{staffStats.qualifiedMainCount}</Tag>
                    : <Tag color="error">{staffStats.qualifiedMainCount}</Tag>
                  }
                </span>
              </Tooltip>
              <Tooltip title="Дисципліни, де хоча б один викладач відповідає п.36 (≥4 типи п.38) та п.37 (кваліфікація + публікації)">
                <span>
                  <Text strong>Забезпечені компоненти (п.36+п.37): </Text>
                  {staffStats.point36Compliant
                    ? <Tag color="success">{staffStats.disciplinesFullyStaffed}/{staffStats.disciplinesTotal}</Tag>
                    : <Tag color="warning">{staffStats.disciplinesFullyStaffed}/{staffStats.disciplinesTotal}</Tag>
                  }
                </span>
              </Tooltip>
            </Space>
          </Card>
        )}

        <ProgramWorkingGroupSection programId={drillProgram.id} />

        <Card size="small" title={`Дисципліни (${drillDisciplines.length})`} loading={drillLoading}>
          {semesterData.semesters.length > 0 ? (
            <Collapse
              defaultActiveKey={[]}
              items={semesterData.semesters.map(([sem, disciplines]) => ({
                key: String(sem),
                label: (
                  <Space>
                    <Text strong>{`${sem} семестр`}</Text>
                    <Tag>{disciplines.length} дисц.</Tag>
                  </Space>
                ),
                children: renderSemesterContent(disciplines),
              }))}
            />
          ) : (
            !drillLoading && <Empty description="Немає дисциплін" />
          )}

          {semesterData.noSemester.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Text type="secondary" strong style={{ display: 'block', marginBottom: 8 }}>
                Без прив'язки до семестру ({semesterData.noSemester.length})
              </Text>
              {renderSemesterContent(semesterData.noSemester)}
            </div>
          )}
        </Card>

        {/* Teacher assignment modal */}
        <Modal
          title={`Викладачі — ${teacherModalDiscipline?.name || ''}`}
          open={teacherModalOpen}
          onCancel={() => { setTeacherModalOpen(false); setTeacherModalDiscipline(null); }}
          footer={null}
          width={560}
        >
          {teacherModalDiscipline && (
            <>
              <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
                Кафедра: {teacherModalDiscipline.department?.number || '—'} — {teacherModalDiscipline.department?.name || '—'}
              </Text>

              {assignedTeachers.length > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>Призначені:</Text>
                  {assignedTeachers.map((td) => {
                    const t = td.teacher ? deptTeachers.find((dt) => dt.id === td.teacher!.id) : null;
                    const ds = teacherModalDiscipline ? disciplineStaffing[teacherModalDiscipline.id] : undefined;
                    const tq = ds?.teachers.find((tqt) => tqt.teacherId === td.teacher?.id);
                    const borderColor = tq?.fullyCompliant ? '#52c41a' : tq ? '#f5222d' : '#d9d9d9';
                    return (
                      <div key={td.id} style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', padding: '8px 10px', marginBottom: 6, background: '#fafafa', borderRadius: 6, borderLeft: `3px solid ${borderColor}` }}>
                        <div style={{ flex: 1 }}>
                          <Text strong>{td.teacher ? `${td.teacher.lastName} ${td.teacher.firstName}` : `#${td.id}`}</Text>
                          {t && teacherInfoLine(t) && (
                            <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{teacherInfoLine(t)}</Text>
                          )}
                          {tq && (
                            <div style={{ fontSize: 12, marginTop: 4, display: 'flex', flexDirection: 'column', gap: 2 }}>
                              <span style={{ color: tq.point36Compliant ? '#52c41a' : '#f5222d' }}>
                                {tq.point36Compliant ? '✓' : '✗'} п.38: {tq.point38TypeCount}/4 типи досягнень
                              </span>
                              <span style={{ color: tq.point37aCompliant ? '#52c41a' : '#f5222d' }}>
                                {tq.point37aCompliant ? '✓' : '✗'} Кваліфікація: {[
                                  tq.hasMatchingDiploma ? 'освіта' : null,
                                  tq.hasMatchingDegree ? 'ступінь' : null,
                                  tq.hasPracticalExperience ? 'проф.стаж' : null,
                                  tq.hasDissertationSupervision ? 'керівник' : null,
                                ].filter(Boolean).join(', ') || '—'}
                              </span>
                              <span style={{ color: tq.point37bCompliant ? '#52c41a' : '#f5222d' }}>
                                {tq.point37bCompliant ? '✓' : '✗'} Публікації: {tq.qualifiedPublicationsCount}/5
                              </span>
                            </div>
                          )}
                        </div>
                        <Button type="text" size="small" danger icon={<DeleteOutlined />}
                          onClick={() => handleRemoveTeacher(td.id)} style={{ marginTop: 2 }} />
                      </div>
                    );
                  })}
                </div>
              )}

              {assignedTeachers.length === 0 && (
                <Empty description="Немає призначених викладачів" style={{ margin: '12px 0' }} image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}

              {/* Switch: вузький список (кафедра дисципліни) ↔ повний (всі викладачі системи) */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <Switch
                  size="small"
                  checked={allowAllTeachers}
                  loading={allTeachersLoading}
                  onChange={handleAllowAllTeachersChange}
                />
                <Text style={{ fontSize: 12, color: '#595959' }}>
                  {allowAllTeachers
                    ? 'Шукати серед усіх викладачів системи'
                    : `Тільки викладачі кафедри${teacherModalDiscipline?.department?.number
                        ? ` ${teacherModalDiscipline.department.number}` : ''}`}
                </Text>
              </div>

              <Select
                style={{ width: '100%' }}
                placeholder={allowAllTeachers
                  ? 'Введіть прізвище — шукаємо серед усіх...'
                  : 'Додати викладача...'}
                showSearch
                optionFilterProp="label"
                loading={teacherAssigning || (allowAllTeachers && allTeachersLoading)}
                value={null as any}
                onChange={handleAssignTeacher}
                popupMatchSelectWidth={false}
                dropdownStyle={{ minWidth: 520 }}
                optionRender={(option) => {
                  const sourceList = allowAllTeachers ? allTeachers : deptTeachers;
                  const t = sourceList.find((dt) => dt.id === option.value);
                  const cr = t ? complianceMap[t.id] : undefined;
                  const info = t ? teacherInfoLine(t) : '';
                  const hasDegree = !!(cr?.degreeMatchesDepartment || cr?.diplomaMatchesDepartment);
                  // Кафедра викладача — корисно показати коли список ширший за кафедру дисципліни
                  const ownDeptLabel = (t?.departmentNumber || t?.departmentName) && allowAllTeachers
                    && t.departmentId !== teacherModalDiscipline?.department?.id
                    ? (t.departmentNumber ? `каф. ${t.departmentNumber}` : t.departmentName)
                    : null;
                  return (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <div>
                          {option.label}
                          {hasDegree && <span style={{ color: '#52c41a', marginLeft: 4, fontSize: 12 }}>●</span>}
                          {ownDeptLabel && (
                            <span style={{ color: '#1890ff', marginLeft: 6, fontSize: 11 }}>
                              [{ownDeptLabel}]
                            </span>
                          )}
                        </div>
                        {info && <div style={{ fontSize: 11, color: '#8c8c8c' }}>{info}</div>}
                      </div>
                      <div style={{ textAlign: 'right', flexShrink: 0, fontSize: 11, color: '#8c8c8c', whiteSpace: 'nowrap' }}>
                        {cr ? (
                          <>
                            <div>п.38: {cr.uniqueTypeCount}/4</div>
                            <div>публ: {cr.publicationsCount}</div>
                          </>
                        ) : '—'}
                      </div>
                    </div>
                  );
                }}
                options={(allowAllTeachers ? allTeachers : deptTeachers)
                  .filter((t) => !assignedTeachers.some((td) => td.teacher?.id === t.id))
                  .sort((a, b) => {
                    // Sort: with degree/title first, then by achievement count desc
                    const aCrLocal = complianceMap[a.id];
                    const bCrLocal = complianceMap[b.id];
                    const aDegree = (aCrLocal?.degreeMatchesDepartment || aCrLocal?.diplomaMatchesDepartment) ? 1 : 0;
                    const bDegree = (bCrLocal?.degreeMatchesDepartment || bCrLocal?.diplomaMatchesDepartment) ? 1 : 0;
                    if (aDegree !== bDegree) return bDegree - aDegree;
                    return (bCrLocal?.uniqueTypeCount || 0) - (aCrLocal?.uniqueTypeCount || 0);
                  })
                  .map((t) => ({
                    value: t.id,
                    label: `${t.lastName} ${t.firstName}${t.patronymic ? ' ' + t.patronymic : ''}`,
                  }))}
              />
            </>
          )}
        </Modal>
      </div>
    );
  }

  // ─── Main view: OPP list ───
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>Освітньо-професійні програми</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Додати програму
        </Button>
      </div>

      <Card size="small">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space size="large" wrap>
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Ступінь</div>
              <Radio.Group
                value={selectedDegree}
                onChange={(e) => { setSelectedDegree(e.target.value); setSelectedYear(null); }}
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: 'бакалавр', label: 'Бакалавр' },
                  { value: 'магістр', label: 'Магістр' },
                  { value: 'доктор філософії', label: 'Доктор філософії' },
                ]}
              />
            </div>
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Форма навчання</div>
              <Radio.Group
                value={selectedForm}
                onChange={(e) => { setSelectedForm(e.target.value); setSelectedYear(null); }}
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: 'денна', label: 'Денна' },
                  { value: 'заочна', label: 'Заочна' },
                ]}
              />
            </div>
            {availableYears.length > 0 && (
              <div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>Рік набору</div>
                <Radio.Group
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(e.target.value)}
                  optionType="button"
                  buttonStyle="solid"
                  options={availableYears.map((y) => ({ value: y, label: String(y) }))}
                />
              </div>
            )}
          </Space>

          {filteredPrograms.length > 0 ? (
            <Table
              dataSource={filteredPrograms}
              columns={columns}
              rowKey="id"
              loading={loading}
              scroll={{ x: 1100 }}
              pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `Всього: ${total}` }}
              onRow={(record) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm, a')) return;
                  openDrill(record);
                },
                style: { cursor: 'pointer' },
              })}
            />
          ) : (
            <Empty
              description={`Немає програм для ступеня "${selectedDegree}"${selectedYear ? ` та року ${selectedYear}` : ''}`}
              style={{ padding: '40px 0' }}
            />
          )}
        </Space>
      </Card>

      <Modal
        title={editing ? 'Редагувати програму' : 'Нова програма'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={700}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ maxHeight: '60vh', overflowY: 'auto', paddingRight: 8 }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="name" label="Назва" rules={[{ required: true, message: 'Введіть назву' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="shortCode" label="Скорочене позначення" style={{ width: 200 }}>
              <Input placeholder="F3 КН, F5 КЗІ (121501)" />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="specialty" label="Спеціальність" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="fieldOfKnowledge" label="Галузь знань" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="specialization" label="Спеціалізація">
            <Input />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="educationLevel" label="Рівень вищої освіти" style={{ flex: 1 }}>
              <Input placeholder="перший (бакалаврський)" />
            </Form.Item>
            <Form.Item name="degree" label="Ступінь" style={{ width: 180 }}>
              <Input placeholder="бакалавр" />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="educationForm" label="Форма навчання" style={{ flex: 1 }}>
              <Input placeholder="очна (денна)" />
            </Form.Item>
            <Form.Item name="educationalQualification" label="Освітня кваліфікація" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="professionalQualification" label="Професійна кваліфікація">
            <Input />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="credits" label="Кредити ЄКТС" style={{ width: 140 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="duration" label="Термін навчання" style={{ flex: 1 }}>
              <Input placeholder="4 роки" />
            </Form.Item>
            <Form.Item name="enrollmentYear" label="Рік набору" style={{ width: 130 }}>
              <InputNumber min={2000} max={2100} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="departmentId" label="Кафедра">
            <Select
              allowClear
              showSearch
              placeholder="Оберіть кафедру"
              options={departmentOptions}
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default EducationalProgramsPage;
