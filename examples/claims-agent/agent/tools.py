"""The claims agent's tools, each behind a Devernance check.

This file is the enforcement point. The LLM decides *that* it wants to call a tool; whether the call runs is
decided here, by policy, outside the model. No LLM or agent framework is needed to see it work: a LangGraph
node, an MCP tool or a plain function all call these the same way.

`session` is the application's own, trusted state (which claim the agent is working on, for which customer).
It is never taken from model output.
"""

from devernance import GovernanceClient

from .services import CustomerDatabase, PaymentService

MODEL = "gpt-4.1@2025-04-14"
TOOLS = ["claim_search", "customer_database", "process_refund"]
PROMPT_HASH = "sha256:claims-prompt-v1"


class ClaimsTools:
    def __init__(self, governance=None, model=MODEL):
        self.governance = governance or GovernanceClient(system="claims-agent", model=model)
        self.payment_service = PaymentService(self.governance)
        self.customers = CustomerDatabase()

    def process_refund(self, session, customer_id, amount, wait_for_approval=0.0, resume=None):
        """Issues a refund. Above the policy threshold it waits for (or asks for) a human approval."""
        resource = {"type": "customer_payment", "customer_id": customer_id}
        context = {
            "amount": amount,
            "currency": "INR",
            "authorized_customer_id": session["authorized_customer_id"],
        }
        with self.governance.guard("payment.refund", resource=resource, context=context,
                                   wait_for_approval=wait_for_approval, resume=resume) as decision:
            return self.payment_service.refund(
                customer_id, amount, "INR", session["authorized_customer_id"], authorization=decision.token)

    def get_customer(self, session, customer_id):
        """Reads a customer record. Only the customer of the authorised claim is readable."""
        with self.governance.guard(
                "customer.read",
                resource={"customer_id": customer_id},
                context={"authorized_customer_id": session["authorized_customer_id"]}):
            return self.customers.get(customer_id)

    def export_all_customers(self, session):
        """A tool someone added without a policy. It is denied at runtime and fails `devernance validate`."""
        with self.governance.guard("customer.export"):
            return [self.customers.get(customer_id) for customer_id in ("CUST-102", "CUST-999")]

    def report_state(self, model=None, tools=None):
        """What this deployment is really running, for drift detection. Call at startup and on a timer."""
        return self.governance.report_state(
            model=model or self.governance.model, prompt_hash=PROMPT_HASH, tools=tools or TOOLS,
            source="claims-agent")
