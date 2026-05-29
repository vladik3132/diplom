import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, isTeacher, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Redirect teacher from root/dashboard to their profile
  if (isTeacher && user?.teacherId && (location.pathname === '/' || location.pathname === '/dashboard')) {
    return <Navigate to={`/teachers/${user.teacherId}`} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
