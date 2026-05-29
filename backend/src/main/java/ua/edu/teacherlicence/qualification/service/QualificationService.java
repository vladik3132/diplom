package ua.edu.teacherlicence.qualification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualificationService {

    private final QualificationImprovementRepository repository;
    private final ApplicationEventPublisher events;

    public List<QualificationImprovement> findAll() {
        return repository.findAll();
    }

    public QualificationImprovement findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис підвищення кваліфікації не знайдено: " + id));
    }

    public List<QualificationImprovement> findByTeacherId(Long teacherId) {
        return repository.findByTeacherId(teacherId);
    }

    @Transactional
    public QualificationImprovement create(QualificationImprovement qi) {
        QualificationImprovement saved = repository.save(qi);
        if (saved.getTeacher() != null && saved.getTeacher().getId() != null) {
            events.publishEvent(new ComplianceEvents.QualificationChanged(saved.getTeacher().getId()));
        }
        return saved;
    }

    @Transactional
    public QualificationImprovement update(Long id, QualificationImprovement updated) {
        QualificationImprovement existing = findById(id);
        existing.setTitle(updated.getTitle());
        existing.setOrganization(updated.getOrganization());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setHours(updated.getHours());
        existing.setCredits(updated.getCredits());
        existing.setCertificateNumber(updated.getCertificateNumber());
        existing.setCertificateDate(updated.getCertificateDate());
        existing.setCertificateUrl(updated.getCertificateUrl());
        // Поля, які раніше пропускались під час update — через що при редагуванні
        // країна / категорія / рівень військового курсу не зберігались:
        existing.setCountry(updated.getCountry());
        existing.setCategory(updated.getCategory());
        existing.setMilitaryCourseLevel(updated.getMilitaryCourseLevel());
        QualificationImprovement saved = repository.save(existing);
        if (saved.getTeacher() != null && saved.getTeacher().getId() != null) {
            events.publishEvent(new ComplianceEvents.QualificationChanged(saved.getTeacher().getId()));
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Long tid = repository.findById(id)
                .map(q -> q.getTeacher() != null ? q.getTeacher().getId() : null)
                .orElse(null);
        repository.deleteById(id);
        if (tid != null) {
            events.publishEvent(new ComplianceEvents.QualificationChanged(tid));
        }
    }
}
