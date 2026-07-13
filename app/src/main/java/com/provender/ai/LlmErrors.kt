package com.provender.ai

/** The model isn't usable yet: not downloaded, still downloading, or device incapable. */
class ModelNotReadyException(message: String) : IllegalStateException(message)

/** The model produced unparseable output even after the single allowed retry. */
class MalformedModelOutputException(message: String) : IllegalStateException(message)
