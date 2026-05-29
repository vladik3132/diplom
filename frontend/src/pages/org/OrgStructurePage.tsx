import React, { useEffect, useState, useCallback } from 'react';
import {
  Layout, Tree, Button, Table, Modal, Form, Input, InputNumber, Select, Tag,
  Space, Typography, Popconfirm, Card, Empty, message, Spin, Dropdown, Tooltip,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, ApartmentOutlined,
  BankOutlined, TeamOutlined, MoreOutlined, ImportOutlined,
  CheckCircleOutlined, ExclamationCircleOutlined, MinusCircleOutlined,
  LinkOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import type { Faculty, Department, StaffPosition, Teacher } from '../../types/teacher';
import {
  getFaculties, createFaculty, updateFaculty, deleteFaculty,
  getDepartments, createDepartment, updateDepartment, deleteDepartment,
  getStaffPositions, createStaffPosition, updateStaffPosition, deleteStaffPosition,
  batchImportStaffPositions,
  relinkStaffPositions,
} from '../../api/departmentApi';
import { getTeachers } from '../../api/teacherApi';
import { rankOfPosition } from '../../utils/positionSeniority';
import { useAuth } from '../../auth/AuthContext';

const { Sider, Content } = Layout;
const { Title, Text } = Typography;
const { TextArea } = Input;

type SelectedNode = { type: 'faculty'; id: number } | { type: 'department'; id: number } | null;

// ── Staff text parser ──────────────────────────────────────────────────

interface ParsedPosition {
  orderNumber: number;
  positionTitle: string;
  militaryRankCategory: string;
  militarySpecialtyCode: string;
  tariffGrade: number | undefined;
  teacherName: string;
  rate: number;
}

function parseStaffText(text: string): ParsedPosition[] {
  const results: ParsedPosition[] = [];
  // Normalize whitespace, keep newlines
  const cleaned = text.replace(/\r\n/g, '\n').replace(/\t/g, ' ');
  const lines = cleaned.split('\n').map((l) => l.trim()).filter(Boolean);

  let currentPos: Omit<ParsedPosition, 'teacherName' | 'rate'> | null = null;

  const rankPatterns = ['Полковник', 'Підполковник', 'Майор', 'Капітан', 'Старший лейтенант', 'Лейтенант', 'Працівник ЗСУ'];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Try to match a position line: starts with number
    const posMatch = line.match(/^(\d{1,3})\s*(.+)/);
    if (posMatch) {
      const num = parseInt(posMatch[1]);
      let rest = posMatch[2];

      // Try to extract rank
      let rank = '';
      for (const rp of rankPatterns) {
        const idx = rest.indexOf(rp);
        if (idx >= 0) {
          rank = rp;
          const title = rest.substring(0, idx).trim();
          rest = rest.substring(idx + rp.length).trim();
          if (title) {
            // Extract VOS code (7 digits)
            const vosMatch = rest.match(/^(\d{7})/);
            const vos = vosMatch ? vosMatch[1] : '';
            if (vosMatch) rest = rest.substring(7).trim();

            // Extract tariff (2 digits)
            const tariffMatch = rest.match(/^(\d{2})\s*/);
            const tariff = tariffMatch ? parseInt(tariffMatch[1]) : undefined;
            if (tariffMatch) rest = rest.substring(tariffMatch[0].length).trim();

            currentPos = { orderNumber: num, positionTitle: title, militaryRankCategory: rank, militarySpecialtyCode: vos, tariffGrade: tariff };

            // Rest is teacher name(s)
            if (rest) {
              parseTeacherNames(rest, currentPos, results);
            }
            break;
          }
        }
      }

      // If no rank found in this line, it might be a position title spanning multiple lines
      if (!rank && currentPos) {
        // Could be a continuation teacher name line
        parseTeacherNames(line.replace(/^\d{1,3}\s*/, ''), currentPos, results);
      } else if (!rank && !currentPos) {
        // First line without rank — skip or treat as position
        currentPos = { orderNumber: num, positionTitle: rest, militaryRankCategory: '', militarySpecialtyCode: '', tariffGrade: undefined };
      }
    } else if (currentPos) {
      // Continuation line — could be rank or teacher names
      let foundRank = false;
      for (const rp of rankPatterns) {
        if (line.startsWith(rp)) {
          currentPos.militaryRankCategory = rp;
          const afterRank = line.substring(rp.length).trim();

          // Try VOS + tariff
          let rest2 = afterRank;
          const vosMatch2 = rest2.match(/^(\d{7})/);
          if (vosMatch2) { currentPos.militarySpecialtyCode = vosMatch2[1]; rest2 = rest2.substring(7).trim(); }
          const tariffMatch2 = rest2.match(/^(\d{2})\s*/);
          if (tariffMatch2) { currentPos.tariffGrade = parseInt(tariffMatch2[1]); rest2 = rest2.substring(tariffMatch2[0].length).trim(); }

          if (rest2) parseTeacherNames(rest2, currentPos, results);
          foundRank = true;
          break;
        }
      }
      if (!foundRank) {
        // Teacher name line
        parseTeacherNames(line, currentPos, results);
      }
    }
  }

  // If we have a currentPos with no results added for it, add as vacant
  if (currentPos && !results.find((r) => r.orderNumber === currentPos!.orderNumber)) {
    results.push({ ...currentPos, teacherName: 'ВАКАНТ', rate: 1.0 });
  }

  return results;
}

