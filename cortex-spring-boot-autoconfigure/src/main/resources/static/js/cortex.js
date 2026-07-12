function escapeHtml(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

document.querySelectorAll("code.linkify").forEach((code) => {
    code.innerHTML = escapeHtml(code.textContent)
      .split("\n")
      .map((line) => {
          return line.replace(/cortex:(\w[\w-]*)/g, (match, prefix) => {
              return `<a href="/assertions/${prefix}" class="code-link"><b>${prefix}</b></a>`;
          });
      })
      .join("<br>");
});
