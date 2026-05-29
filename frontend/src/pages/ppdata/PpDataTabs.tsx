import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Tabs, Table, Button, Modal, Form, Input, Select, InputNumber, DatePicker, Space, Popconfirm, message, Card, Empty, Descriptions, Tag, List as AntList, Dropdown, Typography, Tooltip, Collapse, Drawer } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyCertificateOutlined, CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, HistoryOutlined, LoadingOutlined, QuestionCircleOutlined, InfoCircleOutlined, BulbOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import { parseDate, formatDate } from '@/utils/dateUtils';

import { PP_TABS } from '@/types/ppData';
import {
  DEGREE_TYPE_LABELS,
  ATTESTATION_ROLE_LABELS,
  EDITORIAL_ROLE_LABELS,
  EXPERT_COUNCIL_TYPE_LABELS,
  INTERNATIONAL_PROGRAM_LABELS,
  OLYMPIAD_LEVEL_LABELS,
  OLYMPIAD_ROLE_LABELS,
  PP14_ACTIVITY_TYPE_LABELS,
  MISSION_TYPE_LABELS,
  COMPETITION_SCOPE_LABELS,
} from '@/types/ppData';
import {
  getPpDataList, createPpData, updatePpData, deletePpData,
  validatePpData, validateSinglePpData,
  getPpDataValidationHistory, getPpDataValidationSession,
  getPpDataValidationStatuses,
  PpDataValidationResponse, PpDataValidationItem, PpDataValidationSession,
} from '@/api/ppDataApi';
import FileAttachmentList from '@/components/FileAttachmentList';
import type { FileAttachmentListRef } from '@/components/FileAttachmentList';

const { TextArea } = Input;

/** Map entityPath (kebab-case) to file attachment entityType constant */
const PP_ENTITY_TYPE_MAP: Record<string, string> = {
  'scientific-supervision': 'PP_SCIENTIFIC_SUPERVISION',
  'attestation-activity': 'PP_ATTESTATION_ACTIVITY',
  'editorial-activity': 'PP_EDITORIAL_ACTIVITY',
  'expert-council': 'PP_EXPERT_COUNCIL',
  'international-project': 'PP_INTERNATIONAL_PROJECT',
  'scientific-consulting': 'PP_SCIENTIFIC_CONSULTING',
  'foreign-language-teaching': 'PP_FOREIGN_LANGUAGE_TEACHING',
  'olympiad-guidance': 'PP_OLYMPIAD_GUIDANCE',
  'military-mission': 'PP_MILITARY_MISSION',
  'professional-association': 'PP_PROFESSIONAL_ASSOCIATION',
  'practical-experience': 'PP_PRACTICAL_EXPERIENCE',
};
const { Text } = Typography;

interface Props {
  teacherId: number;
}

// ── Конфiгурацiя полiв для кожного типу сутностi ──

interface FieldConfig {
  name: string;
  label: string;
  type: 'text' | 'textarea' | 'select' | 'date' | 'number';
  required?: boolean;
  options?: { value: string; label: string }[];
  width?: number;
  tableWidth?: number;
  showInTable?: boolean;
  renderDate?: boolean;
}

const enumToOptions = (labels: Record<string, string>) =>
  Object.entries(labels).map(([value, label]) => ({ value, label }));

