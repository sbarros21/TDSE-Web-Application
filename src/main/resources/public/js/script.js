const resultDiv = document.getElementById("result");
const errorDiv = document.getElementById("error");
const loadingDiv = document.getElementById("loading");

function showLoading() {
    loadingDiv.style.display = "block";
    resultDiv.textContent = "";
    errorDiv.textContent = "";
}

function hideLoading() {
    loadingDiv.style.display = "none";
}

function showResult(text) {
    resultDiv.textContent = text;
    errorDiv.textContent = "";
}

function showError(text) {
    errorDiv.textContent = text;
    resultDiv.textContent = "";
}

async function callService(url) {
    showLoading();
    try {
        const response = await fetch(url);
        if (!response.ok) {
            // Server responded, but with an error status (e.g. 400, 404, 405).
            let message = `Request failed (status ${response.status})`;
            try {
                const errorBody = await response.json();
                if (errorBody.error) {
                    message = errorBody.error;
                }
            } catch (parseError) {
                // Response wasn't JSON; keep the generic message.
            }
            showError(message);
            return;
        }
        const data = await response.json();
        return data;
    } catch (networkError) {
        // fetch() itself failed: no connection, DNS error, server down, etc.
        showError("Network error: could not reach the server.");
    } finally {
        hideLoading();
    }
}

document.getElementById("greetingForm").addEventListener("submit", async (event) => {
    event.preventDefault(); // Stay on the same page.
    const name = document.getElementById("nameInput").value.trim();
    if (!name) {
        showError("Please enter a name.");
        return;
    }
    const data = await callService(`/greeting?name=${encodeURIComponent(name)}`);
    if (data) {
        showResult(data.message);
    }
});

document.getElementById("squareForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const value = document.getElementById("numberInput").value.trim();
    if (value === "") {
        showError("Please enter a number.");
        return;
    }
    const data = await callService(`/square?value=${encodeURIComponent(value)}`);
    if (data) {
        showResult(`${data.input} squared is ${data.square}`);
    }
});

document.getElementById("timeButton").addEventListener("click", async () => {
    const data = await callService("/server-time");
    if (data) {
        showResult(`Server time: ${data.serverTime}`);
    }
});