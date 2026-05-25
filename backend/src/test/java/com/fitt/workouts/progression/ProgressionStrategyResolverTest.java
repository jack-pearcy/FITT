package com.fitt.workouts.progression;

import com.fitt.workouts.domain.ProgressionModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Plain unit test. No Spring, no DB.
// Strategy collaborators mocked because the resolver only checks identity,
// so the test stays decoupled from any concrete strategy implementation.
class ProgressionStrategyResolverTest {

    @Test
    void resolve_doubleProgression_returnsDoubleProgressionStrategy() {
        ProgressionStrategy doubleStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy linearStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy rpeStrategy = mock(ProgressionStrategy.class);

        ProgressionStrategyResolver resolver = new ProgressionStrategyResolver(Map.of(
                "DOUBLE_PROGRESSION", doubleStrategy,
                "LINEAR_PERIODIZATION", linearStrategy,
                "RPE_AUTOREGULATION", rpeStrategy
        ));

        ProgressionStrategy resolved = resolver.resolve(ProgressionModel.DOUBLE_PROGRESSION);

        assertThat(resolved).isSameAs(doubleStrategy);
    }

    @Test
    void resolve_linearPeriodization_returnsLinearPeriodizationStrategy() {
        ProgressionStrategy doubleStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy linearStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy rpeStrategy = mock(ProgressionStrategy.class);

        ProgressionStrategyResolver resolver = new ProgressionStrategyResolver(Map.of(
                "DOUBLE_PROGRESSION", doubleStrategy,
                "LINEAR_PERIODIZATION", linearStrategy,
                "RPE_AUTOREGULATION", rpeStrategy
        ));

        ProgressionStrategy resolved = resolver.resolve(ProgressionModel.LINEAR_PERIODIZATION);

        assertThat(resolved).isSameAs(linearStrategy);
    }

    @Test
    void resolve_rpeAutoregulation_returnsRpeAutoregulationStrategy() {
        ProgressionStrategy doubleStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy linearStrategy = mock(ProgressionStrategy.class);
        ProgressionStrategy rpeStrategy = mock(ProgressionStrategy.class);

        ProgressionStrategyResolver resolver = new ProgressionStrategyResolver(Map.of(
                "DOUBLE_PROGRESSION", doubleStrategy,
                "LINEAR_PERIODIZATION", linearStrategy,
                "RPE_AUTOREGULATION", rpeStrategy
        ));

        ProgressionStrategy resolved = resolver.resolve(ProgressionModel.RPE_AUTOREGULATION);

        assertThat(resolved).isSameAs(rpeStrategy);
    }

    @Test
    void resolve_modelNotRegistered_throwsIllegalArgumentException() {
        ProgressionStrategyResolver resolver = new ProgressionStrategyResolver(Map.of());

        assertThatThrownBy(() -> resolver.resolve(ProgressionModel.DOUBLE_PROGRESSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOUBLE_PROGRESSION");
    }
}