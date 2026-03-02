
const loginForm = document.getElementById("loginForm");
if (loginForm) {
    loginForm.addEventListener("submit", function(e) {

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
            usernameError.textContent = "Username is required";
            username.classList.add("error");
            isValid = false;
        } else if (username.value.trim().length < 3) {
            usernameError.textContent = "Username must be at least 3 characters";
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
}

function wireToggle(btnId, inputId) {
    const btn = document.getElementById(btnId);
    const input = document.getElementById(inputId);
    if (!btn || !input) return;

    const eyeOpenSVG = '<svg viewBox="0 0 24 24" width="20" height="20">' +
      '<circle cx="12" cy="12" r="3" />' +
      '<path d="M22 12c-2.667 4.667-6 7-10 7s-7.333-2.333-10-7c2.667-4.667 6-7 10-7s7.333 2.333 10 7z" />' +
      '</svg>';

    const eyeClosedSVG = '<svg viewBox="0 0 24 24" width="20" height="20">' +
      '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />' +
      '<line x1="1" y1="1" x2="23" y2="23" />' +
      '</svg>';

    btn.addEventListener('click', () => {
        const isPassword = input.type === 'password';
        input.type = isPassword ? 'text' : 'password';
        btn.innerHTML = isPassword ? eyeClosedSVG : eyeOpenSVG;
        btn.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
        btn.setAttribute('aria-pressed', isPassword ? 'true' : 'false');
    });
}

document.addEventListener('DOMContentLoaded', () => {
    wireToggle('toggleLoginPassword', 'loginPassword');
    wireToggle('toggleResetPassword', 'password');
});

