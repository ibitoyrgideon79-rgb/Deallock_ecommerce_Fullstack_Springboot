
const loginForm = document.getElementById("loginForm");
loginForm?.addEventListener("submit", function(e) {

    const username = document.getElementById("loginUsername");
    const password = document.getElementById("loginPassword");
    const usernameError = document.getElementById("usernameError");
    const passwordError = document.getElementById("passwordError");

    let isValid = true;

    
    usernameError.textContent = "";
    passwordError.textContent = "";
    username.classList.remove("error");
    password.classList.remove("error");

    
    if (username.value.trim() === "") {
        usernameError.textContent = "Email is required";
        username.classList.add("error");
        isValid = false;
    } else if (username.value.trim().length < 3) {
        usernameError.textContent = "Email must be at least 3 characters";
        username.classList.add("error");
        isValid = false;
    }

    
    if (password.value.trim() === "") {
        passwordError.textContent = "Password is required";
        password.classList.add("error");
        isValid = false;
    } else if (password.value.trim().length < 6) {
        passwordError.textContent = "Password must be at least 6 characters";
        password.classList.add("error");
        isValid = false;
    }

    if (!isValid) {
        e.preventDefault();
    }
});

document.querySelectorAll(".toggle-password").forEach(btn => {
    btn.addEventListener("click", () => {
        const targetId = btn.getAttribute("data-target");
        const input = document.getElementById(targetId);
        if (!input) return;
        const isHidden = input.type === "password";
        input.type = isHidden ? "text" : "password";
        const icon = btn.querySelector("i");
        if (icon) {
            icon.classList.toggle("fa-eye", !isHidden);
            icon.classList.toggle("fa-eye-slash", isHidden);
        }
    });
});

