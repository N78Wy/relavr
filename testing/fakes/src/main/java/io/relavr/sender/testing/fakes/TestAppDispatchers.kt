package io.relavr.sender.testing.fakes

import io.relavr.sender.core.common.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher

class TestAppDispatchers(
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val main: CoroutineDispatcher,
) : AppDispatchers
