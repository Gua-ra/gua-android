/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import coil3.ImageLoader

/** Loads provider media through the same restricted client as the provider API. */
interface GifImageLoader {
    val imageLoader: ImageLoader
}
