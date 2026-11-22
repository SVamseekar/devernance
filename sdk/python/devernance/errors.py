"""Every error the SDK raises. All of them mean: the governed action must not run."""


class GovernanceError(Exception):
    """Base class. Catch this to handle any governance failure."""


class GovernanceUnavailable(GovernanceError):
    """Devernance could not be reached or did not answer in time.

    The SDK fails closed: no answer is never treated as permission.
    """


class GovernanceApiError(GovernanceError):
    """Devernance answered with an HTTP error (bad request, missing permission, unknown system...)."""

    def __init__(self, status, message):
        super().__init__("HTTP %s: %s" % (status, message))
        self.status = status
        self.message = message


class _DecisionError(GovernanceError):
    def __init__(self, decision, message=None):
        super().__init__(message or decision.reason)
        self.decision = decision


class ActionDenied(_DecisionError, PermissionError):
    """The policy denied the action. `decision.reason` says why. Also a `PermissionError`."""


class ApprovalPending(_DecisionError):
    """A person must approve first and has not answered yet.

    Keep `decision.id`; call `GovernanceClient.guard(..., resume=decision.id)` once it is approved.
    """


class ApprovalRejected(_DecisionError, PermissionError):
    """The approver rejected the request."""


class ApprovalExpired(_DecisionError, PermissionError):
    """Nobody approved in time, or the approval was not used in time. Ask again."""
