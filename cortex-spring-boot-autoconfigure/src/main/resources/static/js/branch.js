import { editable } from "./statement-editor.js";

const saveButton = document.getElementById("save-changes");
const resetButton = document.getElementById("reset-changes");

const editor = editable(document.querySelector(".describe-container"), (dirty) => {
  saveButton.hidden = !dirty;
  resetButton.hidden = !dirty;
});

resetButton.addEventListener("click", () => editor.reset());

saveButton.addEventListener("click", async () => {
  const changes = editor.changes();
  if (changes === null) return;
  const renames = editor.renames();
  if (renames === null) return;
  // Statement changes address subjects by their current IRI, so apply them before any renames
  if (changes.length > 0 && !(await post("update", changes, "Saving changes"))) return;
  if (renames.length > 0 && !(await post("rename", renames, "Renaming subjects"))) return;
  window.location.reload();
});

async function post(action, body, description) {
  const response = await fetch(`${window.location.pathname}/${action}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) alert(`${description} failed: ${response.status}`);
  return response.ok;
}
