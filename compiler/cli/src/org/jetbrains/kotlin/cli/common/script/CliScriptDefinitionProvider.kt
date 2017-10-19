/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.common.script

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.kotlin.script.StandardScriptDefinition
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CliScriptDefinitionProvider : ScriptDefinitionProvider {
    private val _definitions: MutableList<KotlinScriptDefinition> = arrayListOf(StandardScriptDefinition)
    private val definitionsLock = ReentrantReadWriteLock()

    override val definitions: List<KotlinScriptDefinition> = definitionsLock.read { _definitions }

    fun setScriptDefinitions(newDefinitions: List<KotlinScriptDefinition>) {
        definitionsLock.write {
            _definitions.clear()
            _definitions.addAll(newDefinitions)

            if (_definitions.isEmpty()) {
                _definitions.add(StandardScriptDefinition)
            }
        }
    }
}