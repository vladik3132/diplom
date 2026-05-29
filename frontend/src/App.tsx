import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import ukUA from 'antd/locale/uk_UA';
import { AuthProvider } from './auth/AuthContext';
import LoginPage from './auth/LoginPage';
import KeycloakCallback from './auth/KeycloakCallback';
import ProtectedRoute from './auth/ProtectedRoute';
import MyProfileRedirect from './auth/MyProfileRedirect';
import AppLayout from './layout/AppLayout';
import DashboardPage from './pages/dashboard/DashboardPage';
import TeacherListPage from './pages/teachers/TeacherListPage';
import TeacherProfilePage from './pages/teachers/TeacherProfilePage';
import PublicationsPage from './pages/publications/PublicationsPage';
import AchievementsPage from './pages/achievements/AchievementsPage';
import ComplianceReportPage from './pages/achievements/ComplianceReportPage';
import DisciplineListPage from './pages/disciplines/DisciplineListPage';
import QualificationPage from './pages/qualification/QualificationPage';
import EditorialPlanPage from './pages/editorial/EditorialPlanPage';
import GanttPage from './pages/gantt/GanttPage';
import NotificationsPage from './pages/notifications/NotificationsPage';
import DocxConstructorPage from './pages/docx/DocxConstructorPage';
import AiAssistantPage from './pages/ai/AiAssistantPage';
import DataImportPage from './pages/import/DataImportPage';
import DepartmentListPage from './pages/departments/DepartmentListPage';
import DepartmentDetailPage from './pages/departments/DepartmentDetailPage';
import OrgStructurePage from './pages/org/OrgStructurePage';
import FakhoviJournalsPage from './pages/admin/FakhoviJournalsPage';
import EducationalProgramsPage from './pages/admin/EducationalProgramsPage';
import RatingPage from './pages/rating/RatingPage';
import RatingProtocolPage from './pages/rating/RatingProtocolPage';

const App: React.FC = () => {
  return (
    <ConfigProvider
      locale={ukUA}
      theme={{
        token: {
          colorPrimary: '#ea580c',
          colorLink: '#ea580c',
          colorBgLayout: '#f3f4f6',
          borderRadius: 8,
          fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
        },
      }}
    >
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/login/callback" element={<KeycloakCallback />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/teachers" element={<TeacherListPage />} />
              <Route path="/teachers/:id" element={<TeacherProfilePage />} />
              <Route path="/my-profile" element={<MyProfileRedirect />} />
              <Route path="/departments" element={<DepartmentListPage />} />
              <Route path="/departments/:id" element={<DepartmentDetailPage />} />
              <Route path="/org-structure" element={<OrgStructurePage />} />
              <Route path="/publications" element={<PublicationsPage />} />
              <Route path="/achievements" element={<AchievementsPage />} />
              <Route path="/compliance" element={<ComplianceReportPage />} />
              <Route path="/disciplines" element={<DisciplineListPage />} />
              <Route path="/qualification" element={<QualificationPage />} />
              <Route path="/editorial" element={<EditorialPlanPage />} />
              <Route path="/gantt" element={<GanttPage />} />
              <Route path="/docx" element={<DocxConstructorPage />} />
              <Route path="/ai" element={<AiAssistantPage />} />
              <Route path="/import" element={<DataImportPage />} />
              <Route path="/fakhovi-journals" element={<FakhoviJournalsPage />} />
              <Route path="/educational-programs" element={<EducationalProgramsPage />} />
              <Route path="/rating" element={<RatingPage />} />
              <Route path="/rating/protocol" element={<RatingProtocolPage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </ConfigProvider>
  );
};

export default App;
