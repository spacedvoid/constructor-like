/*
 * Copyright 2025-2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
/**
 * Marks this function as a pseudo-constructor for Dokka.
 * Has no effect on source code.
 *
 * For more information, refer to this project on [github](https://github.com/spacedvoid/constructor-like).
 */
annotation class ConstructorLike
