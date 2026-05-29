package ua.edu.teacherlicence.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.user.model.User;

import ua.edu.teacherlicence.user.model.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByRoleAndTeacherIdIn(Role role, List<Long> teacherIds);

    Optional<User> findByTeacherId(Long teacherId);
}
