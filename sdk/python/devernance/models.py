"""The answer to an authorization request."""


class Decision:
    """One authorization decision.

    Attributes:
        id: decision id (None for a dry run).
        status: "ALLOW", "DENY" or "REVIEW". What the policy answered.
        authorized: True when the action may run now. This is the field to act on: a REVIEW decision
            becomes authorized after approval, and a policy in monitor mode authorizes everything.
        enforced: False when the policy runs in monitor mode.
        reason: why, in words.
        rule: which policy rule decided.
        token: signed proof of the authorization, to pass to the service that carries the action out.
            None unless authorized.
        approval: the approval request (dict) for a REVIEW decision, else None.
        raw: the full JSON answer.
    """

    def __init__(self, raw):
        self.raw = raw
        self.id = raw.get("id")
        self.status = raw.get("status")
        self.authorized = bool(raw.get("authorized"))
        self.enforced = bool(raw.get("enforced", True))
        self.reason = raw.get("reason") or ""
        self.rule = raw.get("rule")
        self.token = raw.get("authorizationToken")
        self.approval = raw.get("approval")
        self.policy = raw.get("policy")
        self.execution = raw.get("execution")

    @property
    def approval_status(self):
        """"PENDING", "APPROVED", "REJECTED", "EXPIRED", or None when no approval is involved."""
        return (self.approval or {}).get("status")

    def __repr__(self):
        return "Decision(id=%r, status=%r, authorized=%r, reason=%r)" % (
            self.id, self.status, self.authorized, self.reason)
