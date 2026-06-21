package com.junnz.shared.ipc

object IpcProtocol {
    const val VERSION = 1

    // Watch → Phone message paths
    const val PATH_CAPTURE_START = "/capture/start"
    const val PATH_CAPTURE_AUDIO = "/capture/audio/"      // + sessionId
    const val PATH_CAPTURE_END = "/capture/end/"          // + sessionId
    const val PATH_ACTION = "/action/"                    // + reminderId

    // Phone → Watch message paths
    const val PATH_CAPTURE_TRANSCRIPT = "/capture/transcript/" // + sessionId
    const val PATH_FIRE = "/fire"

    // DataClient paths (state, last-write-wins)
    const val DATA_PATH_REMINDERS = "/reminders"

    // Message keys (within byte payload, JSON-encoded)
    const val KEY_SESSION_ID = "sessionId"
    const val KEY_PROTOCOL_VERSION = "protocolVersion"
    const val KEY_TRANSCRIPT = "transcript"
    const val KEY_INTENT = "intent"
    const val KEY_OUTCOME = "outcome"
    const val KEY_REMINDER_ID = "reminderId"
    const val KEY_ACTION = "action"
    const val KEY_REASON = "reason"
    const val KEY_REMINDER_LIST = "reminderList"
    const val KEY_FIRE_EVENT = "fireEvent"

    // Intent classification values
    const val INTENT_CREATE = "CREATE"
    const val INTENT_CONTEXT_ANNOUNCE = "CONTEXT_ANNOUNCE"
    const val INTENT_QUERY = "QUERY"
    const val INTENT_DISMISS = "DISMISS"
    const val INTENT_UNKNOWN = "UNKNOWN"

    // Outcome values
    const val OUTCOME_SAVED = "SAVED"
    const val OUTCOME_FIRED = "FIRED"
    const val OUTCOME_NO_MATCH = "NO_MATCH"
    const val OUTCOME_ERROR = "ERROR"
}
