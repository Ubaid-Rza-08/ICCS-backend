// CONFIGURATION
const API_BASE_URL = "http://localhost:8080";

// 1. ON LOAD: Check if we are returning from Google Login
window.onload = function() {
    const params = new URLSearchParams(window.location.search);

    // If URL has tokens, save them and clean URL
    if (params.has('accessToken')) {
        localStorage.setItem('accessToken', params.get('accessToken'));
        localStorage.setItem('refreshToken', params.get('refreshToken'));
        localStorage.setItem('uid', params.get('uid')); // Needed for refresh lookup

        // Remove tokens from address bar for security
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // Check if logged in and render appropriate view
    checkLoginState();
};

// 2. UI STATE MANAGEMENT
function checkLoginState() {
    const accessToken = localStorage.getItem('accessToken');

    if (accessToken) {
        document.getElementById('login-section').classList.add('hidden');
        document.getElementById('dashboard-section').classList.remove('hidden');

        // Display Tokens
        document.getElementById('access-token-display').innerText = accessToken;
        document.getElementById('refresh-token-display').innerText = localStorage.getItem('refreshToken');

        // Load User Profile
        loadUserProfile();
    } else {
        document.getElementById('login-section').classList.remove('hidden');
        document.getElementById('dashboard-section').classList.add('hidden');
    }
}

// 3. SMART FETCH WRAPPER (Handles Token Refresh Automatically)
async function apiRequest(endpoint, method = "GET", body = null) {
    let accessToken = localStorage.getItem("accessToken");
    let headers = { "Content-Type": "application/json" };

    if (accessToken) headers["Authorization"] = "Bearer " + accessToken;

    try {
        let response = await fetch(API_BASE_URL + endpoint, {
            method: method,
            headers: headers,
            body: body ? JSON.stringify(body) : null
        });

        // IF 403 Forbidden -> Token likely expired
        if (response.status === 403) {
            console.warn("Access Token expired. Attempting refresh...");
            setStatus("Token expired. Refreshing...", "blue");

            const refreshSuccess = await performRefreshToken();

            if (refreshSuccess) {
                // Retry Original Request with NEW Token
                accessToken = localStorage.getItem("accessToken");
                headers["Authorization"] = "Bearer " + accessToken;

                response = await fetch(API_BASE_URL + endpoint, {
                    method: method,
                    headers: headers,
                    body: body ? JSON.stringify(body) : null
                });
            } else {
                logout(); // Refresh failed, force login
                return null;
            }
        }

        return response.ok ? response.json() : null;

    } catch (error) {
        console.error("API Request failed", error);
        setStatus("Network Error", "red");
        return null;
    }
}

// 4. REFRESH TOKEN LOGIC
async function performRefreshToken() {
    const refreshToken = localStorage.getItem("refreshToken");
    const uid = localStorage.getItem("uid");

    if (!refreshToken || !uid) return false;

    try {
        const response = await fetch(API_BASE_URL + "/api/auth/refresh", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken, uid })
        });

        if (response.ok) {
            const data = await response.json();
            localStorage.setItem("accessToken", data.accessToken);
            document.getElementById('access-token-display').innerText = data.accessToken;
            setStatus("Token Refreshed Successfully!", "green");
            return true;
        }
    } catch (e) { console.error(e); }

    return false;
}

// 5. UI ACTIONS
async function loadUserProfile() {
    // Calls the backend. If token is expired, apiRequest handles it.
    const user = await apiRequest("/api/user");

    if (user) {
        document.getElementById('user-name').innerText = user.name;
        document.getElementById('user-email').innerText = user.email;
        document.getElementById('user-avatar').src = user.picture;
    }
}

async function fetchProtectedData() {
    setStatus("Fetching protected data...", "black");
    const data = await apiRequest("/api/user"); // Re-using user endpoint as a test
    if (data) {
        setStatus("API Call Successful! Data received.", "green");
    } else {
        setStatus("API Call Failed.", "red");
    }
}

async function manualRefresh() {
    setStatus("Manually refreshing...", "blue");
    await performRefreshToken();
}

function logout() {
    localStorage.clear();
    window.location.href = API_BASE_URL + "/logout"; // Clears Spring Session too
}

function setStatus(msg, color) {
    const el = document.getElementById('status-message');
    el.innerText = msg;
    el.style.color = color;
}