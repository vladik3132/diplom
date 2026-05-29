import React from 'react';
import { Tag } from 'antd';
import { ComplianceStatus } from '../types/achievement';

interface ComplianceBadgeProps {
  status: ComplianceStatus;
}

const statusConfig: Record<ComplianceStatus, { color: string; label: string }> = {
  COMPLIANT: { color: 'green', label: 'Відповідає' },
  WARNING: { color: 'orange', label: 'Потребує уваги' },
  NON_COMPLIANT: { color: 'red', label: 'Не відповідає' },
  EXEMPT: { color: 'blue', label: 'Стаж НПП менше 3 років' },
};

const ComplianceBadge: React.FC<ComplianceBadgeProps> = ({ status }) => {
  const config = statusConfig[status];
  return <Tag color={config.color}>{config.label}</Tag>;
};

export default ComplianceBadge;
