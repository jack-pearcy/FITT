// Applies @NullMarked to every class in this package.
// All parameters and return types are non-null by default.
// Anything that can actually be null needs an explicit @Nullable annotation.
// This is what clears the JSpecify warnings on the handler method overrides.
@NullMarked
package com.fitt.shared.security;

import org.jspecify.annotations.NullMarked;
