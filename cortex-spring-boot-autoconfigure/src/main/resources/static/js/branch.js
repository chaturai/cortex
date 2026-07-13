const saveButton = document.getElementById("save-changes");
const resetButton = document.getElementById("reset-changes");
const statements = document.querySelectorAll(".statement-item");

function refreshActions() {
  const dirty = document.querySelector(".statement-item.deleted, .statement-item.edited") !== null;
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

resetButton.addEventListener("click", () => {
  statements.forEach((item) => {
    item.querySelector(".statement-object").textContent = item.dataset.object;
    item.classList.remove("deleted", "edited");
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
  const response = await fetch(`${window.location.pathname}/update`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(changes),
  });
  if (response.ok) {
    window.location.reload();
  } else {
    alert(`Saving changes failed: ${response.status}`);
  }
});
