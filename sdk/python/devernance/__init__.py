"""Devernance: governance for AI systems, enforced where actions happen.

Quick start::

    from devernance import GovernanceClient

    governance = GovernanceClient(system="claims-agent")   # server and key from env or `devernance login`

    with governance.guard("payment.refund",
                          resource={"customer_id": customer_id},
                          context={"amount": amount, "currency": "INR"}) as decision:
        payment_service.refund(customer_id, amount, authorization=decision.token)

`guard` asks Devernance first. It raises `ActionDenied` when the policy says no, waits for (or raises
`ApprovalPending` on) a required human approval, runs the block only when authorized, and then reports whether
the action ran. If Devernance cannot be reached it raises `GovernanceUnavailable`: the action does not run.
"""

from .client import GovernanceClient
from .errors import (
    ActionDenied,
    ApprovalExpired,
    ApprovalPending,
    ApprovalRejected,
    GovernanceApiError,
    GovernanceError,
    GovernanceUnavailable,
)
from .models import Decision

__version__ = "0.1.0"

__all__ = [
    "ActionDenied",
    "ApprovalExpired",
    "ApprovalPending",
    "ApprovalRejected",
    "Decision",
    "GovernanceApiError",
    "GovernanceClient",
    "GovernanceError",
    "GovernanceUnavailable",
]
