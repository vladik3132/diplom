package ua.edu.teacherlicence.achievement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final ua.edu.teacherlicence.achievement.repository.AchievementRepository achievementRepository;
    private final ApplicationEventPublisher events;

    public List<Achievement> findAll() {
        return achievementRepository.findAll();
    }

    public List<Achievement> findByTeacherId(Long teacherId) {
        return achievementRepository.findByTeacherId(teacherId);
    }

    public List<Achievement> findByTeacherIdAndType(Long teacherId, AchievementType type) {
        return achievementRepository.findByTeacherIdAndAchievementType(teacherId, type);
    }

    public Achievement findById(Long id) {
        return achievementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Досягнення не знайдено: " + id));
    }

    @Transactional
    public Achievement save(Achievement achievement) {
        Achievement saved = achievementRepository.save(achievement);
        if (saved.getTeacher() != null && saved.getTeacher().getId() != null) {
            events.publishEvent(new ComplianceEvents.AchievementChanged(saved.getTeacher().getId()));
        }
        return saved;
    }

    @Transactional
    public void deleteById(Long id) {
        Long teacherId = achievementRepository.findById(id)
                .map(a -> a.getTeacher() != null ? a.getTeacher().getId() : null)
                .orElse(null);
        achievementRepository.deleteById(id);
        if (teacherId != null) {
            events.publishEvent(new ComplianceEvents.AchievementChanged(teacherId));
        }
    }
}
