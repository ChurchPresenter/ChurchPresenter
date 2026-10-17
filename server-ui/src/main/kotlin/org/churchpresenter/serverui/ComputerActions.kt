package org.churchpresenter.serverui

import org.churchpresenter.calendar.sync.JoinedComputer

/** What the card does about the other computers on its calendar. */
internal class ComputerActions(
    val computers: List<JoinedComputer>,
    val onAdd: () -> Unit,
    val onRemove: (String) -> Unit,
    val onMakeMain: (String) -> Unit,
    val onJoin: () -> Unit,
    val onTakeOver: () -> Unit,
)
