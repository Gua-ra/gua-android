#! /bin/bash

# Copyright (c) 2025 Element Creations Ltd.
# Copyright 2023-2024 New Vector Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Format is:
# gua://open/{sessionId} to open a session
# gua://open/{sessionId}/{roomId} to open a room
# gua://open/{sessionId}/{roomId}/{eventId} to open a thread

# Open a session
# adb shell am start -a android.intent.action.VIEW -d gua://open/@alice
# Open a room
adb shell am start -a android.intent.action.VIEW -d gua://open/@alice/!exampleRoomId
# Open a thread
# adb shell am start -a android.intent.action.VIEW -d gua://open/@alice/!exampleRoomId/\\\$threadId
