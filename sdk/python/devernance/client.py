"""HTTP client for the Devernance governance API. Standard library only."""

import contextlib
import functools
import json
import logging
import time
import urllib.error
import urllib.parse
import urllib.request

from . import config
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

log = logging.getLogger("devernance")

_PREFIX = "/api/v1/governance"


class GovernanceClient:
    """Talks to one Devernance server as one workload.

    Args:
        server: base URL, e.g. https://governance.company.com. Default: DEVERNANCE_SERVER or `devernance login`.
        api_key: an API key. Use a RUNTIME-scoped key in agents, a RELEASE_GATE key in CI. Default:
            DEVERNANCE_API_KEY or `devernance login`. The key's name is recorded as the agent identity.
        system: default AI system name for runtime calls, e.g. "claims-agent".
        model: the model this workload runs ("name@version"). Sent with each request so the policy's approved
            model list is checked.
        environment: deployment environment name. Default "production".
        timeout: seconds to wait for an answer before failing closed.
    """

    def __init__(self, server=None, api_key=None, system=None, model=None, environment=None, timeout=10.0):
        self.server, self.api_key = config.resolve(server, api_key)
        if not self.server:
            raise GovernanceError(
                "No Devernance server configured. Pass server=, set DEVERNANCE_SERVER, or run `devernance login`.")
        if not self.api_key:
            raise GovernanceError(
                "No API key configured. Pass api_key=, set DEVERNANCE_API_KEY, or run `devernance login`.")
        self.system = system
        self.model = model
        self.environment = environment
        self.timeout = timeout

    # ---- transport ------------------------------------------------------------------------------------

    def request(self, method, path, body=None, query=None, authenticated=True):
        """One HTTP call; returns the parsed JSON answer. Raises GovernanceApiError or GovernanceUnavailable."""
        url = self.server + path
        if query:
            pairs = [(key, _query_value(value)) for key, value in query.items() if value is not None]
            if pairs:
                url += "?" + urllib.parse.urlencode(pairs)
        data = None
        headers = {"Accept": "application/json", "User-Agent": "devernance-sdk/0.1"}
        if authenticated:
            headers["X-Api-Key"] = self.api_key
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        http_request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(http_request, timeout=self.timeout) as response:
                text = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            raise GovernanceApiError(error.code, _error_message(error)) from None
        except (urllib.error.URLError, OSError, TimeoutError) as error:
            raise GovernanceUnavailable("Devernance did not answer at %s: %s" % (self.server, error)) from None
        if not text:
            return None
        try:
            return json.loads(text)
        except ValueError:
            raise GovernanceUnavailable("Devernance sent an answer that is not JSON") from None

    # ---- enforce at runtime ---------------------------------------------------------------------------

    def authorize(self, action, resource=None, context=None, system=None, model=None, environment=None,
                  dry_run=False):
        """Asks whether `action` may run. Returns a Decision; never raises for a DENY (see `guard` for that).

        resource: what is acted on, e.g. {"customer_id": "CUST-102"}.
        context: trusted facts about the request, e.g. {"amount": 75000, "authorized_customer_id": "CUST-102"}.
            Fill it from your application's own state, never from model output.
        """
        body = {
            "system": self._system(system),
            "action": action,
            "resource": resource or {},
            "context": context or {},
        }
        if model or self.model:
            body["model"] = model or self.model
        if environment or self.environment:
            body["environment"] = environment or self.environment
        if dry_run:
            body["dryRun"] = True
        return Decision(self.request("POST", _PREFIX + "/authorize", body))

    def decision(self, decision_id):
        """Reads a decision again, e.g. to see whether its approval was given."""
        return Decision(self.request("GET", _PREFIX + "/decisions/" + str(decision_id)))

    def wait_for_approval(self, decision, timeout=300.0, poll_interval=5.0):
        """Polls until the approval of a REVIEW decision is answered or `timeout` seconds pass.

        Returns the authorized Decision. Raises ApprovalRejected, ApprovalExpired, or ApprovalPending (timeout).
        """
        deadline = time.monotonic() + timeout
        current = decision
        while True:
            if current.authorized:
                return current
            status = current.approval_status
            if status == "REJECTED":
                raise ApprovalRejected(current, "Approval rejected: %s" % (
                    (current.approval or {}).get("rationale") or current.reason))
            if status == "EXPIRED":
                raise ApprovalExpired(current, "The approval request expired")
            if status != "PENDING":
                raise ActionDenied(current)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ApprovalPending(current, "Waiting for approval: %s" % current.reason)
            time.sleep(min(poll_interval, remaining))
            current = self.decision(current.id)

    def record_execution(self, decision, status="EXECUTED", detail=None, reference=None):
        """Reports what happened to an authorized action: "EXECUTED" or "FAILED". Once per decision."""
        decision_id = decision.id if isinstance(decision, Decision) else decision
        body = {"status": status}
        if detail:
            body["detail"] = str(detail)[:1024]
        if reference:
            body["reference"] = str(reference)[:256]
        return Decision(self.request("POST", _PREFIX + "/decisions/%s/execution" % decision_id, body))

    def verify_authorization(self, token, action, resource=None, context=None, system=None):
        """For the service that carries an action out: is `token` a valid, unused authorization for exactly
        this request on `system` (the system this service protects; default: the client's system)?
        Returns a dict with `valid` (bool) and `reason`.

        A valid token is redeemed by this call: it works once. Check it right before acting, then act.
        Send values with the same types the agent sent (a number stays a number)."""
        return self.request("POST", _PREFIX + "/authorizations/verify", {
            "token": token, "system": self._system(system), "action": action,
            "resource": resource or {}, "context": context or {}})

    @contextlib.contextmanager
    def guard(self, action, resource=None, context=None, system=None, model=None, wait_for_approval=0.0,
              poll_interval=5.0, resume=None):
        """Runs the `with` block only when the action is authorized, then reports the outcome.

        wait_for_approval: seconds to wait when a person must approve. 0 (default) raises ApprovalPending
            at once, so the caller can tell the user and come back later.
        resume: the id of an earlier decision that was waiting for approval. The stored decision is used
            instead of asking again; the request must be the same one.

        Raises ActionDenied, ApprovalPending, ApprovalRejected, ApprovalExpired, GovernanceUnavailable.
        If the block raises, FAILED is reported and the exception continues.
        """
        if resume:
            decision = self.decision(resume)
            if (decision.raw.get("action") != action or decision.raw.get("resource") != (resource or {})
                    or decision.raw.get("context") != (context or {})):
                raise GovernanceError(
                    "Decision %s was made for a different request; ask for a new authorization." % resume)
        else:
            decision = self.authorize(action, resource, context, system=system, model=model)
        if not decision.authorized:
            if decision.status != "REVIEW":
                raise ActionDenied(decision)
            decision = self.wait_for_approval(decision, timeout=wait_for_approval, poll_interval=poll_interval)
        try:
            yield decision
        except BaseException as error:
            self._report(decision, "FAILED", "%s: %s" % (type(error).__name__, error))
            raise
        self._report(decision, "EXECUTED", None)

    def governed(self, action, resource=None, context=None, wait_for_approval=0.0):
        """Decorator: the function runs only when `action` is authorized, exactly like `guard`.

        resource / context: functions that receive the same arguments as the decorated function and return
        the dict to send. Use them to pass the customer id, the amount, and the trusted session facts.
        The decision is passed to the function as the keyword argument `governance_decision` when the
        function accepts it (for example to hand `decision.token` to the service that does the work).

            @governance.governed("payment.refund",
                                 resource=lambda session, customer_id, amount: {"customer_id": customer_id},
                                 context=lambda session, customer_id, amount: {
                                     "amount": amount, "authorized_customer_id": session.customer_id})
            def process_refund(session, customer_id, amount, governance_decision=None):
                return payments.refund(customer_id, amount, token=governance_decision.token)

        Works for any plain function: a LangGraph node, a LangChain tool, an MCP tool handler.
        """
        def decorate(function):
            import inspect

            accepts_decision = "governance_decision" in inspect.signature(function).parameters

            @functools.wraps(function)
            def wrapper(*args, **kwargs):
                call_resource = resource(*args, **kwargs) if callable(resource) else (resource or {})
                call_context = context(*args, **kwargs) if callable(context) else (context or {})
                with self.guard(action, resource=call_resource, context=call_context,
                                wait_for_approval=wait_for_approval) as decision:
                    if accepts_decision:
                        kwargs["governance_decision"] = decision
                    return function(*args, **kwargs)

            return wrapper

        return decorate

    def _report(self, decision, status, detail):
        # The action has already run (or failed). A problem reporting it must not look like a failed action
        # to the caller, who might retry and do it twice. It is logged; the closure report shows the gap.
        try:
            self.record_execution(decision, status, detail)
        except GovernanceError as error:
            log.error("Could not report %s for decision %s: %s", status, decision.id, error)

    # ---- reconcile ------------------------------------------------------------------------------------

    def report_state(self, model=None, prompt_hash=None, tools=None, policy_version=None, source=None,
                     system=None, environment=None):
        """Tells Devernance what this deployment is really running. Returns the drift report (dict).

        Call it at startup and on a timer. Report only what you know; unreported properties are not compared.
        """
        body = {"system": self._system(system)}
        if model or self.model:
            body["model"] = model or self.model
        if prompt_hash:
            body["promptHash"] = prompt_hash
        if tools is not None:
            body["tools"] = list(tools)
        if policy_version is not None:
            body["policyVersion"] = policy_version
        if source:
            body["source"] = source
        if environment or self.environment:
            body["environment"] = environment or self.environment
        return self.request("POST", _PREFIX + "/observations", body)

    def drift(self, system=None):
        return self.request("GET", _PREFIX + "/drift", query={"system": system})

    # ---- define and check -----------------------------------------------------------------------------

    def apply(self, manifests):
        return self.request("POST", _PREFIX + "/apply", {"resources": manifests})

    def validate(self, manifests=None, system=None):
        """Validates `manifests` without applying them, or (manifests None) what is applied."""
        if manifests:
            return self.request("POST", _PREFIX + "/validate", {"resources": manifests})
        return self.request("GET", _PREFIX + "/validate", query={"system": system})

    def simulate(self, cases, manifests=None):
        body = {"cases": cases}
        if manifests:
            body["resources"] = manifests
        return self.request("POST", _PREFIX + "/simulate", body)

    def release_check(self, system=None):
        return self.request("GET", _PREFIX + "/release-check", query={"system": self._system(system)})

    def resources(self, system=None, active_only=True):
        return self.request("GET", _PREFIX + "/resources",
                            query={"system": self._system(system), "activeOnly": active_only})

    def status(self, system=None):
        return self.request("GET", _PREFIX + "/status", query={"system": system})

    def graph(self, system=None):
        return self.request("GET", _PREFIX + "/graph", query={"system": system})

    # ---- verify ---------------------------------------------------------------------------------------

    def decisions(self, system=None, action=None, status=None, since=None, until=None, limit=None):
        return self.request("GET", _PREFIX + "/decisions", query={
            "system": system, "action": action, "status": status, "from": since, "to": until, "limit": limit})

    def approvals(self, status=None, limit=None):
        return self.request("GET", _PREFIX + "/approvals", query={"status": status, "limit": limit})

    def evidence_bundle(self, system=None, since=None, until=None):
        return self.request("GET", _PREFIX + "/evidence-bundle",
                            query={"system": self._system(system), "from": since, "to": until})

    def closure(self, system=None, since=None, until=None):
        return self.request("GET", _PREFIX + "/closure",
                            query={"system": self._system(system), "from": since, "to": until})

    def jwks(self):
        """The server's public signing keys. Public: no API key is sent."""
        return self.request("GET", "/.well-known/jwks.json", authenticated=False)

    def _system(self, system):
        name = system or self.system
        if not name:
            raise GovernanceError("No system given. Pass system= here or to GovernanceClient(system=...).")
        return name


def _query_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def _error_message(error):
    try:
        text = error.read().decode("utf-8")
        data = json.loads(text)
        if isinstance(data, dict):
            return data.get("message") or data.get("error") or text
        return text
    except Exception:  # noqa: BLE001 - any unreadable body falls back to the status line
        return error.reason or "request failed"
