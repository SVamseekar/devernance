"""Stand-ins for the company's real systems: the payment service and the customer database.

They are deliberately tiny. The point they make is the second enforcement point: the payment service does not
trust its caller. It asks Devernance whether the authorization token it was handed is real, unused, and for
exactly this refund. So a code path that skips the SDK wrapper still cannot pay anything out.
"""


class PaymentService:
    def __init__(self, governance):
        self._governance = governance
        self.payments = []

    def refund(self, customer_id, amount, currency, authorized_customer_id, authorization):
        check = self._governance.verify_authorization(
            authorization,
            "payment.refund",
            resource={"type": "customer_payment", "customer_id": customer_id},
            context={"amount": amount, "currency": currency, "authorized_customer_id": authorized_customer_id},
        )
        if not check["valid"]:
            raise PermissionError("Payment service refused the refund: %s" % check["reason"])
        payment_id = "payment-%04d" % (1001 + len(self.payments))
        self.payments.append({"id": payment_id, "customer_id": customer_id, "amount": amount})
        return payment_id


class CustomerDatabase:
    _ROWS = {
        "CUST-102": {"customer_id": "CUST-102", "name": "A. Rao", "policy": "HOME-7781"},
        "CUST-999": {"customer_id": "CUST-999", "name": "S. Mehta", "policy": "AUTO-1204"},
    }

    def get(self, customer_id):
        return dict(self._ROWS[customer_id])