function parseTeacherNames(text: string, pos: Omit<ParsedPosition, 'teacherName' | 'rate'>, results: ParsedPosition[]) {
  if (!text.trim()) return;

  // Split by known UPPERCASE name pattern: "ПРІЗВИЩЕ Ім'я По-батькові 0,5"
  // Or just "ВАКАНТ" or "ВАКАНТ 0,5"
  const namePattern = /([А-ЯІЇЄҐA-Z][А-ЯІЇЄҐA-Z']+\s+[А-ЯІЇЄҐа-яіїєґA-Za-z][а-яіїєґa-z']+(?:\s+[А-ЯІЇЄҐа-яіїєґA-Za-z][а-яіїєґa-z']+)?(?:\s+\d[,.]?\d*)?|ВАКАНТ(?:\s+\d[,.]?\d*)?)/g;

  const matches = text.match(namePattern);
  if (matches && matches.length > 0) {
    for (const m of matches) {
      const rateMatch = m.match(/\s+(\d[,.]?\d*)$/);
      const rate = rateMatch ? parseFloat(rateMatch[1].replace(',', '.')) : 1.0;
      const name = rateMatch ? m.substring(0, m.length - rateMatch[0].length).trim() : m.trim();
      results.push({ ...pos, teacherName: name, rate });
    }
  } else {
    // Fallback: treat entire text as one name
    const trimmed = text.trim();
    if (trimmed) {
      const rateMatch = trimmed.match(/\s+(\d[,.]?\d*)$/);
      const rate = rateMatch ? parseFloat(rateMatch[1].replace(',', '.')) : 1.0;
      const name = rateMatch ? trimmed.substring(0, trimmed.length - rateMatch[0].length).trim() : trimmed;
      results.push({ ...pos, teacherName: name, rate });
    }
  }
}

// ── Teacher name matching ──────────────────────────────────────────────

function matchTeacherByName(name: string, teachers: Teacher[]): Teacher | null {
  if (!name || name === 'ВАКАНТ') return null;
  const parts = name.trim().split(/\s+/);
  if (parts.length < 2) return null;
  const lastName = parts[0].toLowerCase();
  const firstName = parts[1]?.toLowerCase() ?? '';
  const patronymic = parts[2]?.toLowerCase() ?? '';
  return teachers.find((t) => {
    const lastMatch = t.lastName?.toLowerCase() === lastName;
    const firstMatch = !firstName || t.firstName?.toLowerCase() === firstName;
    const patronMatch = !patronymic || (t.patronymic?.toLowerCase() ?? '') === patronymic;
    return lastMatch && firstMatch && patronMatch;
  }) ?? null;
}

// ── Component ──────────────────────────────────────────────────────────

const OrgStructurePage: React.FC = () => {
  const { isAdmin, isHead, user } = useAuth();
  const canManageStaff = isAdmin || isHead;
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [staffPositions, setStaffPositions] = useState<StaffPosition[]>([]);
  const [allTeachers, setAllTeachers] = useState<Teacher[]>([]);
  const [selected, setSelected] = useState<SelectedNode>(null);
  const [loading, setLoading] = useState(false);
  const [staffLoading, setStaffLoading] = useState(false);

  // Modals
  const [facultyModalOpen, setFacultyModalOpen] = useState(false);
  const [deptModalOpen, setDeptModalOpen] = useState(false);
  const [posModalOpen, setPosModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [editingFaculty, setEditingFaculty] = useState<Faculty | null>(null);
  const [editingDept, setEditingDept] = useState<(Department & { faculty?: { id: number } }) | null>(null);
  const [editingPos, setEditingPos] = useState<StaffPosition | null>(null);
  const [importText, setImportText] = useState('');
  const [importPreview, setImportPreview] = useState<ParsedPosition[]>([]);

  const [facultyForm] = Form.useForm();
  const [deptForm] = Form.useForm();
  const [posForm] = Form.useForm();

  // ── Data loading ───────────────────────────────────────────────────

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [facs, depts, teachers] = await Promise.all([getFaculties(), getDepartments(), getTeachers()]);
      setFaculties(facs);
      setDepartments(depts);
      setAllTeachers(teachers);
    } catch {
      message.error('Помилка завантаження даних');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  // HEAD: auto-select own department
  useEffect(() => {
    if (!isAdmin && user?.departmentId && departments.length > 0 && !selected) {
      setSelected({ type: 'department', id: user.departmentId });
    }
  }, [isAdmin, user?.departmentId, departments, selected]);

  const fetchStaff = useCallback(async (deptId: number) => {
    setStaffLoading(true);
    try {
      const data = await getStaffPositions(deptId);
      setStaffPositions(data);
    } catch {
      message.error('Помилка завантаження штатного розпису');
    } finally {
      setStaffLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selected?.type === 'department') {
      fetchStaff(selected.id);
    } else {
      setStaffPositions([]);
    }
  }, [selected, fetchStaff]);

  // ── Tree data ──────────────────────────────────────────────────────

  const treeData: DataNode[] = (() => {
    // Build faculty nodes with their departments
    const facultyNodes = faculties
      .map((f) => {
        const children = departments
          .filter((d: any) => (d.faculty?.id ?? d.facultyId) === f.id)
          .map((d: any) => ({
            key: `dept-${d.id}`,
            title: d.number ? `${d.number} — ${d.name}` : d.name,
            icon: <TeamOutlined />,
            isLeaf: true,
          }));
        return {
          key: `faculty-${f.id}`,
          title: f.name,
          icon: <BankOutlined />,
          children,
        };
      })
      // For non-admin (HEAD): hide faculties with no visible departments
      .filter((node) => isAdmin || node.children.length > 0);

    // Orphan departments (no faculty)
    const orphans = departments.filter((d: any) => !(d.faculty?.id ?? d.facultyId));
    const orphanNode: DataNode[] = orphans.length === 0 ? [] : [{
      key: 'no-faculty',
      title: 'Без факультету',
      icon: <ApartmentOutlined />,
      selectable: false as const,
      children: orphans.map((d: any) => ({
        key: `dept-${d.id}`,
        title: d.number ? `${d.number} — ${d.name}` : d.name,
        icon: <TeamOutlined />,
        isLeaf: true,
      })),
    }];

    return [...facultyNodes, ...orphanNode];
  })();

  const handleTreeSelect = (keys: React.Key[]) => {
    if (keys.length === 0) { setSelected(null); return; }
    const key = keys[0] as string;
    if (key.startsWith('faculty-')) setSelected({ type: 'faculty', id: Number(key.replace('faculty-', '')) });
    else if (key.startsWith('dept-')) setSelected({ type: 'department', id: Number(key.replace('dept-', '')) });
  };

  // ── Faculty CRUD ───────────────────────────────────────────────────

  const openFacultyModal = (faculty?: Faculty) => {
    setEditingFaculty(faculty ?? null);
    facultyForm.setFieldsValue(faculty ?? { name: '' });
    setFacultyModalOpen(true);
  };

  const handleFacultySave = async () => {
    try {
      const values = await facultyForm.validateFields();
      if (editingFaculty) { await updateFaculty(editingFaculty.id, values); message.success('Факультет оновлено'); }
      else { await createFaculty(values); message.success('Факультет створено'); }
      setFacultyModalOpen(false);
      facultyForm.resetFields();
      fetchData();
    } catch { /* validation */ }
  };

  const handleFacultyDelete = async (id: number) => {
    try {
      await deleteFaculty(id);
      message.success('Факультет видалено');
      if (selected?.type === 'faculty' && selected.id === id) setSelected(null);
      fetchData();
    } catch { message.error('Не вдалося видалити факультет'); }
  };

  // ── Department CRUD ────────────────────────────────────────────────

  const openDeptModal = (dept?: any) => {
    setEditingDept(dept ?? null);
    deptForm.setFieldsValue({ number: dept?.number ?? '', name: dept?.name ?? '', facultyId: dept?.faculty?.id ?? dept?.facultyId ?? undefined });
    setDeptModalOpen(true);
  };

  const handleDeptSave = async () => {
    try {
      const values = await deptForm.validateFields();
      const payload = { number: values.number || null, name: values.name, faculty: values.facultyId ? { id: values.facultyId } : null };
      if (editingDept) { await updateDepartment(editingDept.id, payload); message.success('Кафедру оновлено'); }
      else { await createDepartment(payload); message.success('Кафедру створено'); }
      setDeptModalOpen(false);
      deptForm.resetFields();
      fetchData();
    } catch { /* validation */ }
  };

  const handleDeptDelete = async (id: number) => {
    try {
      await deleteDepartment(id);
      message.success('Кафедру видалено');
      if (selected?.type === 'department' && selected.id === id) setSelected(null);
      fetchData();
    } catch { message.error('Не вдалося видалити кафедру'); }
  };

  // ── StaffPosition CRUD ─────────────────────────────────────────────

  const deptTeachers = selected?.type === 'department'
    ? allTeachers.filter((t) => {
        const deptId = (t as any).department?.id ?? t.departmentId;
        return deptId === selected.id;
      })
    : [];

  const openPosModal = (pos?: StaffPosition) => {
    setEditingPos(pos ?? null);
    posForm.setFieldsValue({
      positionTitle: pos?.positionTitle ?? '',
      militaryRankCategory: pos?.militaryRankCategory ?? '',
      militarySpecialtyCode: pos?.militarySpecialtyCode ?? '',
      tariffGrade: pos?.tariffGrade ?? undefined,
      rate: pos?.rate ?? 1.0,
      teacherId: pos?.teacher?.id ?? undefined,
    });
    setPosModalOpen(true);
  };

  const handlePosSave = async () => {
    if (selected?.type !== 'department') return;
    try {
      const values = await posForm.validateFields();
      const payload: any = {
        orderNumber: editingPos?.orderNumber ?? (staffPositions.length + 1),
        positionTitle: values.positionTitle,
        militaryRankCategory: values.militaryRankCategory || null,
        militarySpecialtyCode: values.militarySpecialtyCode || null,
        tariffGrade: values.tariffGrade || null,
        rate: values.rate ?? 1.0,
        teacher: values.teacherId ? { id: values.teacherId } : null,
      };
      if (editingPos) { await updateStaffPosition(selected.id, editingPos.id, payload); message.success('Посаду оновлено'); }
      else { await createStaffPosition(selected.id, payload); message.success('Посаду додано'); }
      setPosModalOpen(false);
      posForm.resetFields();
      fetchStaff(selected.id);
    } catch { /* validation */ }
  };

  const handlePosDelete = async (posId: number) => {
    if (selected?.type !== 'department') return;
    try {
      await deleteStaffPosition(selected.id, posId);
      message.success('Посаду видалено');
      fetchStaff(selected.id);
    } catch { message.error('Не вдалося видалити посаду'); }
  };

  // ── Import ─────────────────────────────────────────────────────────

  const handleImportParse = () => {
    const parsed = parseStaffText(importText);
    setImportPreview(parsed);
    if (parsed.length === 0) message.warning('Не вдалося розпізнати жодної позиції');
  };

  const handleImportSave = async () => {
    if (selected?.type !== 'department' || importPreview.length === 0) return;
    try {
      const positions = importPreview.map((p) => ({
        orderNumber: p.orderNumber,
        positionTitle: p.positionTitle,
        militaryRankCategory: p.militaryRankCategory || undefined,
        militarySpecialtyCode: p.militarySpecialtyCode || undefined,
        tariffGrade: p.tariffGrade || undefined,
        rate: p.rate,
        importedTeacherName: p.teacherName || undefined,
      }));
      await batchImportStaffPositions(selected.id, positions);
      message.success(`Імпортовано ${positions.length} посад`);
      setImportModalOpen(false);
      setImportText('');
      setImportPreview([]);
      fetchStaff(selected.id);
    } catch { message.error('Помилка імпорту'); }
  };

  // ── Helpers ────────────────────────────────────────────────────────

  const selectedFaculty = selected?.type === 'faculty' ? faculties.find((f) => f.id === selected.id) : null;
  const selectedDept = selected?.type === 'department' ? departments.find((d) => d.id === selected.id) : null;
  const selectedDeptRaw = selected?.type === 'department' ? (departments as any[]).find((d) => d.id === selected.id) : null;
  const facultyDepts = selectedFaculty ? departments.filter((d: any) => (d.faculty?.id ?? d.facultyId) === selectedFaculty.id) : [];

  // ── Сортування штатних посад ────────────────────────────────────
  // Спочатку військові (по тарифу спадання), потім цивільні (по старшинству посади).
  // Словник рангів — у спільній утиліті, синхронізованій з бекендом.

  const CIVILIAN_RANKS = ['Працівник ЗСУ', ''];

  const isCivilian = (p: StaffPosition) =>
    !p.militaryRankCategory || CIVILIAN_RANKS.includes(p.militaryRankCategory);

  const sortedStaffPositions = [...staffPositions].sort((a, b) => {
    const aCiv = isCivilian(a);
    const bCiv = isCivilian(b);
    // Військові перед цивільними
    if (!aCiv && bCiv) return -1;
    if (aCiv && !bCiv) return 1;
    if (!aCiv && !bCiv) {
      // Обидва військові — по тарифу спадання
      return (b.tariffGrade ?? 0) - (a.tariffGrade ?? 0);
    }
    // Обидва цивільні — по старшинству посади
    return rankOfPosition(a.positionTitle) - rankOfPosition(b.positionTitle);
  });

  const staffColumns = [
    { title: '№', key: 'rowNum', width: 50,
      render: (_: unknown, __: StaffPosition, index: number) => index + 1,
    },
    {
      title: 'Посада', dataIndex: 'positionTitle', key: 'positionTitle',
      render: (title: string, record: StaffPosition) => {
        if (record.bootstrapped) {
          return (
            <Tooltip title="Створено автоматично з картки викладача. Перевірте ставку, ШПК, тариф, ВОС та збережіть.">
              <span>
                <ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 6 }} />
                {title}
              </span>
            </Tooltip>
          );
        }
        return title;
      },
    },
    { title: 'ШПК', dataIndex: 'militaryRankCategory', key: 'militaryRankCategory', width: 140 },
    { title: 'ВОС', dataIndex: 'militarySpecialtyCode', key: 'militarySpecialtyCode', width: 90 },
    { title: 'Тариф', dataIndex: 'tariffGrade', key: 'tariffGrade', width: 65 },
    {
      title: 'Ставка', key: 'rate', width: 65,
      render: (_: unknown, r: StaffPosition) => r.rate && r.rate < 1 ? <Tag>{r.rate}</Tag> : null,
    },
    {
      title: 'ПІБ', key: 'teacher',
      render: (_: unknown, record: StaffPosition) => {
        if (record.teacher) {
          // Залінкований викладач
          const t = record.teacher;
          const name = `${t.lastName} ${t.firstName}${t.patronymic ? ' ' + t.patronymic : ''}`;
          const rateTag = record.rate && record.rate < 1 ? <Tag style={{ marginLeft: 4 }}>{record.rate}</Tag> : null;
          return <>{name}{rateTag}</>;
        }
        if (record.importedTeacherName && record.importedTeacherName !== 'ВАКАНТ') {
          // Є ПІБ з імпорту, але не залінкований
          return <span style={{ color: '#faad14' }}>{record.importedTeacherName}</span>;
        }
        return <Tag color="red">ВАКАНТ</Tag>;
      },
    },
    {
      title: <LinkOutlined />, key: 'linkStatus', width: 45, align: 'center' as const,
      render: (_: unknown, record: StaffPosition) => {
        if (record.teacher) {
          return <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} title="Залінкований" />;
        }
        if (record.importedTeacherName && record.importedTeacherName !== 'ВАКАНТ') {
          return <ExclamationCircleOutlined style={{ color: '#faad14', fontSize: 16 }} title="Не знайдено в системі" />;
        }
        return <MinusCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} title="Вакантна посада" />;
      },
    },
    ...(canManageStaff ? [{
      title: '', key: 'actions', width: 80,
      render: (_: unknown, record: StaffPosition) => (
        <Space>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openPosModal(record)} />
          <Popconfirm title="Видалити посаду?" onConfirm={() => handlePosDelete(record.id)} okText="Так" cancelText="Ні">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    }] : []),
  ];

  // ── Render ─────────────────────────────────────────────────────────

  return (
    <div>
      <Title level={3}><ApartmentOutlined /> {isAdmin ? 'Організаційна структура' : 'Штатний розпис кафедри'}</Title>

      <Layout style={{ background: '#fff', minHeight: 500 }}>
        <Sider width={300} style={{ background: '#fff', borderRight: '1px solid #f0f0f0', padding: '12px 0' }}>
          {isAdmin && (
            <div style={{ padding: '0 12px 12px', display: 'flex', gap: 8 }}>
              <Button size="small" icon={<PlusOutlined />} onClick={() => openFacultyModal()}>Факультет</Button>
              <Button size="small" icon={<PlusOutlined />} onClick={() => openDeptModal()}>Кафедра</Button>
            </div>
          )}
          <Spin spinning={loading}>
            <Tree
              showIcon
              defaultExpandAll
              treeData={treeData}
              selectedKeys={selected ? [selected.type === 'faculty' ? `faculty-${selected.id}` : `dept-${selected.id}`] : []}
              onSelect={handleTreeSelect}
              style={{ padding: '0 4px' }}
              titleRender={(node) => {
                const key = node.key as string;
                const isFaculty = key.startsWith('faculty-');
                const isDept = key.startsWith('dept-');
                const id = Number(key.replace(/^(faculty|dept)-/, ''));
                const menuItems = [
                  { key: 'edit', label: 'Редагувати', icon: <EditOutlined /> },
                  { key: 'delete', label: 'Видалити', icon: <DeleteOutlined />, danger: true },
                ];
                const onClick = ({ key: action }: { key: string }) => {
                  if (isFaculty) {
                    if (action === 'edit') openFacultyModal(faculties.find((f) => f.id === id));
                    if (action === 'delete') handleFacultyDelete(id);
                  } else if (isDept) {
                    if (action === 'edit') openDeptModal((departments as any[]).find((d) => d.id === id));
                    if (action === 'delete') handleDeptDelete(id);
                  }
                };
                return (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, width: '100%' }}>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{node.title as string}</span>
                    {isAdmin && (isFaculty || isDept) && (
                      <Dropdown menu={{ items: menuItems, onClick }} trigger={['click']}>
                        <Button type="text" size="small" icon={<MoreOutlined />} onClick={(e) => e.stopPropagation()} style={{ minWidth: 20, padding: 0 }} />
                      </Dropdown>
                    )}
                  </span>
                );
              }}
            />
          </Spin>
        </Sider>

        <Content style={{ padding: 24, minHeight: 400 }}>
          {!selected && <Empty description="Оберіть факультет або кафедру зліва" />}

          {selectedFaculty && (
            <div>
              <Space style={{ marginBottom: 16 }}>
                <Title level={4} style={{ margin: 0 }}><BankOutlined /> {selectedFaculty.name}</Title>
                {isAdmin && <Button size="small" icon={<EditOutlined />} onClick={() => openFacultyModal(selectedFaculty)} />}
              </Space>
              <Title level={5}>Кафедри ({facultyDepts.length})</Title>
              {facultyDepts.length > 0 ? (
                <Table dataSource={facultyDepts} rowKey="id" pagination={false} size="small"
                  onRow={(record) => ({ onClick: () => setSelected({ type: 'department', id: record.id }), style: { cursor: 'pointer' } })}
                  columns={[
                    { title: '№', key: 'number', width: 60, render: (_: unknown, record: any) => record.number || '—' },
                    { title: 'Назва кафедри', dataIndex: 'name', key: 'name' },
                    ...(isAdmin ? [{ title: '', key: 'actions', width: 80, render: (_: unknown, record: Department) => (
                      <Space>
                        <Button type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); openDeptModal(record); }} />
                        <Popconfirm title="Видалити кафедру?" onConfirm={() => handleDeptDelete(record.id)} okText="Так" cancelText="Ні">
                          <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                        </Popconfirm>
                      </Space>
                    )}] : []),
                  ]}
                />
              ) : <Empty description="Немає кафедр" />}
            </div>
          )}

          {selectedDept && (
            <div>
              <Space style={{ marginBottom: 8 }}>
                <Title level={4} style={{ margin: 0 }}><TeamOutlined /> {(selectedDeptRaw?.number ? selectedDeptRaw.number + ' — ' : '')}{selectedDept.name}</Title>
                {isAdmin && <Button size="small" icon={<EditOutlined />} onClick={() => openDeptModal(selectedDeptRaw)} />}
              </Space>
              {selectedDeptRaw?.faculty?.name && (
                <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>{selectedDeptRaw.faculty.name}</Text>
              )}

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <Title level={5} style={{ margin: 0 }}>Штатний розпис</Title>
                {canManageStaff && (
                  <Space>
                    <Button icon={<SyncOutlined />} onClick={async () => {
                      if (!selected) return;
                      try {
                        const res = await relinkStaffPositions(selected.id);
                        if (res.linked > 0) {
                          message.success(`Залінковано ${res.linked} посад(и)`);
                          fetchStaff(selected.id);
                        } else {
                          message.info('Нових збігів не знайдено');
                        }
                      } catch { message.error('Помилка перелінкування'); }
                    }}>
                      Перелінкувати
                    </Button>
                    <Button icon={<ImportOutlined />} onClick={() => { setImportModalOpen(true); setImportText(''); setImportPreview([]); }}>
                      Імпорт з тексту
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openPosModal()}>
                      Додати посаду
                    </Button>
                  </Space>
                )}
              </div>

              <Table dataSource={sortedStaffPositions} columns={staffColumns} rowKey="id" pagination={false} size="small"
                loading={staffLoading} locale={{ emptyText: 'Штатний розпис порожній' }} />

              {sortedStaffPositions.length > 0 && (
                <Card size="small" style={{ marginTop: 12 }}>
                  <Space size="large">
                    <Text>Всього посад: <strong>{sortedStaffPositions.length}</strong></Text>
                    <Text>Зайнято: <strong>{sortedStaffPositions.filter((p) => p.teacher).length}</strong></Text>
                    <Text>Ставок: <strong>{sortedStaffPositions.reduce((s, p) => s + (p.rate ?? 1), 0).toFixed(1)}</strong></Text>
                    <Text>Вакантних: <Tag color="red">{sortedStaffPositions.filter((p) => !p.teacher).length}</Tag></Text>
                  </Space>
                </Card>
              )}
            </div>
          )}
        </Content>
      </Layout>

      {/* ── Faculty Modal ──────────────────────────────────────────── */}
      <Modal title={editingFaculty ? 'Редагувати факультет' : 'Новий факультет'} open={facultyModalOpen}
        onOk={handleFacultySave} onCancel={() => { setFacultyModalOpen(false); facultyForm.resetFields(); }}
        okText="Зберегти" cancelText="Скасувати">
        <Form form={facultyForm} layout="vertical">
          <Form.Item name="name" label="Назва факультету / інституту" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Department Modal ───────────────────────────────────────── */}
      <Modal title={editingDept ? 'Редагувати кафедру' : 'Нова кафедра'} open={deptModalOpen}
        onOk={handleDeptSave} onCancel={() => { setDeptModalOpen(false); deptForm.resetFields(); }}
        okText="Зберегти" cancelText="Скасувати">
        <Form form={deptForm} layout="vertical">
          <Form.Item name="number" label="Номер кафедри">
            <Input placeholder="11, 22, 3..." style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="name" label="Назва кафедри" rules={[{ required: true, message: 'Введіть назву' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="facultyId" label="Факультет / інститут">
            <Select allowClear placeholder="Без факультету">
              {faculties.map((f) => <Select.Option key={f.id} value={f.id}>{f.name}</Select.Option>)}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* ── StaffPosition Modal ────────────────────────────────────── */}
      <Modal title={editingPos ? 'Редагувати посаду' : 'Нова посада'} open={posModalOpen}
        onOk={handlePosSave} onCancel={() => { setPosModalOpen(false); posForm.resetFields(); }}
        okText="Зберегти" cancelText="Скасувати" width={500}>
        <Form form={posForm} layout="vertical">
          <Form.Item name="positionTitle" label="Посада" rules={[{ required: true, message: 'Введіть посаду' }]}>
            <Input placeholder="Начальник кафедри, Професор, Доцент..." />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="militaryRankCategory" label="ШПК" style={{ flex: 1 }}>
              <Input placeholder="Полковник, Підполковник..." />
            </Form.Item>
            <Form.Item name="militarySpecialtyCode" label="ВОС" style={{ width: 110 }}>
              <Input placeholder="5302003" />
            </Form.Item>
            <Form.Item name="tariffGrade" label="Тариф" style={{ width: 75 }}>
              <InputNumber min={1} max={99} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="rate" label="Ставка" style={{ width: 80 }}>
              <InputNumber min={0.1} max={1} step={0.1} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="teacherId" label="Викладач (ПІБ)">
            <Select allowClear showSearch placeholder="ВАКАНТ (не призначено)"
              optionFilterProp="label"
              options={[
                ...deptTeachers.map((t) => ({
                  value: t.id,
                  label: `${t.lastName} ${t.firstName}${t.patronymic ? ' ' + t.patronymic : ''}`,
                })),
                // Also allow teachers from other departments
                ...allTeachers
                  .filter((t) => !deptTeachers.find((dt) => dt.id === t.id))
                  .map((t) => ({
                    value: t.id,
                    label: `${t.lastName} ${t.firstName}${t.patronymic ? ' ' + t.patronymic : ''} (ін. кафедра)`,
                  })),
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Import Modal ───────────────────────────────────────────── */}
      <Modal title="Імпорт штатного розпису з тексту" open={importModalOpen}
        onCancel={() => { setImportModalOpen(false); setImportText(''); setImportPreview([]); }}
        width={900}
        footer={[
          <Button key="cancel" onClick={() => setImportModalOpen(false)}>Скасувати</Button>,
          <Button key="parse" onClick={handleImportParse} disabled={!importText.trim()}>Розпізнати</Button>,
          <Button key="import" type="primary" onClick={handleImportSave} disabled={importPreview.length === 0}>
            Імпортувати ({importPreview.length} посад)
          </Button>,
        ]}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
          Вставте текст штатного розпису (скопійований з таблиці Word/Excel). Формат: № Посада ШПК ВОС Тариф ПІБ.
          Для дробових ставок додайте число після ПІБ (напр. "0,5").
        </Text>
        <TextArea rows={8} value={importText} onChange={(e) => setImportText(e.target.value)}
          placeholder="1Начальник кафедриПолковник530200249РЕДЗЮК Євгеній Володимирович&#10;2Заступник начальника кафедриПідполковник530200348ВАКАНТ&#10;..." />

        {importPreview.length > 0 && (() => {
          const matched = importPreview.filter((p) => p.teacherName !== 'ВАКАНТ' && matchTeacherByName(p.teacherName, allTeachers));
          const unmatched = importPreview.filter((p) => p.teacherName !== 'ВАКАНТ' && !matchTeacherByName(p.teacherName, allTeachers));
          const vacant = importPreview.filter((p) => p.teacherName === 'ВАКАНТ');
          return (
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <Title level={5} style={{ margin: 0 }}>Попередній перегляд ({importPreview.length} позицій)</Title>
                <Space size="middle">
                  {matched.length > 0 && <Text><CheckCircleOutlined style={{ color: '#52c41a' }} /> Знайдено: {matched.length}</Text>}
                  {unmatched.length > 0 && <Text><ExclamationCircleOutlined style={{ color: '#faad14' }} /> Не знайдено: {unmatched.length}</Text>}
                  {vacant.length > 0 && <Text><MinusCircleOutlined style={{ color: '#ff4d4f' }} /> Вакант: {vacant.length}</Text>}
                </Space>
              </div>
              <Table dataSource={importPreview.map((p, i) => ({ ...p, key: i }))} pagination={false} size="small"
                columns={[
                  { title: '№', key: 'num', width: 40, render: (_: unknown, __: ParsedPosition, idx: number) => idx + 1 },
                  { title: 'Посада', dataIndex: 'positionTitle' },
                  { title: 'ШПК', dataIndex: 'militaryRankCategory', width: 120 },
                  { title: 'ВОС', dataIndex: 'militarySpecialtyCode', width: 80 },
                  { title: 'Тариф', dataIndex: 'tariffGrade', width: 55 },
                  { title: 'Ставка', dataIndex: 'rate', width: 55, render: (v: number) => v < 1 ? <Tag>{v}</Tag> : null },
                  { title: 'ПІБ', dataIndex: 'teacherName', render: (v: string) => {
                    if (v === 'ВАКАНТ') return <Tag color="red">ВАКАНТ</Tag>;
                    const found = matchTeacherByName(v, allTeachers);
                    if (found) return <><CheckCircleOutlined style={{ color: '#52c41a', marginRight: 4 }} />{v}</>;
                    return <><ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 4 }} /><span style={{ color: '#faad14' }}>{v}</span></>;
                  }},
                ]}
              />
              <Text type="warning" style={{ display: 'block', marginTop: 8 }}>
                Увага: імпорт замінить поточний штатний розпис.{' '}
                <CheckCircleOutlined style={{ color: '#52c41a' }} /> — викладач знайдений і буде залінкований автоматично.{' '}
                <ExclamationCircleOutlined style={{ color: '#faad14' }} /> — викладач не знайдений в системі (потрібно залінкувати вручну).
              </Text>
            </div>
          );
        })()}
      </Modal>
    </div>
  );
};

export default OrgStructurePage;
