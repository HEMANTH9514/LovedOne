document.querySelectorAll(".reveal").forEach((element, index) => {
    element.style.animationDelay = `${index * 90}ms`;
});

const secretMessage = document.getElementById("decoded-message");
if (secretMessage) {
    const secret = secretMessage.dataset.secret || "";
    try {
        if (/^[A-Za-z0-9+/=]+$/.test(secret) && secret.length % 4 === 0) {
            secretMessage.textContent = atob(secret);
        } else {
            secretMessage.textContent = secret;
        }
    } catch (error) {
        secretMessage.textContent = secret || "Your favorite memories deserve a beautiful home.";
    }
}
