// Inline statement editing, shared by the branch review page and the describe page.
//
// Both pages render the same markup — a list of `.statement-item`s carrying the raw node values as
// data attributes, with a contenteditable `.statement-object` and a delete button — and both send
// the same `BranchChange` JSON. What differs is where that JSON goes: the branch page applies it to
// the branch in place, the describe page stages it for review. So this module owns the editing and
// the serialization, and each page owns its own save.

// A name is used to build an IRI by substitution, so anything the IRI syntax cannot carry has to be
// rejected here rather than producing a resource nobody can address.
const INVALID_IN_NAME = /[\s<>"{}|\\^`]/;

/**
 * The text a row was rendered with. It is the object's display form, which is not always the object
 * itself: the describe page abbreviates a resource against the ontology's prefixes, and sends the
 * abbreviation as the new value while still naming the full IRI as the one it replaces.
 */
function rendered(item) {
  return item.dataset.display ?? item.dataset.object ?? "";
}

/** Reads the node values a statement row carries into the change JSON the API expects. */
function readChange(item, newObject) {
  return {
    subject: item.dataset.subject,
    predicate: item.dataset.predicate,
    object: item.dataset.object ?? null,
    literal: item.dataset.literal === "true",
    datatype: item.dataset.datatype || null,
    language: item.dataset.language || null,
    newObject: newObject,
    retracted: item.dataset.retracted === "true",
  };
}

/**
 * Wires up inline editing over a container.
 *
 * `onDirty` is called whenever the edited state changes, so the page can show or hide its own save
 * and reset controls. The returned object exposes what a page needs at save time.
 */
export function editable(root, onDirty) {
  const state = { added: [] };

  function refresh() {
    onDirty(root.querySelector(".statement-item.deleted, .statement-item.edited, .subject-heading.edited") !== null);
  }

  function syncSubject(subject) {
    if (!subject) return;
    const items = subject.querySelectorAll(".statement-item");
    const allDeleted = [...items].every((item) => item.classList.contains("deleted"));
    subject.classList.toggle("deleted", items.length > 0 && allDeleted);
  }

  function wire(item) {
    const object = item.querySelector(".statement-object");
    if (object && object.isContentEditable) {
      object.addEventListener("input", () => {
        item.classList.toggle("edited", object.textContent !== rendered(item));
        refresh();
      });
    }
    const predicate = item.querySelector(".statement-predicate");
    if (predicate && predicate.isContentEditable) {
      predicate.addEventListener("input", () => {
        item.dataset.predicate = predicate.textContent.trim();
        refresh();
      });
    }
    const remove = item.querySelector(".statement-delete");
    if (remove) {
      remove.addEventListener("click", () => {
        // A row that was added by this edit has nothing behind it to delete: drop it outright.
        if (item.dataset.added === "true") {
          state.added = state.added.filter((added) => added !== item);
          item.remove();
          refresh();
          return;
        }
        item.classList.toggle("deleted");
        syncSubject(item.closest(".branch-subject"));
        refresh();
      });
    }
  }

  root.querySelectorAll(".statement-item").forEach(wire);

  root.querySelectorAll(".subject-delete").forEach((button) => {
    button.addEventListener("click", () => {
      const subject = button.closest(".branch-subject") ?? root;
      const deleted = !subject.classList.contains("deleted");
      subject.classList.toggle("deleted", deleted);
      subject.querySelectorAll(".statement-item").forEach((item) => {
        if (item.dataset.added !== "true") item.classList.toggle("deleted", deleted);
      });
      refresh();
    });
  });

  root.querySelectorAll(".subject-heading").forEach((heading) => {
    // Blank nodes have no IRI ending in the displayed name, so they cannot be renamed
    if (!heading.dataset.uri.endsWith(heading.dataset.name)) {
      heading.removeAttribute("contenteditable");
      return;
    }
    heading.addEventListener("input", () => {
      heading.classList.toggle("edited", heading.textContent !== heading.dataset.name);
      refresh();
    });
  });

  return {
    /** Appends an empty, editable row for a statement being added, and returns it. */
    add(list, subject) {
      const item = document.createElement("li");
      item.className = "statement-item statement-asserted statement-added";
      item.dataset.subject = subject;
      item.dataset.predicate = "";
      item.dataset.added = "true";
      item.innerHTML =
        '<code class="statement-predicate" contenteditable="plaintext-only" spellcheck="false" data-placeholder="property"></code>' +
        '<code class="statement-object" contenteditable="plaintext-only" spellcheck="false" data-placeholder="value"></code>' +
        '<button type="button" class="statement-delete" title="Discard statement">&times;</button>';
      list.appendChild(item);
      state.added.push(item);
      wire(item);
      item.querySelector(".statement-predicate").focus();
      return item;
    },

    /** The changes to send, or `null` if an added row is incomplete. */
    changes() {
      const changes = [];
      for (const item of root.querySelectorAll(".statement-item")) {
        if (item.dataset.added === "true") {
          const predicate = item.querySelector(".statement-predicate").textContent.trim();
          const value = item.querySelector(".statement-object").textContent.trim();
          if (!predicate || !value) {
            alert("A statement being added needs both a property and a value.");
            return null;
          }
          changes.push(readChange(item, value));
          continue;
        }
        const deleted = item.classList.contains("deleted");
        const edited = item.classList.contains("edited");
        if (!deleted && !edited) continue;
        changes.push(readChange(item, deleted ? null : item.querySelector(".statement-object").textContent));
      }
      return changes;
    },

    /** The renames to send, or `null` if a new name is unusable. */
    renames() {
      const renames = [];
      for (const heading of root.querySelectorAll(".subject-heading.edited")) {
        const name = heading.textContent.trim();
        if (!name || INVALID_IN_NAME.test(name)) {
          alert(`"${name}" is not a valid name: it must not be empty or contain whitespace`);
          return null;
        }
        const uri = heading.dataset.uri;
        renames.push({
          subject: uri,
          newSubject: uri.slice(0, uri.length - heading.dataset.name.length) + name,
        });
      }
      return renames;
    },

    /** Restores every row and heading to the state the page was rendered in. */
    reset() {
      state.added.forEach((item) => item.remove());
      state.added = [];
      root.querySelectorAll(".statement-item").forEach((item) => {
        const object = item.querySelector(".statement-object");
        if (object) object.textContent = rendered(item);
        item.classList.remove("deleted", "edited");
      });
      root.querySelectorAll(".subject-heading").forEach((heading) => {
        heading.textContent = heading.dataset.name;
        heading.classList.remove("edited");
      });
      root.querySelectorAll(".branch-subject").forEach((subject) => subject.classList.remove("deleted"));
      root.classList.remove("deleted");
      refresh();
    },

    refresh,
  };
}
