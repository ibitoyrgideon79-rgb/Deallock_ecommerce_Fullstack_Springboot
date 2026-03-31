const API_BASE = "/api";  

    let emailVerified = false;
    let isSendingOtp  = false;
    let countdownTimer = null;
    let resendCooldown = 0;

    const els = {
        email:        document.getElementById("email"),
        getCodeBtn:   document.getElementById("getCodeBtn"),
        otpChannel:   document.querySelector('input[name="otpChannel"]:checked'),
        otpSection:   document.getElementById("otpSection"),
        otpInput:     document.getElementById("otpInput"),
        verifyOtpBtn: document.getElementById("verifyOtpBtn"),
        resendLink:   document.getElementById("resendLink"),
        signupBtn:    document.getElementById("signupBtn"),
        signupForm:   document.getElementById("signupForm"),
        successPopup: document.getElementById("successPopup"),
        countdown:    document.getElementById("countdown"),
        status:       document.getElementById("statusMessage"),
    };

    const errors = {
        email:        document.getElementById("emailError"),
        otp:          document.getElementById("otpError"),
        fullName:     document.getElementById("fullNameError"),
        address:      document.getElementById("addressError"),
        phone:        document.getElementById("phoneError"),
        dob:          document.getElementById("dobError"),
        username:     document.getElementById("usernameError"),
        password:     document.getElementById("passwordError"),
        confirm:      document.getElementById("confirmPasswordError"),
    };

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const phoneRegex = /^\+[1-9]\d{7,14}$/;
    const NG_CODE = "+234";

    function normalizePhone(raw) {
        if (!raw) return raw;
        const cleaned = raw.replace(/\s+/g, "");
        if (cleaned.startsWith("+") && phoneRegex.test(cleaned)) return cleaned;
        if (cleaned.startsWith("0") && cleaned.length === 11) {
            return NG_CODE + cleaned.slice(1);
        }
        if (cleaned.startsWith("234") && cleaned.length === 13) {
            return "+" + cleaned;
        }
        return cleaned;
    }

    function showError(el, msg) {
        el.textContent = msg;
        el.previousElementSibling?.classList.add("error");
    }

    function clearErrors() {
        Object.values(errors).forEach(e => {
            e.textContent = "";
        });
        document.querySelectorAll(".signup-input.error").forEach(i => i.classList.remove("error"));
        if (els.status) els.status.textContent = "";
    }

    function updateGetCodeButton() {
        const valid = emailRegex.test(els.email.value.trim());
        const channel = document.querySelector('input[name="otpChannel"]:checked')?.value || "email";
        const phoneValue = document.getElementById("phone")?.value?.trim() || "";
        const phoneOk = channel !== "phone" || phoneRegex.test(phoneValue);
        els.getCodeBtn.style.display = "block";
        els.getCodeBtn.disabled = !valid || !phoneOk || isSendingOtp || resendCooldown > 0;
        if (!isSendingOtp && resendCooldown <= 0) {
            els.getCodeBtn.textContent = "Get Verification Code";
        }

        if (!valid) {
            els.otpSection.style.display = "none";
            emailVerified = false;
            els.signupBtn.disabled = true;
        }
    }

    els.email.addEventListener("input", updateGetCodeButton);
    els.email.addEventListener("change", updateGetCodeButton);
    const phoneInput = document.getElementById("phone");
    phoneInput?.addEventListener("input", updateGetCodeButton);
    phoneInput?.addEventListener("blur", () => {
        const normalized = normalizePhone(phoneInput.value.trim());
        phoneInput.value = normalized;
        if (normalized && !phoneRegex.test(normalized)) {
            showError(errors.phone, "Your number must follow this format: +2348012345678");
        } else {
            errors.phone.textContent = "";
        }
        updateGetCodeButton();
    });
    document.querySelectorAll('input[name="otpChannel"]').forEach(r => {
        r.addEventListener("change", updateGetCodeButton);
    });

    function startResendCooldown(seconds) {
        resendCooldown = seconds;
        if (els.resendLink) {
            els.resendLink.style.pointerEvents = "none";
            els.resendLink.style.opacity = "0.6";
        }
        if (els.status) {
            els.status.textContent = `OTP sent. You can resend in ${resendCooldown}s.`;
        }
        const tick = () => {
            resendCooldown -= 1;
            if (resendCooldown <= 0) {
                clearInterval(countdownTimer);
                countdownTimer = null;
                if (els.resendLink) {
                    els.resendLink.style.pointerEvents = "";
                    els.resendLink.style.opacity = "";
                    els.resendLink.textContent = "Didn't receive code? Resend";
                }
                updateGetCodeButton();
                if (els.status) els.status.textContent = "";
                return;
            }
            if (els.resendLink) {
                els.resendLink.textContent = `Resend in ${resendCooldown}s`;
            }
            updateGetCodeButton();
        };
        if (countdownTimer) clearInterval(countdownTimer);
        countdownTimer = setInterval(tick, 1000);
    }

    async function sendOtp() {
        if (isSendingOtp) return;
        const email = els.email.value.trim();
        const channel = document.querySelector('input[name="otpChannel"]:checked')?.value || "email";
        const phone = normalizePhone(document.getElementById("phone")?.value?.trim() || "");
        document.getElementById("phone").value = phone;
        if (!emailRegex.test(email)) return;
        if (channel === "phone" && !phoneRegex.test(phone)) {
            showError(errors.phone, "Use international format e.g. +2348012345678");
            return;
        }

        isSendingOtp = true;
        els.getCodeBtn.disabled = true;
        els.getCodeBtn.textContent = "Sending...";
        errors.email.textContent = "";
        errors.phone.textContent = "";

        try {
            const res = await fetch(`${API_BASE}/send-otp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, phone, channel })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Failed to send code");
            }

            els.otpSection.style.display = "block";
            els.otpInput.value = "";
            els.verifyOtpBtn.disabled = false;
            els.getCodeBtn.textContent = "Resend Code";
            startResendCooldown(60);
        } catch (err) {
            if (channel === "phone") {
                showError(errors.phone, err.message || "Could not send verification code");
            } else {
                showError(errors.email, err.message || "Could not send verification code");
            }
            if (els.status) els.status.textContent = err.message || "Could not send verification code";
            els.getCodeBtn.textContent = "Get Verification Code";
        } finally {
            isSendingOtp = false;
            updateGetCodeButton();
        }
    }

    els.getCodeBtn.addEventListener("click", sendOtp);
    els.resendLink.addEventListener("click", sendOtp);

    els.verifyOtpBtn.addEventListener("click", async () => {
        const otp = els.otpInput.value.trim();
        if (!otp) {
            showError(errors.otp, "Please enter the code");
            return;
        }
        const channel = document.querySelector('input[name="otpChannel"]:checked')?.value || "email";
        const phone = normalizePhone(document.getElementById("phone")?.value?.trim() || "");
        document.getElementById("phone").value = phone;

        els.verifyOtpBtn.disabled = true;
        errors.otp.textContent = "";

        try {
            const res = await fetch(`${API_BASE}/verify-otp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    email: els.email.value.trim(),
                    phone,
                    channel,
                    otp
                })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Invalid or expired code");
            }

            emailVerified = true;
            els.otpSection.style.display = "none";
            els.signupBtn.disabled = false;
            if (els.status) els.status.textContent = data.message || "OTP verified.";
            
        } catch (err) {
            showError(errors.otp, err.message || "Verification failed");
            els.verifyOtpBtn.disabled = false;
            if (els.status) els.status.textContent = err.message || "Verification failed";
        }
    });

    els.signupForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        clearErrors();

        if (!emailVerified) {
            showError(errors.email, "Please verify your OTP first");
            if (els.status) els.status.textContent = "Please verify your OTP first.";
            return;
        }

        const values = {
            fullName:       els.signupForm.fullName.value.trim(),
            address:        els.signupForm.address.value.trim(),
            phone:          normalizePhone(els.signupForm.phone.value.trim()),
            dob:            els.signupForm.dob.value,
            username:       els.signupForm.username.value.trim(),
            email:          els.email.value.trim(),
            password:       els.signupForm.password.value,
            confirmPassword: els.signupForm.confirmPassword.value,
        };

        let valid = true;

        if (values.fullName.length < 2) {
            showError(errors.fullName, "Please enter your full name");
            valid = false;
        }
        if (values.address.length < 5) {
            showError(errors.address, "Please enter a valid address");
            valid = false;
        }
        if (!phoneRegex.test(values.phone)) {
            showError(errors.phone, "Use international format e.g. +2348012345678");
            valid = false;
        }
        if (!values.dob) {
            showError(errors.dob, "Date of birth is required");
            valid = false;
        } else {
            const dobDate = new Date(values.dob);
            const today = new Date();
            let age = today.getFullYear() - dobDate.getFullYear();
            const m = today.getMonth() - dobDate.getMonth();
            if (m < 0 || (m === 0 && today.getDate() < dobDate.getDate())) {
                age--;
            }
            if (age < 18) {
                showError(errors.dob, "You must be at least 18 years old");
                valid = false;
            }
        }
        if (values.username.length < 9) {
            showError(errors.username, "Username must be at least 9 characters");
            valid = false;
        }
        const strongPwd = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,}$/;
        if (!strongPwd.test(values.password)) {
            showError(errors.password, "Password must be 8+ chars with upper, lower, number, special");
            valid = false;
        }
        if (values.confirmPassword !== values.password) {
            showError(errors.confirm, "Passwords do not match");
            valid = false;
        }

        if (!valid) {
            if (els.status) els.status.textContent = "Please fix the errors above.";
            return;
        }

        
        try {
            const res = await fetch(`${API_BASE}/signup`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fullName: values.fullName,
                    address:  values.address,
                    phone:    values.phone,
                    dob:      values.dob,
                    username: values.username,
                    email:    values.email,
                    password: values.password,
                    confirmPassword: values.confirmPassword,
                    
                })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Registration failed");
            }

            els.successPopup.style.display = "flex";
            let sec = 8;
            els.countdown.textContent = sec;

            countdownTimer = setInterval(() => {
                sec--;
                els.countdown.textContent = sec;
                if (sec <= 0) {
                    clearInterval(countdownTimer);
                    window.location.href = "/login";  
                }
            }, 1000);

        } catch (err) {
            const msg = err.message || "Something went wrong during registration";
            if (msg.toLowerCase().includes("email")) {
                showError(errors.email, msg);
            } else if (msg.toLowerCase().includes("password")) {
                showError(errors.confirm, msg);
            } else if (msg.toLowerCase().includes("phone")) {
                showError(errors.phone, msg);
            } else if (msg.toLowerCase().includes("username")) {
                showError(errors.username, msg);
            }
            if (els.status) {
                els.status.textContent = msg;
            }
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
