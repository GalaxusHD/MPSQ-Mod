package de.galaxushd.mpsqcamera;

import java.util.UUID;

/** One centrally stored task. Completion ticks deliberately stay local. */
public record TeamTodo(UUID id, String text, TeamTodoList list, boolean done) { }
