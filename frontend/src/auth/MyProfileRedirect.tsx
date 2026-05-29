import React from 'react';
import { Navigate } from 'react-router-dom';
import { Result } from 'antd';
import { useAuth } from './AuthContext';

/**
 * Маршрут /my-profile — для пунктів меню "Мій профіль" у TEACHER/HEAD.
 * Редіректить на /teachers/{teacherId}, або показує повідомлення якщо
 * акаунт ще не привʼязаний до картки викладача.
 */
const MyProfileRedirect: React.FC = () => {
  const { user } = useAuth();

  if (user?.teacherId) {
    return <Navigate to={`/teachers/${user.teacherId}`} replace />;
  }

  return (
    <Result
      status="warning"
      title="Ваш обліковий запис не привʼязаний до картки викладача"
      subTitle={
        'Обліковий запис у Keycloak існує, але в БД системи не вдалося автоматично знайти ' +
        'відповідну картку викладача за прізвищем + імʼям. Це може статися якщо ви були ' +
        'ADMIN до зміни ролі, або картка викладача ще не була створена.\n' +
        'Зверніться до адміністратора — він прив’яже акаунт через адмін-API ' +
        'або перелогіньтесь після того як адмін додасть/перейменує вашу картку.'
      }
    />
  );
};

export default MyProfileRedirect;
