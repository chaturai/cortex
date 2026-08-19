import { editable } from "./statement-editor.js";

// Editing an approved resource does not change it. It stages a branch carrying the additions and
// the retractions the edit implies, and sends the author to that branch to review and approve —
// the same review every other write to the knowledge graph goes through.

const container = document.querySelector(".describe-container");
const saveButton = document.getElementById("save-changes");
const resetButton = document.getElementById("reset-changes");
const addButton = document.getElementById("add-statement");
const heading = container.querySelector(".subject-heading");
const list = container.querySelector(".statement-list");

const editor = editable(container, (dirty) => {
  const pending = dirty || container.querySelector(".statement-added") !== null;
  saveButton.hidden = !pending;
  resetButton.hidden = !pending;
});

addButton.addEventListener("click", () => {
  editor.add(list, heading.dataset.uri);
  editor.refresh();
  saveButton.hidden = false;
  resetButton.hidden = false;
});

resetButton.addEventListener("click", () => editor.reset());

saveButton.addEventListener("click", async () => {
  const changes = editor.changes();
  if (changes === null) return;
  const renames = editor.renames();
  if (renames === null) return;
  if (changes.length === 0 && renames.length === 0) return;
  const response = await fetch(container.dataset.reviseUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      subject: heading.dataset.uri,
      newSubject: renames.length > 0 ? renames[0].newSubject : null,
      changes: changes,
    }),
  });
  let result = null;
  try {
    result = await response.json();
  } catch {
    alert(`Proposing the edit failed: ${response.status}`);
    return;
  }
  if (!result.valid) {
    alert(`The edit was rejected:\n\n${result.errors}`);
    return;
  }
  if (!result.branch) {
    alert("Nothing to review: the edit changes nothing.");
    return;
  }
  window.location.assign(`${container.dataset.branchesUrl}/${result.branch}`);
});
