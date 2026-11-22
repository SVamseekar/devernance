import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { QUESTIONNAIRE, deriveRegistration, initialAnswers, sectorAnswerDefaults } from "./system-registration";

const none = { q_biometrics: false, q_essential: false, q_hr: false, q_interaction: false };

describe("deriveRegistration", () => {
  it("suggests high risk for essential-service use and lists human oversight", () => {
    const r = deriveRegistration({ ...none, q_essential: true }, "other");
    assert.equal(r.riskClass, "high");
    assert.match(r.riskBasis, /Annex III/);
    assert.ok(r.obligations.some((o) => /Human Oversight/i.test(o)));
  });

  it("suggests limited risk with Article 50 disclosure for chat-style systems", () => {
    const r = deriveRegistration({ ...none, q_interaction: true }, "other");
    assert.equal(r.riskClass, "limited");
    assert.match(r.riskBasis, /Article 50/);
    assert.ok(r.obligations.some((o) => /Article 50/.test(o)));
  });

  it("suggests minimal risk when nothing applies", () => {
    assert.equal(deriveRegistration(none, "other").riskClass, "minimal");
  });

  it("adds the sector pack obligations for insurance", () => {
    const r = deriveRegistration({ ...none, q_essential: true }, "insurance");
    assert.ok(r.obligations.some((o) => /Insurance pack/.test(o)));
  });
});

describe("sectorAnswerDefaults", () => {
  it("pre-answers the essential-services question for insurance and finance", () => {
    assert.deepEqual(sectorAnswerDefaults("insurance"), { q_essential: true, q_hr: false });
    assert.deepEqual(sectorAnswerDefaults("finance"), { q_essential: true, q_hr: false });
  });
  it("pre-answers the HR question for hr and leaves other sectors alone", () => {
    assert.deepEqual(sectorAnswerDefaults("hr"), { q_hr: true, q_essential: false });
    assert.equal(sectorAnswerDefaults("healthcare"), null);
  });
});

describe("QUESTIONNAIRE", () => {
  it("has the four classification questions", () => {
    assert.deepEqual(QUESTIONNAIRE.map((q) => q.id), ["q_biometrics", "q_essential", "q_hr", "q_interaction"]);
  });
});

describe("initialAnswers", () => {
  it("applies the default sector's hints before anyone changes the sector", () => {
    const answers = initialAnswers("insurance");
    assert.equal(answers.q_essential, true);
    assert.equal(deriveRegistration(answers, "insurance").riskClass, "high");
  });

  it("leaves every answer off for a sector without hints", () => {
    assert.deepEqual(initialAnswers("other"), { q_biometrics: false, q_essential: false, q_hr: false, q_interaction: false });
  });
});
