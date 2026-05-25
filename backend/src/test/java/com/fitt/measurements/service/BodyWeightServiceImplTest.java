package com.fitt.measurements.service;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;
import com.fitt.measurements.repository.BodyWeightEntryRepository;
import com.fitt.measurements.service.exception.BodyWeightEntryNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Service test for BodyWeightServiceImpl. BodyWeightEntryRepository is mocked
// because it reaches JPA and has its own logic. Service logic stays under test:
// upsert branching, days validation, average rounding, ownership-safe delete.
@ExtendWith(MockitoExtension.class)
class BodyWeightServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long ENTRY_ID = 42L;

    @Mock
    private BodyWeightEntryRepository repository;

    @InjectMocks
    private BodyWeightServiceImpl service;

    @Test
    void logWeight_noExistingEntry_savesNewWithCreatedTrue() {
        LocalDate today = LocalDate.of(2026, 5, 25);
        BigDecimal weight = new BigDecimal("80.5");
        when(repository.findByOwnerUserIdAndLoggedAt(USER_ID, today))
                .thenReturn(Optional.empty());
        when(repository.save(any(BodyWeightEntryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WeightLogResult result = service.logWeight(USER_ID, today, weight);

        ArgumentCaptor<BodyWeightEntryEntity> captor =
                ArgumentCaptor.forClass(BodyWeightEntryEntity.class);
        verify(repository).save(captor.capture());
        BodyWeightEntryEntity saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getLoggedAt()).isEqualTo(today);
        assertThat(saved.getWeight()).isEqualByComparingTo("80.5");

        assertThat(result.created()).isTrue();
        assertThat(result.entry()).isSameAs(saved);
    }

    @Test
    void logWeight_existingEntry_updatesAndReturnsCreatedFalse() {
        LocalDate today = LocalDate.of(2026, 5, 25);
        BodyWeightEntryEntity existing = new BodyWeightEntryEntity(
                USER_ID, today, new BigDecimal("80.0"));
        when(repository.findByOwnerUserIdAndLoggedAt(USER_ID, today))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        BigDecimal newWeight = new BigDecimal("82.5");
        WeightLogResult result = service.logWeight(USER_ID, today, newWeight);

        // Existing entity mutated in place via updateWeight.
        assertThat(existing.getWeight()).isEqualByComparingTo("82.5");
        assertThat(result.created()).isFalse();
        assertThat(result.entry()).isSameAs(existing);
    }

    @Test
    void getEntries_daysLessThanOne_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getEntries(USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    void getEntries_daysGreaterThan365_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getEntries(USER_ID, 366))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    void getEntries_validDays_delegatesToRepositoryWithCorrectDateRange() {
        when(repository.findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                any(), any(), any()))
                .thenReturn(List.of());

        service.getEntries(USER_ID, 7);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                eq(USER_ID), startCaptor.capture(), endCaptor.capture());

        // days=7 means today plus the 6 days before it, so start = end.minusDays(6).
        LocalDate start = startCaptor.getValue();
        LocalDate end = endCaptor.getValue();
        assertThat(start).isEqualTo(end.minusDays(6));
    }

    @Test
    void getSevenDayAverage_noEntries_returnsZero() {
        when(repository.findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                any(), any(), any()))
                .thenReturn(List.of());

        BigDecimal result = service.getSevenDayAverage(USER_ID);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getSevenDayAverage_threeEntries_returnsAverageRoundedHalfUpToOneDecimal() {
        // 80.0 + 81.5 + 82.6 = 244.1, divided by 3 = 81.3666..., HALF_UP to 1 decimal = 81.4
        BodyWeightEntryEntity e1 = new BodyWeightEntryEntity(
                USER_ID, LocalDate.of(2026, 5, 25), new BigDecimal("80.0"));
        BodyWeightEntryEntity e2 = new BodyWeightEntryEntity(
                USER_ID, LocalDate.of(2026, 5, 24), new BigDecimal("81.5"));
        BodyWeightEntryEntity e3 = new BodyWeightEntryEntity(
                USER_ID, LocalDate.of(2026, 5, 23), new BigDecimal("82.6"));
        when(repository.findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
                any(), any(), any()))
                .thenReturn(List.of(e1, e2, e3));

        BigDecimal result = service.getSevenDayAverage(USER_ID);

        assertThat(result).isEqualByComparingTo("81.4");
    }

    @Test
    void deleteEntry_entryExists_callsRepositoryDelete() {
        BodyWeightEntryEntity entry = new BodyWeightEntryEntity(
                USER_ID, LocalDate.of(2026, 5, 25), new BigDecimal("80.0"));
        ReflectionTestUtils.setField(entry, "id", ENTRY_ID);
        when(repository.findByIdAndOwnerUserId(ENTRY_ID, USER_ID))
                .thenReturn(Optional.of(entry));

        service.deleteEntry(USER_ID, ENTRY_ID);

        verify(repository).delete(entry);
    }

    @Test
    void deleteEntry_entryNotFound_throwsBodyWeightEntryNotFoundException() {
        when(repository.findByIdAndOwnerUserId(ENTRY_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEntry(USER_ID, ENTRY_ID))
                .isInstanceOf(BodyWeightEntryNotFoundException.class)
                .hasMessageContaining(String.valueOf(ENTRY_ID));

        verify(repository, never()).delete(any(BodyWeightEntryEntity.class));
    }
}