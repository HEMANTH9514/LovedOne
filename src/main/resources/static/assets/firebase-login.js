import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import { getAuth, signInWithEmailAndPassword, signOut } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";

const loginForm = document.querySelector(".auth-form");
const errorElement = document.getElementById("login-error");
const config = window.__OUR_STORY_FIREBASE_CONFIG || {};

if (!loginForm || !config.enabled) {
    // Keep legacy form POST available when Firebase is not configured yet.
} else {
    const firebaseApp = initializeApp({
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        storageBucket: config.storageBucket,
        messagingSenderId: config.messagingSenderId,
        appId: config.appId
    });
    const auth = getAuth(firebaseApp);

    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const usernameInput = loginForm.elements.namedItem("username");
        const passwordInput = loginForm.elements.namedItem("password");
        const email = usernameInput ? usernameInput.value.trim() : "";
        const password = passwordInput ? passwordInput.value : "";

        if (!email || !password) {
            showError("Enter your email and password to continue.");
            return;
        }

        try {
            const credential = await signInWithEmailAndPassword(auth, email, password);
            const idToken = await credential.user.getIdToken();
            const response = await fetch("/login/firebase", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ idToken: idToken })
            });

            if (response.ok) {
                window.location.href = "/dashboard";
                return;
            }

            let errorMessage = "Private login failed. Please verify your email and password.";
            try {
                const body = await response.json();
                if (body && typeof body.error === "string" && body.error.trim()) {
                    errorMessage = body.error;
                }
            } catch (ignored) {
            }

            showError(errorMessage);
            await signOut(auth);
        } catch (error) {
            showError("Private login failed. Please verify your email and password.");
        }
    });
}

function showError(message) {
    if (errorElement) {
        errorElement.textContent = message;
    }
}
