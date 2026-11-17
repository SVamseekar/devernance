"""Runs the three production situations against a real Devernance server.

    A. a 20,000 refund             -> ALLOW, paid
    B. a 75,000 refund             -> REVIEW, paid only after a compliance officer approves
    C. another customer's record   -> DENY, the database is never touched
    +  an export tool with no rule -> DENY

Needs DEVERNANCE_SERVER and DEVERNANCE_API_KEY (a RUNTIME key named claims-agent), and the manifests in
./governance applied. See README.md. With --wait N it waits up to N seconds for the approval of B;
approve it in the dashboard (Governance > Approvals) or let scripts/governance-e2e.sh do it.
"""

import argparse
import json
import sys

from devernance import ActionDenied, ApprovalPending, GovernanceError

from agent.tools import ClaimsTools

SESSION = {"claim_id": "CLM-5512", "authorized_customer_id": "CUST-102"}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--wait", type=float, default=0.0, help="seconds to wait for the approval of B")
    parser.add_argument("--result-file", help="write the outcome of each situation here as JSON")
    args = parser.parse_args()
    tools = ClaimsTools()
    outcome = {}

    print("A. Refund 20,000 to CUST-102")
    payment = tools.process_refund(SESSION, "CUST-102", 20000)
    print("   ALLOWED and paid: %s" % payment)
    outcome["A"] = {"paid": payment}

    print("B. Refund 75,000 to CUST-102")
    try:
        payment = tools.process_refund(SESSION, "CUST-102", 75000)
        print("   paid without approval: %s  <-- this must never happen" % payment)
        outcome["B"] = {"paid": payment, "approvalWasRequired": False}
    except ApprovalPending as pending:
        decision = pending.decision
        print("   HUMAN APPROVAL REQUIRED: %s (decision %s)" % (decision.reason, decision.id))
        print("   Payment is suspended. Payments so far: %d" % len(tools.payment_service.payments))
        outcome["B"] = {"decisionId": decision.id, "approvalId": decision.approval["id"],
                        "approvalWasRequired": True, "paid": None}
        if args.wait > 0:
            print("   Waiting up to %.0f s for a compliance officer..." % args.wait)
            sys.stdout.flush()
            if args.result_file:
                with open(args.result_file, "w", encoding="utf-8") as handle:
                    json.dump(outcome, handle)
            try:
                payment = tools.process_refund(SESSION, "CUST-102", 75000, wait_for_approval=args.wait,
                                               resume=decision.id)
                print("   APPROVED and paid: %s" % payment)
                outcome["B"]["paid"] = payment
            except GovernanceError as error:
                print("   not paid: %s" % error)
                outcome["B"]["error"] = str(error)

    print("C. Read customer CUST-999 while working on CUST-102's claim")
    try:
        tools.get_customer(SESSION, "CUST-999")
        print("   read  <-- this must never happen")
        outcome["C"] = {"denied": False}
    except ActionDenied as denied:
        print("   DENIED: %s" % denied.decision.reason)
        outcome["C"] = {"denied": True, "decisionId": denied.decision.id}

    print("+  Call a tool that has no policy rule (export_all_customers)")
    try:
        tools.export_all_customers(SESSION)
        print("   exported  <-- this must never happen")
        outcome["export"] = {"denied": False}
    except ActionDenied as denied:
        print("   DENIED: %s" % denied.decision.reason)
        outcome["export"] = {"denied": True}

    print("+  A code path that skips the SDK and calls the payment service with a made-up token")
    try:
        tools.payment_service.refund("CUST-102", 999999, "INR", "CUST-102", authorization="not-a-real-token")
        print("   paid  <-- this must never happen")
        outcome["bypass"] = {"refused": False}
    except PermissionError as refused:
        print("   REFUSED by the payment service: %s" % refused)
        outcome["bypass"] = {"refused": True}

    outcome["payments"] = tools.payment_service.payments
    if args.result_file:
        with open(args.result_file, "w", encoding="utf-8") as handle:
            json.dump(outcome, handle)
    print("Payments made: %s" % json.dumps(tools.payment_service.payments))


if __name__ == "__main__":
    main()