const ENTITY_FIELDS: Record<string, FieldConfig[]> = {
  'scientific-supervision': [
    { name: 'studentName', label: 'ПIБ здобувача', type: 'text', required: true, showInTable: true },
    { name: 'topic', label: 'Тема дисертацii', type: 'textarea', showInTable: true },
    { name: 'defenseDate', label: 'Дата захисту', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'degreeType', label: 'Тип ступеня', type: 'select', options: enumToOptions(DEGREE_TYPE_LABELS), showInTable: true, tableWidth: 160 },
    { name: 'diplomaNumber', label: '№ диплому', type: 'text', showInTable: false, tableWidth: 140 },
  ],
  'attestation-activity': [
    { name: 'role', label: 'Роль', type: 'select', required: true, options: enumToOptions(ATTESTATION_ROLE_LABELS), showInTable: true, tableWidth: 180 },
    { name: 'councilName', label: 'Назва ради', type: 'text', showInTable: true },
    { name: 'studentName', label: 'ПІБ здобувача', type: 'text', showInTable: true },
    { name: 'defenseDate', label: 'Дата захисту', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateFrom', label: 'Початок членства (для постійної ради)', type: 'date', showInTable: false, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Кінець членства (для постійної ради)', type: 'date', showInTable: false, renderDate: true, tableWidth: 120 },
  ],
  'editorial-activity': [
    { name: 'role', label: 'Роль', type: 'select', required: true, options: enumToOptions(EDITORIAL_ROLE_LABELS), showInTable: true, tableWidth: 160 },
    { name: 'journalOrProjectName', label: 'Журнал/Проект', type: 'text', showInTable: true },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'description', label: 'Опис', type: 'textarea', showInTable: false },
  ],
  'expert-council': [
    { name: 'councilName', label: 'Назва ради', type: 'text', required: true, showInTable: true },
    { name: 'type', label: 'Тип', type: 'select', options: enumToOptions(EXPERT_COUNCIL_TYPE_LABELS), showInTable: true, tableWidth: 120 },
    { name: 'role', label: 'Роль', type: 'text', showInTable: true, tableWidth: 140 },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'orderNumber', label: '№ наказу', type: 'text', showInTable: false, tableWidth: 140 },
  ],
  'international-project': [
    { name: 'projectName', label: 'Назва проекту', type: 'text', required: true, showInTable: true },
    { name: 'program', label: 'Програма', type: 'select', options: enumToOptions(INTERNATIONAL_PROGRAM_LABELS), showInTable: true, tableWidth: 140 },
    { name: 'role', label: 'Роль', type: 'text', showInTable: true, tableWidth: 140 },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'description', label: 'Опис', type: 'textarea', showInTable: false },
  ],
  'scientific-consulting': [
    { name: 'organizationName', label: 'Органiзацiя', type: 'text', required: true, showInTable: true },
    { name: 'contractNumber', label: '№ договору', type: 'text', showInTable: true, tableWidth: 140 },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'yearsCount', label: 'Рокiв', type: 'number', showInTable: true, tableWidth: 80 },
  ],
  'foreign-language-teaching': [
    { name: 'disciplineName', label: 'Дисциплiна', type: 'text', required: true, showInTable: true },
    { name: 'language', label: 'Мова', type: 'text', showInTable: true, tableWidth: 120 },
    { name: 'hours', label: 'Годин', type: 'number', showInTable: true, tableWidth: 80 },
    { name: 'academicYear', label: 'Навч. рiк', type: 'text', showInTable: true, tableWidth: 120 },
    { name: 'semester', label: 'Семестр', type: 'number', showInTable: true, tableWidth: 80 },
  ],
  'olympiad-guidance': [
    { name: 'activityType', label: 'Тип дiяльностi', type: 'select', required: true, options: enumToOptions(PP14_ACTIVITY_TYPE_LABELS), showInTable: true, tableWidth: 180 },
    { name: 'competitionScope', label: 'Масштаб заходу', type: 'select', options: enumToOptions(COMPETITION_SCOPE_LABELS), showInTable: true, tableWidth: 140 },
    { name: 'role', label: 'Роль', type: 'select', options: enumToOptions(OLYMPIAD_ROLE_LABELS), showInTable: true, tableWidth: 140 },
    { name: 'level', label: 'Рiвень', type: 'select', options: enumToOptions(OLYMPIAD_LEVEL_LABELS), showInTable: true, tableWidth: 140 },
    { name: 'olympiadName', label: 'Назва олiмпiади/конкурсу/гуртка', type: 'text', showInTable: true },
    { name: 'studentName', label: 'ПIБ учасника', type: 'text', showInTable: false },
    { name: 'result', label: 'Результат', type: 'text', showInTable: true, tableWidth: 120 },
    { name: 'year', label: 'Рiк', type: 'number', showInTable: true, tableWidth: 80 },
    { name: 'academicYear', label: 'Навч. рiк (напр. 2023-2024)', type: 'text', showInTable: false, tableWidth: 120 },
    { name: 'departmentName', label: 'Кафедра', type: 'text', showInTable: false },
    { name: 'participantCount', label: 'К-сть учасникiв', type: 'number', showInTable: false, tableWidth: 80 },
    { name: 'orderNumber', label: '№ наказу', type: 'text', showInTable: false },
    { name: 'orderDate', label: 'Дата наказу', type: 'date', showInTable: false, renderDate: true },
    { name: 'description', label: 'Опис', type: 'textarea', showInTable: false },
  ],
  'military-mission': [
    { name: 'missionType', label: 'Тип мiсii', type: 'select', required: true, options: enumToOptions(MISSION_TYPE_LABELS), showInTable: true, tableWidth: 200 },
    { name: 'missionName', label: 'Назва мiсii', type: 'text', showInTable: true },
    { name: 'country', label: 'Краiна', type: 'text', showInTable: false, tableWidth: 120 },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
  ],
  'professional-association': [
    { name: 'organizationName', label: 'Органiзацiя', type: 'text', required: true, showInTable: true },
    { name: 'role', label: 'Роль', type: 'text', showInTable: true, tableWidth: 140 },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'certificateNumber', label: '№ сертифiката', type: 'text', showInTable: false, tableWidth: 140 },
  ],
  'practical-experience': [
    { name: 'organizationName', label: 'Органiзацiя', type: 'text', required: true, showInTable: true },
    { name: 'position', label: 'Посада', type: 'text', showInTable: true },
    { name: 'dateFrom', label: 'Дата з', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'dateTo', label: 'Дата по', type: 'date', showInTable: true, renderDate: true, tableWidth: 120 },
    { name: 'yearsCount', label: 'Рокiв', type: 'number', showInTable: true, tableWidth: 80 },
    { name: 'specialtyName', label: 'Спецiальнiсть', type: 'text', showInTable: false },
  ],
};

