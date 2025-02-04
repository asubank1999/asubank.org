// Theme Toggle based on device theme
const themeToggle = document.getElementById("theme-toggle");
document.body.setAttribute("data-theme", window.matchMedia('(prefers-color-scheme: dark)').matches ? "dark" : "light");

function toggleTheme() {
    const currentTheme = document.body.getAttribute("data-theme");
    const newTheme = currentTheme === "dark" ? "light" : "dark";
    document.body.setAttribute("data-theme", newTheme);
    themeToggle.textContent = newTheme === "dark" ? "🌞" : "🌙";
}

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
    document.body.setAttribute("data-theme", e.matches ? "dark" : "light");
});

// Sidebar Toggle
function toggleMenu() {
    document.getElementById("sidebar").classList.toggle("active");
}

// Close Sidebar when clicking outside
document.addEventListener("click", function (event) {
    const sidebar = document.getElementById("sidebar");
    if (!sidebar.contains(event.target) && !event.target.closest(".menu-toggle")) {
        sidebar.classList.remove("active");
    }
});