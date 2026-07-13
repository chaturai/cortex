const saveButton = document.getElementById("save-changes");
const resetButton = document.getElementById("reset-changes");
const statements = document.querySelectorAll(".statement-item");
const headings = document.querySelectorAll(".subject-heading");

function refreshActions() {
  const dirty =
    document.querySelector(".statement-item.deleted, .statement-item.edited, .subject-heading.edited") !== null;
  saveButton.hidden = !dirty;
  resetButton.hidden = !dirty;
}

statements.forEach((item) => {
  const object = item.querySelector(".statement-object");

  object.addEventListener("input", () => {
    item.classList.toggle("edited", object.textContent !== item.dataset.object);
    refreshActions();
  });

  item.querySelector(".statement-delete").addEventListener("click", () => {
    item.classList.toggle("deleted");
    refreshActions();
  });
});

headings.forEach((heading) => {
  // Blank nodes have no IRI ending in the displayed name, so they cannot be renamed
  if (!heading.dataset.uri.endsWith(heading.dataset.name)) {
    heading.removeAttribute("contenteditable");
    return;
  }
  heading.addEventListener("input", () => {
    heading.classList.toggle("edited", heading.textContent !== heading.dataset.name);
    refreshActions();
  });
});

resetButton.addEventListener("click", () => {
  statements.forEach((item) => {
    item.querySelector(".statement-object").textContent = item.dataset.object;
    item.classList.remove("deleted", "edited");
  });
  headings.forEach((heading) => {
    heading.textContent = heading.dataset.name;
    heading.classList.remove("edited");
  });
  refreshActions();
});

saveButton.addEventListener("click", async () => {
  const changes = [];
  statements.forEach((item) => {
    const deleted = item.classList.contains("deleted");
    const edited = item.classList.contains("edited");
    if (!deleted && !edited) return;
    changes.push({
      subject: item.dataset.subject,
      predicate: item.dataset.predicate,
      object: item.dataset.object,
      literal: item.dataset.literal === "true",
      datatype: item.dataset.datatype || null,
      newObject: deleted ? null : item.querySelector(".statement-object").textContent,
    });
  });
  const renames = [];
  for (const heading of document.querySelectorAll(".subject-heading.edited")) {
    const name = heading.textContent.trim();
    if (!name || /[\s<>"{}|\\^`]/.test(name)) {
      alert(`"${name}" is not a valid name: it must not be empty or contain whitespace`);
      return;
    }
    const uri = heading.dataset.uri;
    renames.push({
      subject: uri,
      newSubject: uri.slice(0, uri.length - heading.dataset.name.length) + name,
    });
  }
  // Statement changes address subjects by their current IRI, so apply them before any renames
  if (changes.length > 0) {
    const response = await fetch(`${window.location.pathname}/update`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(changes),
    });
    if (!response.ok) {
      alert(`Saving changes failed: ${response.status}`);
      return;
    }
  }
  if (renames.length > 0) {
    const response = await fetch(`${window.location.pathname}/rename`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(renames),
    });
    if (!response.ok) {
      alert(`Renaming subjects failed: ${response.status}`);
      return;
    }
  }
  window.location.reload();
});
