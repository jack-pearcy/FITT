package com.fitt.workouts.progression;

import com.fitt.workouts.domain.ProgressionModel;
import org.springframework.stereotype.Component;

import java.util.Map;

// Resolves the correct ProgressionStrategy at runtime using Spring's
// built-in map injection. Not if-else/switch.
@Component
public class ProgressionStrategyResolver {

    private final Map<String, ProgressionStrategy> strategies;

    // Spring injects all ProgressionStrategy beans automatically.
    // Map keys come from each bean's @Component name.
    public ProgressionStrategyResolver(Map<String, ProgressionStrategy> strategies) {
        this.strategies = strategies;
    }

    public ProgressionStrategy resolve(ProgressionModel model) {
        ProgressionStrategy strategy = strategies.get(model.name());
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for model: " + model);
        }
        return strategy;
    }
}
