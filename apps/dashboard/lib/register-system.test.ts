import assert from "node:assert/strict";
import test from "node:test";

import { splitList } from "./register-system";

test("splitList takes commas and new lines and drops blanks", () => {
  assert.deepEqual(splitList("Health declaration, Medical reports\nClaims history,, "), [
    "Health declaration",
    "Medical reports",
    "Claims history",
  ]);
  assert.deepEqual(splitList(""), []);
  assert.deepEqual(splitList(undefined), []);
});