// ── Пiдказки та приклади для кожного типу ppData ──

interface PpHint {
  requirement: string;
  examples: string[];
  tips?: string[];
}

const PP_HINTS: Record<string, PpHint> = {
  'scientific-supervision': {
    requirement: 'пп.6 — Наукове керiвництво (консультування) здобувача, який одержав документ про присудження наукового ступеня.',
    examples: [
      'Iванов I.I., тема "Методи машинного навчання...", захист 15.03.2024, PhD',
      'Петренко А.В., тема "Кiберзахист критичної iнфраструктури...", захист 20.11.2023, доктор наук',
    ],
    tips: [
      'Обов\'язково вкажiть ПIБ здобувача та дату захисту',
      'Захист має бути пiдтверджений дипломом (вкажiть номер)',
    ],
  },
  'attestation-activity': {
    requirement: 'пп.7 — Участь в атестації наукових кадрів як офіційного опонента, рецензента, голови разової спецради, або члена постійної спеціалізованої вченої ради.',
    examples: [
      'Опонент, Спецрада Д 26.062.01, Іванов І.І., захист 10.05.2024',
      'Рецензент, Спецрада К 11.031.02, Петренко О.О., захист 15.03.2024',
      'Голова, разова спецрада, Сидоров В.В., захист 22.06.2024',
      'Член постійної спецради Д 26.062.05, період 2022—2024',
    ],
    tips: [
      'Член постійної спецради — 1 запису достатньо для виконання пп.7.',
      'Опонент / Рецензент / Голова — разові ролі, потрібно ≥3 в сумі.',
      'Для постійного членства вкажіть період (дати з/по), для разових — дату захисту.',
      'Вкажіть ПІБ здобувача (для разових ролей — обовʼязково).',
    ],
  },
  'editorial-activity': {
    requirement: 'пп.8 — Виконання функцiй наукового керiвника або вiдповiдального виконавця наукової теми (проекту), або головного редактора/члена редакцiйної колегii/експерта (рецензента) наукового видання, включеного до перелiку фахових видань Украiни, або iноземного наукового видання, що iндексується в бiблiографiчних базах.',
    examples: [
      'Головний редактор, "Системи озброєння i вiйськова технiка", 2020\u20142024',
      'Член редколегii, "Ukrainian Journal of Information Technology", 2022\u2014...',
      'Рецензент, "Journal of Cyber Security" (Scopus), 2023\u2014...',
      'Керiвник теми, НДР "Розробка системи кiберзахисту...", 2021\u20142023',
    ],
    tips: [
      'Видання має бути у перелiку фахових видань Украiни або iндексуватися в Scopus/Web of Science',
      'Система автоматично перевiряє наявнiсть видання в реєстрах',
      'Для керiвника теми/проекту вкажiть назву та термiни виконання',
    ],
  },
  'expert-council': {
    requirement: 'пп.9 — Робота у складi експертної ради з питань проведення експертизи дисертацiй МОН, НАЗЯВО, Акредитацiйної комiсii, науково-методичних комiсiй МОН, наукових/експертних рад органiв державної влади, комiсiй Державної служби якостi освiти.',
    examples: [
      'Експерт НАЗЯВО, акредитацiйна експертиза, 2022\u20142024',
      'Член науково-методичної комiсii МОН з iнформацiйних технологiй, 2021\u20142023',
      'Експерт Державної служби якостi освiти, планова перевiрка, 2023',
    ],
    tips: [
      'Вкажiть тип ради (МОН, НАЗЯВО, Акредитацiйна) та вашу роль',
      'Бажано вказати номер наказу про включення до складу ради',
    ],
  },
  'international-project': {
    requirement: 'пп.10 — Участь у мiжнародних наукових та/або освiтнiх проектах, залучення до мiжнародної експертизи, наявнiсть звання "суддя мiжнародної категорii".',
    examples: [
      'Erasmus+ KA2 "Digital Skills for Cyber Defence", учасник, 2022\u20142025',
      'NATO SPS "Hybrid Warfare Detection System", спiвкерiвник, 2023\u20142024',
      'Horizon Europe "AI for Security", дослiдник, 2021\u20142024',
      'Мiжнародний експерт з акредитацii, AQAS (Нiмеччина), 2023',
    ],
    tips: [
      'Вкажiть програму (Erasmus+, Horizon, NATO SPS, грант тощо)',
      'Мiжнародна експертиза також зараховується',
    ],
  },
  'scientific-consulting': {
    requirement: 'пп.11 — Наукове консультування пiдприємств, установ, органiзацiй не менше трьох рокiв на пiдставi договору iз закладом вищої освiти (науковою установою).',
    examples: [
      'ТОВ "Укртелеком", договiр №123 вiд 01.01.2020, 3 роки',
      'ДП "Укроборонпром", договiр №45 вiд 15.06.2019, 4 роки',
    ],
    tips: [
      'Мiнiмальний термiн консультування — 3 роки',
      'Обов\'язково вкажiть номер договору та перiод',
      'Договiр має бути укладений iз ЗВО, а не особисто з викладачем',
    ],
  },
  'foreign-language-teaching': {
    requirement: 'пп.13 — Проведення навчальних занять iз спецiальних дисциплiн iноземною мовою (крiм дисциплiн мовної пiдготовки) в обсязi не менше 50 аудиторних годин на навчальний рiк.',
    examples: [
      '"Network Security", англiйська, 72 години, 2023\u20142024 н.р., 1 семестр',
      '"Cryptography", англiйська, 54 години, 2023\u20142024 н.р., 2 семестр',
    ],
    tips: [
      'Мiнiмум 50 аудиторних годин на навчальний рiк',
      'Дисциплiни мовної пiдготовки (iноземна мова, переклад) НЕ зараховуються',
      'Вкажiть точну кiлькiсть годин та навчальний рiк',
    ],
  },
  'olympiad-guidance': {
    requirement: 'пп.14\u201415 — Керiвництво студентом-призером олiмпiад/конкурсiв, робота в оргкомiтетi/журi, керiвництво науковим гуртком; керiвництво школярем-призером олiмпiад МАН.',
    examples: [
      'Олiмпiада: керiвництво студентом, II етап Всеукраiнської олiмпiади з програмування, 1 мiсце, 2024',
      'Конкурс: TIDE NATO Hackathon, команда "CyberDefenders", призер, 2024',
      'Гурток: науковий гурток з кiбербезпеки, кафедра КIТ, 30 курсантiв, 2023\u20142024 н.р.',
      'МАН: керiвництво учнем, III етап Всеукраiнського конкурсу-захисту МАН, 2 мiсце, 2024',
    ],
    tips: [
      'Хакатони, турнiри, змагання з програмування зараховуються як конкурси',
      'Для гуртка вкажiть кафедру, к-сть учасникiв, навч. рiк та номер наказу',
      'Для олiмпiад/конкурсiв достатньо назви команди (ПIБ учасника не обов\'язкове)',
      'пп.15 (школярi/МАН) — оберiть рiвень "Шкiльна/МАН"',
    ],
  },
  'military-mission': {
    requirement: 'пп.17 — Участь у мiжнародних операцiях з пiдтримання миру i безпеки пiд егiдою ООН.\nпп.18 — Участь у мiжнародних вiйськових навчаннях (тренуваннях) за участю збройних сил краiн-членiв НАТО.',
    examples: [
      'Миротворча операцiя ООН, UNIFIL (Лiван), 2020\u20142021',
      'Навчання НАТО, Combined Resolve XVII (Нiмеччина), 2023',
      'Навчання НАТО, Rapid Trident 2024 (Украiна), 2024',
    ],
    tips: [
      'Для ВНЗ iз специфiчними умовами навчання та вiйськових пiдроздiлiв ЗВО',
      'Вкажiть тип мiсii, назву операцii/навчань та краiну проведення',
    ],
  },
  'professional-association': {
    requirement: 'пп.19 — Дiяльнiсть за спецiальнiстю у формi участi у професiйних та/або громадських об\'єднаннях.',
    examples: [
      'IEEE (Institute of Electrical and Electronics Engineers), член, з 2020',
      'ACM (Association for Computing Machinery), член, з 2019',
      'ГО "Украiнська асоцiацiя з iнформацiйної безпеки", член правлiння, з 2021',
    ],
    tips: [
      'Членство має бути дiючим (активним)',
      'Перевiрка актуальностi не прив\'язана до 5-рiчного перiоду — важлива сама наявнiсть членства',
      'Бажано вказати номер сертифiката/посвiдчення',
    ],
  },
  'practical-experience': {
    requirement: 'пп.20 — Досвiд практичної роботи за спецiальнiстю не менше п\'яти рокiв (крiм педагогiчної, науково-педагогiчної, науковоi дiяльностi).',
    examples: [
      'ТОВ "Iнфоком", iнженер з кiбербезпеки, 2010\u20142018 (8 рокiв)',
      'ДП "Спецзв\'язок", системний адмiнiстратор, 2015\u20142020 (5 рокiв)',
    ],
    tips: [
      'Мiнiмум 5 рокiв практичної роботи за спецiальнiстю',
      'Педагогiчна, науково-педагогiчна та наукова дiяльнiсть НЕ зараховується',
      'Рахується загальний стаж за всiма записами',
    ],
  },
};

