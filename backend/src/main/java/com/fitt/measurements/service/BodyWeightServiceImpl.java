package com.fitt.measurements.service;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;
import com.fitt.measurements.repository.BodyWeightEntryRepository;
import com.fitt.measurements.service.exception.BodyWeightEntryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BodyWeightServiceImpl implements BodyWeightService {

    private final BodyWeightEntryRepository repository;

    public BodyWeightServiceImpl(BodyWeightEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public WeightLogResult logWeight(Long ownerUserId, LocalDate loggedAt, BigDecimal weight) {
        // Upsert, if an entry already exists for this user on this date, update it
        Optional<BodyWeightEntryEntity> existing =
                repository.findByOwnerUserIdAndLoggedAt(ownerUserId, loggedAt);

        if (existing.isPresent()) {
            BodyWeightEntryEntity entry = existing.get();
            entry.updateWeight(weight);
            return new WeightLogResult(repository.save(entry), false);
        }

        BodyWeightEntryEntity newEntry = new BodyWeightEntryEntity(ownerUserId, loggedAt, weight);
        return new WeightLogResult(repository.save(newEntry), true);
    }

    @Override
    public List<BodyWeightEntryEntity> getEntries(Long ownerUserId, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }

        LocalDate end = LocalDate.now();
        // minusDays(days - 1) so that days=7 covers today and the 6 days before it
        LocalDate start = end.minusDays(days - 1);

        return repository.findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                ownerUserId, start, end);
    }

    @Override
    public BigDecimal getSevenDayAverage(Long ownerUserId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        List<BodyWeightEntryEntity> entries =
                repository.findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                        ownerUserId, start, end);

        if (entries.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = entries.stream()
                .map(BodyWeightEntryEntity::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(entries.size()), 1, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void deleteEntry(Long ownerUserId, Long entryId) {
        BodyWeightEntryEntity entry = repository.findByIdAndOwnerUserId(entryId, ownerUserId)
                .orElseThrow(() -> new BodyWeightEntryNotFoundException(
                        "Body weight entry not found with id: " + entryId));

        repository.delete(entry);
    }
}
