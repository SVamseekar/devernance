import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { HOVER_CLICK_GRACE_MS, menuAfterClick } from "./menu-toggle";

describe("menuAfterClick", () => {
  it("opens a closed menu", () => {
    assert.equal(menuAfterClick(null, "Solutions", null, 1_000), "Solutions");
  });

  it("switches from another open menu", () => {
    assert.equal(menuAfterClick("Product", "Solutions", 900, 1_000), "Solutions");
  });

  it("keeps a menu open when the click follows the hover that opened it", () => {
    assert.equal(menuAfterClick("Solutions", "Solutions", 1_000, 1_000 + 120), "Solutions");
  });

  it("closes a menu on a later click", () => {
    assert.equal(menuAfterClick("Solutions", "Solutions", 1_000, 1_000 + HOVER_CLICK_GRACE_MS + 1), null);
  });

  it("closes a menu that was opened by click, not hover", () => {
    assert.equal(menuAfterClick("Solutions", "Solutions", null, 5_000), null);
  });
});