// ── Компонент пiдказки ──

const PpHintPanel: React.FC<{ entityPath: string }> = ({ entityPath }) => {
  const hint = PP_HINTS[entityPath];
  if (!hint) return null;

  return (
    <Collapse
      size="small"
      style={{ marginBottom: 12 }}
      items={[{
        key: '1',
        label: (
          <Space size="small">
            <BulbOutlined style={{ color: '#1890ff' }} />
            <span style={{ color: '#1890ff', fontSize: 13 }}>Вимоги та приклади заповнення</span>
          </Space>
        ),
        children: (
          <div style={{ fontSize: 13 }}>
            <div style={{ marginBottom: 8 }}>
              <Text strong style={{ fontSize: 13 }}>
                <InfoCircleOutlined style={{ marginRight: 4 }} />
                Вимога:
              </Text>
              <div style={{ marginTop: 4, whiteSpace: 'pre-line', color: '#555' }}>{hint.requirement}</div>
            </div>

            <div style={{ marginBottom: 8 }}>
              <Text strong style={{ fontSize: 13 }}>Приклади:</Text>
              <ul style={{ margin: '4px 0 0 0', paddingLeft: 20, color: '#555' }}>
                {hint.examples.map((ex, i) => (
                  <li key={i} style={{ marginBottom: 2 }}>{ex}</li>
                ))}
              </ul>
            </div>

            {hint.tips && hint.tips.length > 0 && (
              <div>
                <Text strong style={{ fontSize: 13 }}>
                  <BulbOutlined style={{ marginRight: 4, color: '#faad14' }} />
                  Поради:
                </Text>
                <ul style={{ margin: '4px 0 0 0', paddingLeft: 20, color: '#555' }}>
                  {hint.tips.map((tip, i) => (
                    <li key={i} style={{ marginBottom: 2 }}>{tip}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        ),
      }]}
    />
  );
};

// ── Генерик CRUD-таблиця для одного типу ppData ──

interface ValidationStatus {
  status: string;
  reasoning: string;
}

interface PpDataTableProps {
  teacherId: number;
  entityPath: string;
  onSaveStart?: () => void;
  onEntryChanged?: () => void;
  validationStatuses: Record<string, ValidationStatus>;
  autoValidating: boolean;
}

const PpDataTable: React.FC<PpDataTableProps> = ({ teacherId, entityPath, onSaveStart, onEntryChanged, validationStatuses, autoValidating }) => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const fileListRef = useRef<FileAttachmentListRef>(null);

  const fields = ENTITY_FIELDS[entityPath] || [];

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getPpDataList(teacherId, entityPath);
      setData(result);
      // Sync drawer if open
      if (selectedRecord) {
        const updated = result.find((r: any) => r.id === selectedRecord.id);
        if (updated) setSelectedRecord(updated);
        else { setSelectedRecord(null); setDrawerOpen(false); }
      }
    } catch {
      message.error('Помилка завантаження даних');
    } finally {
      setLoading(false);
    }
  }, [teacherId, entityPath]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: Record<string, any> = {};
      for (const field of fields) {
        const val = values[field.name];
        if (field.type === 'date' && val) {
          payload[field.name] = val.format('DD.MM.YYYY');
        } else if (val !== undefined && val !== null) {
          payload[field.name] = val;
        }
      }
      if (values.documentUrl) payload.documentUrl = values.documentUrl;

      // Блокуємо кнопку ШI одразу при збереженнi
      onSaveStart?.();

      let savedEntity: any;
      if (editingId) {
        savedEntity = await updatePpData(teacherId, entityPath, editingId, payload);
        message.success('Запис оновлено');
      } else {
        savedEntity = await createPpData(teacherId, entityPath, payload);
        const pending = fileListRef.current?.getPendingFiles() || [];
        if (pending.length > 0 && PP_ENTITY_TYPE_MAP[entityPath]) {
          await fileListRef.current?.uploadPendingFiles(PP_ENTITY_TYPE_MAP[entityPath], savedEntity.id, teacherId);
        }
        message.success('Запис додано');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingId(null);
      loadData();

      // Автовалiдацiя пiсля збереження
      if (savedEntity?.id) {
        try {
          const result = await validateSinglePpData(teacherId, entityPath, savedEntity.id);
          if (result.status === 'OK') {
            message.success('ШI: запис вiдповiдає вимогам');
          } else if (result.status === 'WARNING') {
            message.warning(`ШI: ${result.reasoning}`, 6);
          } else {
            message.error(`ШI: ${result.reasoning}`, 8);
          }
        } catch {
          // ШI недоступний — не критично
        }
      }

      onEntryChanged?.();
    } catch {
      // validation error
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (record: any) => {
    setEditingId(record.id);
    const formValues: Record<string, any> = {};
    for (const field of fields) {
      if (field.type === 'date' && record[field.name]) {
        formValues[field.name] = parseDate(record[field.name]);
      } else {
        formValues[field.name] = record[field.name];
      }
    }
    formValues.documentUrl = record.documentUrl;
    form.setFieldsValue(formValues);
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePpData(teacherId, entityPath, id);
      message.success('Запис видалено');
      loadData();
      onEntryChanged?.();
    } catch {
      message.error('Помилка видалення');
    }
  };

  const getValidationForRecord = (record: any): ValidationStatus | null => {
    const key = `${entityPath}:${record.id}`;
    return validationStatuses[key] || null;
  };

  const columns: ColumnsType<any> = [
    ...fields
      .filter(f => f.showInTable)
      .map((field) => ({
        title: field.label,
        dataIndex: field.name,
        key: field.name,
        width: field.tableWidth,
        ellipsis: !field.tableWidth,
        render: field.renderDate
          ? (v: string) => formatDate(v)
          : field.options
          ? (v: string) => {
              const opt = field.options?.find(o => o.value === v);
              return opt?.label || v || '\u2014';
            }
          : undefined,
      })),
    {
      title: 'Статус ШI',
      key: '_validation_status',
      width: 100,
      align: 'center' as const,
      render: (_: unknown, record: any) => {
        if (autoValidating) {
          return <LoadingOutlined style={{ color: '#1890ff' }} />;
        }
        const vs = getValidationForRecord(record);
        if (!vs) return <Tooltip title="Не перевiрено"><QuestionCircleOutlined style={{ color: '#bbb', fontSize: 16 }} /></Tooltip>;
        const icon = vs.status === 'OK'
          ? <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
          : vs.status === 'WARNING'
          ? <WarningOutlined style={{ color: '#faad14', fontSize: 16 }} />
          : <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />;
        return <Tooltip title={vs.reasoning || statusLabel(vs.status)}>{icon}</Tooltip>;
      },
    },
    {
      title: 'Обгрунтування',
      key: '_validation_reasoning',
      width: 250,
      ellipsis: true,
      render: (_: unknown, record: any) => {
        if (autoValidating) {
          return <Text type="secondary" italic>Перевiрка...</Text>;
        }
        const vs = getValidationForRecord(record);
        if (!vs) return <Text type="secondary">\u2014</Text>;
        const color = vs.status === 'OK' ? '#52c41a' : vs.status === 'WARNING' ? '#faad14' : '#ff4d4f';
        return (
          <Tooltip title={vs.reasoning}>
            <Text style={{ color, fontSize: 12 }} ellipsis>
              {vs.reasoning || '\u2014'}
            </Text>
          </Tooltip>
        );
      },
    },
    {
      title: '', key: 'actions', width: 80,
      render: (_: unknown, record: any) => (
        <Space>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          <Popconfirm title="Видалити запис?" onConfirm={() => handleDelete(record.id)} okText="Так" cancelText="Нi">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ── Drawer ──────────────────────────────────────────────────────
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<any>(null);

  const renderFormFields = () => (
    <>
      {fields.map((field) => {
        switch (field.type) {
          case 'text':
            return (
              <Form.Item key={field.name} name={field.name} label={field.label}
                rules={field.required ? [{ required: true, message: `Введiть ${field.label.toLowerCase()}` }] : undefined}>
                <Input />
              </Form.Item>
            );
          case 'textarea':
            return (
              <Form.Item key={field.name} name={field.name} label={field.label}
                rules={field.required ? [{ required: true, message: `Введiть ${field.label.toLowerCase()}` }] : undefined}>
                <TextArea rows={2} />
              </Form.Item>
            );
          case 'select':
            return (
              <Form.Item key={field.name} name={field.name} label={field.label}
                rules={field.required ? [{ required: true, message: `Оберiть ${field.label.toLowerCase()}` }] : undefined}>
                <Select allowClear options={field.options} placeholder={`Оберiть ${field.label.toLowerCase()}`} />
              </Form.Item>
            );
          case 'date':
            return (
              <Form.Item key={field.name} name={field.name} label={field.label}
                rules={field.required ? [{ required: true, message: `Оберiть ${field.label.toLowerCase()}` }] : undefined}>
                <DatePicker format="DD.MM.YYYY" style={{ width: '100%' }} />
              </Form.Item>
            );
          case 'number':
            return (
              <Form.Item key={field.name} name={field.name} label={field.label}
                rules={field.required ? [{ required: true, message: `Введiть ${field.label.toLowerCase()}` }] : undefined}>
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            );
          default:
            return null;
        }
      })}
      <Form.Item name="documentUrl" label="Зовнішнє посилання" tooltip="Посилання на зовнішній ресурс (сайт НАЗЯВО, реєстр, портал). Файли завантажуйте через систему.">
        <Input placeholder="https://nazvo.gov.ua/..., https://mon.gov.ua/..." />
      </Form.Item>
    </>
  );

  return (
    <>
      <PpHintPanel entityPath={entityPath} />

      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingId(null); form.resetFields(); setModalOpen(true); }}>
          Додати запис
        </Button>
      </div>

      <Table
        dataSource={data}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={false}
        size="middle"
        locale={{ emptyText: <Empty description="Немає записiв" /> }}
        onRow={(record) => ({
          onClick: (e) => {
            if ((e.target as HTMLElement).closest('.ant-btn, .ant-popconfirm, .ant-dropdown')) return;
            setSelectedRecord(record);
            setDrawerOpen(true);
          },
          style: { cursor: 'pointer' },
        })}
      />

      <Modal
        title={editingId ? 'Редагувати запис' : 'Новий запис'}
        open={modalOpen}
        onOk={handleSave}
        confirmLoading={saving}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditingId(null); fileListRef.current?.clearPending(); }}
        okText="Зберегти"
        cancelText="Скасувати"
        width={600}
      >
        <div style={{ marginBottom: 12, borderBottom: '1px solid #f0f0f0', paddingBottom: 12 }}>
          <Text strong style={{ fontSize: 13 }}>Прикріплені файли</Text>
          <FileAttachmentList
            ref={fileListRef}
            entityType={PP_ENTITY_TYPE_MAP[entityPath] || entityPath}
            entityId={editingId}
            teacherId={teacherId}
          />
        </div>
        <Form form={form} layout="vertical">
          {renderFormFields()}
        </Form>
      </Modal>

      <Drawer
        title="Деталі запису"
        placement="right"
        width={480}
        open={drawerOpen}
        onClose={() => { setDrawerOpen(false); setSelectedRecord(null); }}
      >
        {selectedRecord && (
          <>
            <Descriptions column={1} size="small" bordered>
              {fields.map((field) => {
                const val = selectedRecord[field.name];
                if (val === undefined || val === null || val === '') return null;
                let displayVal: React.ReactNode = val;
                if (field.type === 'date' && val) {
                  displayVal = formatDate(val as string);
                } else if (field.options && val) {
                  const opt = field.options.find((o: { value: string; label: string }) => o.value === val);
                  displayVal = opt?.label || (val as string);
                }
                return (
                  <Descriptions.Item key={field.name} label={field.label}>
                    {displayVal}
                  </Descriptions.Item>
                );
              })}
              {selectedRecord.documentUrl && (
                <Descriptions.Item label="Зовнішнє посилання">
                  <a href={selectedRecord.documentUrl} target="_blank" rel="noopener noreferrer">
                    {selectedRecord.documentUrl.length > 50
                      ? selectedRecord.documentUrl.substring(0, 50) + '...'
                      : selectedRecord.documentUrl}
                  </a>
                </Descriptions.Item>
              )}
            </Descriptions>

            {PP_ENTITY_TYPE_MAP[entityPath] && (
              <div style={{ marginTop: 16 }}>
                <Text strong>Прикріплені файли</Text>
                <FileAttachmentList
                  entityType={PP_ENTITY_TYPE_MAP[entityPath]}
                  entityId={selectedRecord.id}
                  teacherId={teacherId}
                  editable={false}
                />
              </div>
            )}
          </>
        )}
      </Drawer>
    </>
  );
};

// ── Iконки та кольори статусiв ──

const StatusIcon: React.FC<{ status: string }> = ({ status }) => {
  switch (status) {
    case 'OK': return <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />;
    case 'WARNING': return <WarningOutlined style={{ color: '#faad14', fontSize: 18 }} />;
    case 'ERROR': return <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />;
    default: return null;
  }
};

const statusColor = (s: string) => s === 'OK' ? 'green' : s === 'WARNING' ? 'orange' : s === 'ERROR' ? 'red' : 'default';
const statusLabel = (s: string) => s === 'OK' ? 'Вiдповiдає' : s === 'WARNING' ? 'Зауваження' : s === 'ERROR' ? 'Не вiдповiдає' : s;

// ── Головний компонент ──

const PpDataTabs: React.FC<Props> = ({ teacherId }) => {
  const [validating, setValidating] = useState(false);
  const [autoValidating, setAutoValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<PpDataValidationResponse | null>(null);
  const [validationModalOpen, setValidationModalOpen] = useState(false);

  // Статуси валiдацii для таблиць
  const [validationStatuses, setValidationStatuses] = useState<Record<string, { status: string; reasoning: string }>>({});

  // Iсторiя
  const [historySessions, setHistorySessions] = useState<PpDataValidationSession[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const loadStatuses = useCallback(async () => {
    try {
      const statuses = await getPpDataValidationStatuses(teacherId);
      setValidationStatuses(statuses);
    } catch {
      // silently fail
    }
  }, [teacherId]);

  const loadHistory = useCallback(async () => {
    try {
      const sessions = await getPpDataValidationHistory(teacherId);
      setHistorySessions(sessions);
    } catch {
      // silently fail
    }
  }, [teacherId]);

  useEffect(() => { loadHistory(); loadStatuses(); }, [loadHistory, loadStatuses]);

  const handleValidate = async () => {
    setValidating(true);
    try {
      const result = await validatePpData(teacherId);
      setValidationResult(result);
      setValidationModalOpen(true);
      loadHistory();
      loadStatuses();
    } catch {
      message.error('ШI-валiдацiя недоступна');
    } finally {
      setValidating(false);
    }
  };

  // Блокуємо кнопку ОДРАЗУ при натисканнi "Зберегти" (до закриття модалки)
  const handleSaveStart = useCallback(() => {
    setAutoValidating(true);
  }, []);

  // Callback пiсля змiни запису — перезавантажити статуси
  const handleEntryChanged = useCallback(async () => {
    // Невелика затримка, щоб дати час ШI зберегти результат
    setTimeout(async () => {
      await loadStatuses();
      await loadHistory();
      setAutoValidating(false);
    }, 1500);
  }, [loadStatuses, loadHistory]);

  const handleLoadSession = async (sessionId: string) => {
    setHistoryLoading(true);
    try {
      const result = await getPpDataValidationSession(teacherId, sessionId);
      setValidationResult(result);
      setValidationModalOpen(true);
    } catch {
      message.error('Не вдалося завантажити сесiю');
    } finally {
      setHistoryLoading(false);
    }
  };

  const historyMenuItems: MenuProps['items'] = historySessions.length > 0
    ? historySessions.map((s) => {
        const date = dayjs(s.validatedAt).format('DD.MM.YYYY HH:mm');
        const icon = s.errorCount > 0 ? '\u274C' : s.warningCount > 0 ? '\u26A0\uFE0F' : '\u2705';
        return {
          key: s.sessionId,
          label: (
            <Space size="small">
              <span>{icon}</span>
              <span>{date}</span>
              <Tag color="green" style={{ margin: 0 }}>{s.validCount}</Tag>
              {s.warningCount > 0 && <Tag color="orange" style={{ margin: 0 }}>{s.warningCount}</Tag>}
              {s.errorCount > 0 && <Tag color="red" style={{ margin: 0 }}>{s.errorCount}</Tag>}
            </Space>
          ),
        };
      })
    : [{ key: 'empty', label: 'Немає попереднiх перевiрок', disabled: true }];

  const tabItems = PP_TABS.map((tab) => ({
    key: tab.key,
    label: tab.label,
    children: (
      <PpDataTable
        teacherId={teacherId}
        entityPath={tab.entityPath}
        onSaveStart={handleSaveStart}
        onEntryChanged={handleEntryChanged}
        validationStatuses={validationStatuses}
        autoValidating={autoValidating}
      />
    ),
  }));

  return (
    <Card
      size="small"
      extra={
        <Space>
          <Button
            icon={validating ? <LoadingOutlined /> : <SafetyCertificateOutlined />}
            onClick={handleValidate}
            loading={validating}
            disabled={autoValidating}
          >
            {validating ? 'Перевiряю...' : autoValidating ? 'Автоперевiрка...' : 'Перевiрити з ШI'}
          </Button>
          <Dropdown
            menu={{
              items: historyMenuItems,
              onClick: ({ key }) => {
                if (key !== 'empty') handleLoadSession(key);
              },
            }}
            trigger={['click']}
          >
            <Button icon={<HistoryOutlined />} loading={historyLoading}>
              Iсторiя ({historySessions.length})
            </Button>
          </Dropdown>
        </Space>
      }
    >
      <Tabs
        items={tabItems}
        size="small"
        tabPosition="left"
        style={{ minHeight: 400 }}
      />

      <Modal
        title={
          <Space>
            <span>Результати перевiрки даних п.38</span>
            {validationResult?.sessionId && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {validationResult.sessionId}
                {validationResult.validatedAt && ` | ${dayjs(validationResult.validatedAt).format('DD.MM.YYYY HH:mm')}`}
              </Text>
            )}
          </Space>
        }
        open={validationModalOpen}
        onCancel={() => setValidationModalOpen(false)}
        footer={null}
        width={720}
      >
        {validationResult && (
          <>
            <Space style={{ marginBottom: 16 }}>
              <Tag color="green">Вiдповiдає: {validationResult.validCount}</Tag>
              <Tag color="orange">Зауваження: {validationResult.warningCount}</Tag>
              <Tag color="red">Не вiдповiдає: {validationResult.errorCount}</Tag>
              <Tag>Всього: {validationResult.totalChecked}</Tag>
            </Space>

            {validationResult.totalChecked === 0 ? (
              <Empty description="Немає даних для перевiрки" />
            ) : (
              <AntList
                dataSource={validationResult.items}
                renderItem={(item: PpDataValidationItem) => (
                  <AntList.Item>
                    <AntList.Item.Meta
                      avatar={<StatusIcon status={item.status} />}
                      title={
                        <Space>
                          <Tag color="blue">пп.{item.ppNumber}</Tag>
                          <span>{item.ppLabel}</span>
                          <Tag color={statusColor(item.status)}>{statusLabel(item.status)}</Tag>
                        </Space>
                      }
                      description={
                        <div>
                          <div style={{ color: '#666', marginBottom: 4 }}>{item.entitySummary}</div>
                          <div style={{ whiteSpace: 'pre-wrap' }}>{item.reasoning}</div>
                        </div>
                      }
                    />
                  </AntList.Item>
                )}
              />
            )}
          </>
        )}
      </Modal>
    </Card>
  );
};

export default PpDataTabs;
