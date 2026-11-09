package com.mauforge.paintlife.core.database

/**
 * Lifecycle of an exercise or quiet session. Stored by name, so constants must never be
 * renamed without a migration.
 */
enum class SessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,

    /** Ended by the user without completing (exercise reset, quiet cancel). */
    CANCELLED,

    /** Interrupted (e.g. reboot during quiet time). Never counts as completed. */
    INCOMPLETE,

    /**
     * Quiet time finished before 5:00 by the user (Finish early, or left paused for 30 minutes).
     * Its minutes are kept; it never counts as completed.
     */
    ENDED_EARLY,
}
