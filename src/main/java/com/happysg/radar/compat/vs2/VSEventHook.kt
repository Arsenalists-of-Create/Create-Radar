package com.happysg.radar.compat.vs2

import org.valkyrienskies.mod.common.assembly.VSAssemblyEvents
import org.valkyrienskies.mod.common.assembly.VSSchematicEvents

object RadarVSEventHooks {

    fun init() {

        VSAssemblyEvents.beforeCopy.on { e ->

            VSAssemblySuppression.begin(e.level)
        }

        VSAssemblyEvents.onPasteAfterBlocksAreLoaded.on { e ->
            VSAssemblySuppression.end(e.level)
        }

        VSSchematicEvents.onPasteBeforeBlocksAreLoaded.on { e ->
            VSAssemblySuppression.begin(e.level)
        }

        VSSchematicEvents.onPasteAfterBlocksAreLoaded.on { e ->
            VSAssemblySuppression.end(e.level)
        }
    }
}
