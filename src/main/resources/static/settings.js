const form = document.getElementById("settingsForm");
const loadingState = document.getElementById("loadingState");
const message = document.getElementById("formMessage");
const saveButton = document.getElementById("saveButton");

function render(settings) {
  document.getElementById("dhanCurrent").textContent = settings.dhanAccessTokenMasked;
  document.getElementById("openAiCurrent").textContent = settings.openAiApiKeyMasked;
  document.getElementById("dhanCurrent").classList.toggle("missing", !settings.dhanAccessTokenConfigured);
  document.getElementById("openAiCurrent").classList.toggle("missing", !settings.openAiApiKeyConfigured);
}

function setMessage(text, state = "") {
  message.textContent = text;
  message.className = state;
}

async function loadSettings() {
  try {
    const response = await fetch("/api/settings", { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`Settings request failed (${response.status})`);
    render(await response.json());
    loadingState.hidden = true;
    form.hidden = false;
  } catch (error) {
    loadingState.textContent = "Settings could not be loaded. Refresh the page and sign in again.";
    loadingState.classList.add("error");
  }
}

document.querySelectorAll(".reveal-button").forEach((button) => {
  button.addEventListener("click", () => {
    const input = document.getElementById(button.dataset.target);
    const revealing = input.type === "password";
    input.type = revealing ? "text" : "password";
    button.textContent = revealing ? "Hide" : "Show";
  });
});

form.addEventListener("input", () => setMessage("Unsaved changes"));

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const clearDhan = document.getElementById("clearDhan").checked;
  const clearOpenAi = document.getElementById("clearOpenAi").checked;
  if ((clearDhan || clearOpenAi) && !window.confirm("Remove the selected stored credential?")) return;

  saveButton.disabled = true;
  setMessage("Saving settings");
  try {
    const response = await fetch("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        dhanAccessToken: document.getElementById("dhanAccessToken").value,
        openAiApiKey: document.getElementById("openAiApiKey").value,
        clearDhanAccessToken: clearDhan,
        clearOpenAiApiKey: clearOpenAi
      })
    });
    if (!response.ok) throw new Error(`Settings update failed (${response.status})`);
    render(await response.json());
    form.reset();
    document.querySelectorAll(".secret-input input").forEach((input) => { input.type = "password"; });
    document.querySelectorAll(".reveal-button").forEach((button) => { button.textContent = "Show"; });
    setMessage("Settings saved", "success");
  } catch (error) {
    setMessage("Settings were not saved. Try again.", "error");
  } finally {
    saveButton.disabled = false;
  }
});

loadSettings();
